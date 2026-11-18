"""Cuadre de totales: CSV original frente a lo que muestra la página (API REST).

`verificar.py` comprueba CSV -> Atlas usando la misma transformación del ETL.
Este script cierra la cadena completa con un cálculo INDEPENDIENTE: lee el CSV
crudo con la biblioteca estándar (sin usar `alerta_etl`) y compara contra las
mismas respuestas JSON que consume el frontend.

    CSV crudo  --(este script)-->  totales esperados
    CSV -> ETL -> Atlas -> Spring Boot -> /api/...  -->  totales mostrados

Uso (con el backend levantado):
    python cuadrar_api.py                       # contra http://localhost:8080
    python cuadrar_api.py --api http://host:8080 --csv ../BD_2003-2025_EMERGENCIAS.csv

No necesita credenciales: solo consulta la API pública de lectura.
Sale con código 0 si todo cuadra y 1 si hay alguna diferencia.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent

# Columnas del CSV (posición) y su nombre en la API
COL_ANIO, COL_DPTO, COL_PROV, COL_DIST, COL_UBIGEO = 2, 5, 6, 7, 4
COL_PELIGRO, COL_TIPO, COL_REGION = 8, 9, 10
INDICADORES = {
    "fallecidos": 11, "desaparecidos": 12, "lesionados": 13, "damnificados": 14,
    "afectados": 15, "viviendasDestruidas": 16, "viviendasAfectadas": 17,
    "hectareasCultivoDestruidas": 27, "hectareasCultivoAfectadas": 28,
}
DECIMALES = {"hectareasCultivoDestruidas", "hectareasCultivoAfectadas"}
# Indicadores que devuelven los endpoints agrupados (por año, peligro, territorio)
INDICADORES_GRUPO = ["fallecidos", "damnificados", "afectados", "viviendasDestruidas"]

resultados: list[tuple[str, bool]] = []


def leer_csv(ruta: Path) -> list[list[str]]:
    with ruta.open(encoding="latin-1", newline="") as f:
        filas = list(csv.reader(f, delimiter=";"))
    return filas[1:]


def numero(texto: str) -> float:
    """Celda vacía = sin dato: no suma (igual que en la base, donde no se guarda)."""
    texto = texto.strip()
    return float(texto) if texto else 0.0


def totales(filas: list[list[str]]) -> dict:
    t = {"emergencias": len(filas)}
    for nombre, col in INDICADORES.items():
        t[nombre] = sum(numero(f[col]) for f in filas)
    return t


def agrupar(filas, clave) -> dict[str, dict]:
    grupos: dict[str, list] = defaultdict(list)
    for f in filas:
        grupos[clave(f)].append(f)
    return {k: totales(v) for k, v in grupos.items()}


def api(base: str, ruta: str, **params):
    query = urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
    url = f"{base}/api{ruta}" + (f"?{query}" if query else "")
    with urllib.request.urlopen(url, timeout=60) as r:
        return json.load(r)


def iguales(nombre: str, esperado: float, obtenido) -> bool:
    obtenido = obtenido or 0
    if nombre in DECIMALES:
        return abs(esperado - obtenido) < 0.01
    return round(esperado) == obtenido


def comparar_totales(caso: str, esperado: dict, obtenido: dict, campos) -> None:
    difs = [f"{c}: CSV={esperado[c]:,.2f} API={obtenido.get(c)}" for c in campos
            if not iguales(c, esperado[c], obtenido.get(c))]
    ok = not difs
    resultados.append((caso, ok))
    print(f"  [{'OK' if ok else 'FALLO'}] {caso}"
          + ("" if ok else "\n        " + "\n        ".join(difs)))


def comparar_grupos(caso: str, esperado: dict[str, dict], lista: list[dict]) -> None:
    obtenido = {g["clave"]: g for g in lista}
    difs = []
    for clave in sorted(set(esperado) | set(obtenido)):
        e, o = esperado.get(clave), obtenido.get(clave)
        if e is None or o is None:
            difs.append(f"{clave}: {'solo en la API' if e is None else 'falta en la API'}")
            continue
        for c in ["emergencias", *INDICADORES_GRUPO]:
            if not iguales(c, e[c], o.get(c)):
                difs.append(f"{clave} / {c}: CSV={e[c]:,.0f} API={o.get(c)}")
    ok = not difs
    resultados.append((caso, ok))
    print(f"  [{'OK' if ok else 'FALLO'}] {caso} ({len(esperado)} grupos x {1 + len(INDICADORES_GRUPO)} medidas)"
          + ("" if ok else "\n        " + "\n        ".join(difs[:15])))


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--api", default="http://localhost:8080")
    p.add_argument("--csv", type=Path, default=RAIZ / "BD_2003-2025_EMERGENCIAS.csv")
    a = p.parse_args()

    print(f"Leyendo {a.csv.name} ...")
    filas = leer_csv(a.csv)
    print(f"  {len(filas):,} filas\n")
    base = a.api.rstrip("/")
    todos = list(INDICADORES) + ["emergencias"]

    print("1. Totales nacionales (cifras de la cabecera y de Panorama)")
    comparar_totales("Perú, 2003-2025, 10 indicadores", totales(filas),
                     api(base, "/estadisticas/resumen")["totales"], todos)

    print("\n2. Gráficos agrupados")
    comparar_grupos("Por año", agrupar(filas, lambda f: f[COL_ANIO].strip()),
                    api(base, "/estadisticas/por-anio"))
    comparar_grupos("Por peligro", agrupar(filas, lambda f: f[COL_PELIGRO].strip()),
                    api(base, "/estadisticas/por-peligro"))

    print("\n3. Mapa y ranking territorial")
    comparar_grupos("Por departamento", agrupar(filas, lambda f: f[COL_DPTO].strip()),
                    api(base, "/estadisticas/por-territorio", nivel="departamento"))
    cusco = [f for f in filas if f[COL_DPTO].strip() == "CUSCO"]
    # A nivel provincia la API agrupa por código ubigeo de 4 dígitos
    comparar_grupos("Provincias de Cusco", agrupar(cusco, lambda f: f[COL_UBIGEO].strip()[:4]),
                    api(base, "/estadisticas/por-territorio", nivel="provincia", departamento="CUSCO"))

    print("\n4. Consultas con filtros combinados")
    casos = [
        ("Cusco, 2020-2025", dict(departamento="CUSCO", anioDesde=2020, anioHasta=2025),
         lambda f: f[COL_DPTO].strip() == "CUSCO" and 2020 <= int(f[COL_ANIO]) <= 2025),
        ("Puno, solo bajas temperaturas", dict(departamento="PUNO", peligro="BAJAS TEMPERATURAS"),
         lambda f: f[COL_DPTO].strip() == "PUNO" and f[COL_PELIGRO].strip() == "BAJAS TEMPERATURAS"),
        ("Selva baja, origen humano, 2010-2015",
         dict(regionNatural="SELVA BAJA", tipoPeligro="INDUCIDOS POR LA ACCIÓN HUMANA", anioDesde=2010, anioHasta=2015),
         lambda f: f[COL_REGION].strip() == "SELVA BAJA" and f[COL_TIPO].strip().startswith("INDUCIDO")
         and 2010 <= int(f[COL_ANIO]) <= 2015),
        ("Lima Metropolitana, 2017", dict(departamento="LIMA METROPOLITANA", anioDesde=2017, anioHasta=2017),
         lambda f: f[COL_UBIGEO].strip().startswith("1501") and int(f[COL_ANIO]) == 2017),
        ("Distrito 080101 (Cusco, Cusco)", dict(distrito="080101"),
         lambda f: f[COL_UBIGEO].strip() == "080101"),
    ]
    for nombre, params, cond in casos:
        sub = [f for f in filas if cond(f)]
        comparar_totales(f"{nombre}: {len(sub):,} registros", totales(sub),
                         api(base, "/estadisticas/resumen", **params)["totales"], todos)
        listado = api(base, "/emergencias", size=1, **params)
        ok = listado["totalElementos"] == len(sub)
        resultados.append((f"{nombre} (pestaña Registros)", ok))
        print(f"  [{'OK' if ok else 'FALLO'}] {nombre}: pestaña Registros cuenta "
              f"{listado['totalElementos']:,} (CSV {len(sub):,})")

    fallos = [n for n, ok in resultados if not ok]
    print(f"\nResultado: {len(resultados) - len(fallos)} de {len(resultados)} comprobaciones cuadran.")
    if fallos:
        print("Con diferencias: " + "; ".join(fallos))
    return 1 if fallos else 0


if __name__ == "__main__":
    sys.exit(main())
