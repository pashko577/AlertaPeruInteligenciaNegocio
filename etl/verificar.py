"""Verifica la carga en Atlas comparándola con cálculos directos sobre el CSV.

Uso:
    python verificar.py

Comprueba (criterios de aceptación, sección 16 del documento del proyecto):
  1. Conteo total y conteo por año.
  2. Conteo por departamento.
  3. Sumas de indicadores clave (fallecidos, damnificados, afectados, viviendas
     destruidas, hectáreas afectadas).
  4. Que los vacíos no se convirtieron en ceros (columnas educativas por periodo).
  5. Que las consultas principales usan índices (explain).
  6. Almacenamiento usado frente al límite de 0,5 GB de Atlas M0.
Devuelve código de salida 0 si todo coincide y 1 si algo falla.
"""

from __future__ import annotations

import sys
from collections import Counter
from datetime import datetime

from alerta_etl import carga
from alerta_etl.config import cargar_config
from alerta_etl.transformacion import leer_y_transformar

LIMITE_M0_BYTES = 512 * 1024 * 1024
fallos: list[str] = []


def comparar(nombre: str, esperado, obtenido) -> None:
    ok = esperado == obtenido
    print(f"  [{'OK' if ok else 'FALLO'}] {nombre}: CSV={esperado} | Atlas={obtenido}")
    if not ok:
        fallos.append(nombre)


def redondear(valor) -> float:
    return round(float(valor), 4)


def usa_indice(plan: dict) -> bool:
    """Busca una etapa IXSCAN (o EXPRESS/IDHACK) en el plan ganador."""
    texto = str(plan)
    return any(etapa in texto for etapa in ("IXSCAN", "EXPRESS_IXSCAN", "IDHACK", "EXPRESS"))


def main() -> int:
    cfg = cargar_config()
    if not cfg.mongodb_uri:
        print("Falta MONGODB_URI en etl/.env")
        return 1

    print("Calculando valores esperados desde el CSV...")
    docs = leer_y_transformar(cfg.csv_path).documentos

    cliente, db = carga.conectar(cfg.mongodb_uri, cfg.mongodb_database)
    col = db[carga.COLECCION_EMERGENCIAS]
    with cliente:
        print("\n1. Conteos")
        comparar("total de documentos", len(docs), col.count_documents({}))
        esperado_anio = dict(sorted(Counter(d["anio"] for d in docs).items()))
        obtenido_anio = {r["_id"]: r["n"] for r in col.aggregate(
            [{"$group": {"_id": "$anio", "n": {"$sum": 1}}}, {"$sort": {"_id": 1}}])}
        comparar("documentos por año", esperado_anio, obtenido_anio)

        print("\n2. Departamentos")
        esperado_dep = dict(sorted(Counter(d["ubicacion"]["departamento"] for d in docs).items()))
        obtenido_dep = {r["_id"]: r["n"] for r in col.aggregate(
            [{"$group": {"_id": "$ubicacion.departamento", "n": {"$sum": 1}}}, {"$sort": {"_id": 1}}])}
        comparar("documentos por departamento (26)", esperado_dep, obtenido_dep)

        print("\n3. Sumas de indicadores")
        campos = [("personas", "fallecidos"), ("personas", "damnificados"), ("personas", "afectados"),
                  ("vivienda", "destruidas"), ("agricultura", "hasAfectadas")]
        grupo = {"_id": None}
        for g, c in campos:
            grupo[f"{g}_{c}"] = {"$sum": f"${g}.{c}"}
        obtenido_sumas = next(col.aggregate([{"$group": grupo}]))
        for g, c in campos:
            esperado = redondear(sum(d[g].get(c, 0) for d in docs))
            comparar(f"suma de {g}.{c}", esperado, redondear(obtenido_sumas[f"{g}_{c}"]))

        print("\n4. Vacíos frente a ceros (columnas educativas)")
        comparar("aulas informadas antes de 2019 (debe ser 0)", 0,
                 col.count_documents({"anio": {"$lte": 2018}, "educacion.aulasAfectadas": {"$exists": True}}))
        comparar("centros educativos informados desde 2019 (debe ser 0)", 0,
                 col.count_documents({"anio": {"$gte": 2019}, "educacion.centrosDestruidos": {"$exists": True}}))
        esperado_2025 = sum(1 for d in docs if d["anio"] == 2025)
        comparar("documentos de 2025 con fecha dentro de 2025", esperado_2025,
                 col.count_documents({"anio": 2025, "fecha": {"$gte": datetime(2025, 1, 1),
                                                               "$lt": datetime(2026, 1, 1)}}))

        print("\n5. Uso de índices (explain)")
        consultas = {
            "departamento + orden por fecha": ({"ubicacion.departamento": "CUSCO"}, [("fecha", -1)]),
            "provincia por prefijo de ubigeo": ({"ubicacion.ubigeo": {"$regex": "^0801"}}, [("fecha", -1)]),
            "distrito": ({"ubicacion.ubigeo": "030101"}, [("fecha", -1)]),
            "peligro": ({"peligro.nombre": "SISMO"}, [("fecha", -1)]),
            "código SINPAD": ({"codigoSinpad": "97553"}, None),
        }
        for nombre, (filtro, orden) in consultas.items():
            cursor = col.find(filtro).limit(20)
            if orden:
                cursor = cursor.sort(orden)
            plan = cursor.explain().get("queryPlanner", {}).get("winningPlan", {})
            ok = usa_indice(plan)
            print(f"  [{'OK' if ok else 'FALLO'}] {nombre}")
            if not ok:
                fallos.append(f"índice: {nombre}")

        print("\n6. Almacenamiento")
        stats = db.command("dbStats")
        datos, indices = stats.get("dataSize", 0), stats.get("indexSize", 0)
        total = datos + indices
        print(f"  Datos: {datos / 1e6:.1f} MB | Índices: {indices / 1e6:.1f} MB | "
              f"Total: {total / 1e6:.1f} MB ({100 * total / LIMITE_M0_BYTES:.0f} % de 0,5 GB)")
        if total > LIMITE_M0_BYTES:
            fallos.append("almacenamiento por encima del límite de M0")

    print("\nResultado:", "TODO CORRECTO" if not fallos else f"{len(fallos)} FALLOS: {fallos}")
    return 0 if not fallos else 1


if __name__ == "__main__":
    sys.exit(main())
