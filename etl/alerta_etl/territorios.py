"""Construcción del catálogo 'territorios' para los filtros y el mapa.

- Departamentos: los 26 nombres tal como los publica INDECI en el CSV
  (LIMA METROPOLITANA y LIMA PROVINCIAS por separado).
- Provincias y distritos: un nombre oficial por ubigeo, tomado de los
  límites IDE-INEI 2023 (lincolneulogio/peru-geojson, variante ligera).
- Excepciones documentadas en DATASET_AUDIT.md:
    * 060506: el INEI publica el nombre cortado; se usa el del CSV.
    * Ubigeos del CSV sin polígono (130112 ALTO TRUJILLO): nombre del CSV.
"""

from __future__ import annotations

import json
from collections import Counter, defaultdict
from pathlib import Path

# Ubigeos en los que se prefiere el nombre del CSV al de la fuente cartográfica
NOMBRES_DESDE_CSV = {"060506"}  # INEI: "SANTA CRUZ DE TOLED" (cortado)


def _cargar_geojson(ruta: Path) -> dict[str, dict]:
    with open(ruta, encoding="utf-8") as f:
        datos = json.load(f)
    return {
        feat["properties"]["ubigeo"]: feat["properties"]
        for feat in datos["features"]
        if feat.get("geometry")
    }


def _mas_frecuente(contador: Counter) -> str:
    return contador.most_common(1)[0][0]


def construir_territorios(documentos: list[dict], geojson_dir: Path) -> tuple[list[dict], list[str]]:
    """Devuelve (documentos del catálogo, avisos) a partir de las emergencias y del GeoJSON."""
    poligonos_prov = _cargar_geojson(geojson_dir / "provincias.geojson")
    poligonos_dist = _cargar_geojson(geojson_dir / "distritos.geojson")

    # Nombres y relaciones observados en el CSV
    dep_nombre_por_prov: dict[str, Counter] = defaultdict(Counter)
    prov_nombre_csv: dict[str, Counter] = defaultdict(Counter)
    dist_nombre_csv: dict[str, Counter] = defaultdict(Counter)
    for doc in documentos:
        u = doc["ubicacion"]
        dep_nombre_por_prov[u["codProvincia"]][u["departamento"]] += 1
        prov_nombre_csv[u["codProvincia"]][u["provincia"]] += 1
        dist_nombre_csv[u["ubigeo"]][u["distrito"]] += 1

    avisos: list[str] = []
    # Cada provincia debe pertenecer a un único departamento INDECI
    for prov, nombres in dep_nombre_por_prov.items():
        if len(nombres) > 1:
            avisos.append(f"La provincia {prov} aparece con varios departamentos: {dict(nombres)}")
    dep_de_prov = {prov: _mas_frecuente(n) for prov, n in dep_nombre_por_prov.items()}

    catalogo: list[dict] = []

    # --- Departamentos (nombres INDECI) ---
    deps: dict[str, set[str]] = defaultdict(set)
    for prov, dep in dep_de_prov.items():
        deps[dep].add(prov[:2])
    for nombre, codigos in sorted(deps.items()):
        if len(codigos) != 1:
            avisos.append(f"El departamento {nombre} abarca varios códigos: {sorted(codigos)}")
        catalogo.append({
            "_id": f"departamento:{nombre}",
            "nivel": "departamento",
            "nombre": nombre,
            "codDepartamento": sorted(codigos)[0],
            "provincias": sorted(p for p, d in dep_de_prov.items() if d == nombre),
        })

    # --- Provincias ---
    for prov in sorted(dep_de_prov):
        props = poligonos_prov.get(prov)
        if props:
            nombre = props["nombre_provincia"].upper()
        else:
            nombre = _mas_frecuente(prov_nombre_csv[prov])
            avisos.append(f"Provincia {prov} ({nombre}) sin polígono: se usa el nombre del CSV")
        catalogo.append({
            "_id": f"provincia:{prov}",
            "nivel": "provincia",
            "ubigeo": prov,
            "nombre": nombre,
            "departamento": dep_de_prov[prov],
            "codDepartamento": prov[:2],
            "tienePoligono": props is not None,
        })

    # --- Distritos ---
    for dist in sorted(dist_nombre_csv):
        props = poligonos_dist.get(dist)
        if props and dist not in NOMBRES_DESDE_CSV:
            nombre = props["nombre_distrito"].upper()
        else:
            nombre = _mas_frecuente(dist_nombre_csv[dist])
            if not props:
                avisos.append(f"Distrito {dist} ({nombre}) sin polígono: se usa el nombre del CSV")
        prov = dist[:4]
        catalogo.append({
            "_id": f"distrito:{dist}",
            "nivel": "distrito",
            "ubigeo": dist,
            "nombre": nombre,
            "departamento": dep_de_prov[prov],
            "codDepartamento": dist[:2],
            "codProvincia": prov,
            "tienePoligono": props is not None,
        })

    return catalogo, avisos
