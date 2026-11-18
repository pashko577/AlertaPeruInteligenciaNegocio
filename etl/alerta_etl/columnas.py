"""Mapeo explícito de las 49 columnas del CSV de INDECI al modelo de documento.

El orden y los nombres se verificaron en la auditoría (DATASET_AUDIT.md).
Los encabezados se comparan normalizados (espacios colapsados y mayúsculas),
porque el CSV trae espacios irregulares como "FECHA  DE LA EMER" o
"CARRETERA DESTRUIDA KM ".
"""

from __future__ import annotations

from typing import NamedTuple


class ColumnaNumerica(NamedTuple):
    encabezado: str
    grupo: str
    campo: str


# Columnas de identificación, tiempo, territorio y peligro (índices 0 a 10)
COLUMNAS_TEXTO = [
    "CODIGO DE EMERGENCIA-SINPAD",
    "FECHA DE LA EMER",
    "AÑO",
    "MES",
    "COD. DISTRITO",
    "DPTO.",
    "PROV.",
    "DIST.",
    "PELIGRO",
    "TIPO DE PELIGRO",
    "REGIÓN NATURAL",
]

# Columnas numéricas (índices 11 a 48), en el orden del CSV
COLUMNAS_NUMERICAS = [
    ColumnaNumerica("FALLECIDOS", "personas", "fallecidos"),
    ColumnaNumerica("DESAPARECIDOS", "personas", "desaparecidos"),
    ColumnaNumerica("LESIONADOS", "personas", "lesionados"),
    ColumnaNumerica("DAMNIFICADOS", "personas", "damnificados"),
    ColumnaNumerica("AFECTADOS", "personas", "afectados"),
    ColumnaNumerica("VIVIENDAS DESTRUIDAS", "vivienda", "destruidas"),
    ColumnaNumerica("VIVIENDAS AFECTADAS", "vivienda", "afectadas"),
    ColumnaNumerica("CENTROS EDUCATIVOS DESTRUIDOS", "educacion", "centrosDestruidos"),
    ColumnaNumerica("CENTROS EDUCATIVOS AFECTADOS", "educacion", "centrosAfectados"),
    ColumnaNumerica("SCANT_CCEE", "educacion", "centrosEvaluados"),
    ColumnaNumerica("SAFEC_AULA", "educacion", "aulasAfectadas"),
    ColumnaNumerica("AULA_DES+INHA", "educacion", "aulasDestruidasInhabitables"),
    ColumnaNumerica("SDESTRU_AULA", "educacion", "aulasDestruidas"),
    ColumnaNumerica("SINHAB_AULA", "educacion", "aulasInhabitables"),
    ColumnaNumerica("CENTROS SALUD DESTRUIDOS", "salud", "centrosDestruidos"),
    ColumnaNumerica("CENTROS SALUD AFECTADOS", "salud", "centrosAfectados"),
    ColumnaNumerica("HAS CULTIVO DESTRUIDO", "agricultura", "hasDestruidas"),
    ColumnaNumerica("HAS CULTIVO AFECTADO", "agricultura", "hasAfectadas"),
    ColumnaNumerica("PUENTE DESTRUIDO", "infraestructura", "puentesDestruidos"),
    ColumnaNumerica("PUENTE AFECTADO", "infraestructura", "puentesAfectados"),
    ColumnaNumerica("CARRETERA DESTRUIDA KM", "infraestructura", "carreteraDestruidaKm"),
    ColumnaNumerica("CARRETERA AFECTADA KM", "infraestructura", "carreteraAfectadaKm"),
    ColumnaNumerica("CAMINO RURAL DESTRUIDO KM", "infraestructura", "caminoRuralDestruidoKm"),
    ColumnaNumerica("CAMINO RURAL AFECTADO KM", "infraestructura", "caminoRuralAfectadoKm"),
    ColumnaNumerica("CANAL DE REGADIO COLAPSADO", "infraestructura", "canalColapsado"),
    ColumnaNumerica("CANAL DE REGADIO AFECTADO", "infraestructura", "canalAfectado"),
    ColumnaNumerica("PERDIDA VACUNO", "ganaderia", "vacunoPerdida"),
    ColumnaNumerica("AFECTADOS VACUNO", "ganaderia", "vacunoAfectado"),
    ColumnaNumerica("PERDIDA CAMELIDO", "ganaderia", "camelidoPerdida"),
    ColumnaNumerica("AFECTADOS CAMELIDO", "ganaderia", "camelidoAfectado"),
    ColumnaNumerica("PERDIDA CAPRINO", "ganaderia", "caprinoPerdida"),
    ColumnaNumerica("AFECTADOS CAPRINO", "ganaderia", "caprinoAfectado"),
    ColumnaNumerica("PERDIDA PORCINO", "ganaderia", "porcinoPerdida"),
    ColumnaNumerica("AFECTADOS PORCINO", "ganaderia", "porcinoAfectado"),
    ColumnaNumerica("PERDIDA DE ANIMALES MENORES", "ganaderia", "menoresPerdida"),
    ColumnaNumerica("AFECTA DE ANIMALES MENORES", "ganaderia", "menoresAfectado"),
    ColumnaNumerica("PESO DE LA AYUDA", "ayuda", "peso"),
    ColumnaNumerica("COSTO DE LA AYUDA", "ayuda", "costo"),
]

GRUPOS = ["personas", "vivienda", "educacion", "salud", "agricultura",
          "infraestructura", "ganaderia", "ayuda"]

ENCABEZADOS_ESPERADOS = COLUMNAS_TEXTO + [c.encabezado for c in COLUMNAS_NUMERICAS]
INICIO_NUMERICAS = len(COLUMNAS_TEXTO)


def normalizar_encabezado(texto: str) -> str:
    """Colapsa espacios repetidos, quita los extremos y pasa a mayúsculas."""
    return " ".join(texto.split()).upper()


def validar_encabezados(encabezados: list[str]) -> None:
    """Lanza ValueError si el CSV no tiene exactamente las columnas esperadas."""
    normalizados = [normalizar_encabezado(h) for h in encabezados]
    if len(normalizados) != len(ENCABEZADOS_ESPERADOS):
        raise ValueError(
            f"Se esperaban {len(ENCABEZADOS_ESPERADOS)} columnas y el CSV tiene "
            f"{len(normalizados)}. Revisa si INDECI cambió el formato del archivo."
        )
    diferencias = [
        (i, esperado, real)
        for i, (esperado, real) in enumerate(zip(ENCABEZADOS_ESPERADOS, normalizados))
        if esperado != real
    ]
    if diferencias:
        detalle = "; ".join(f"col {i}: esperado '{e}', encontrado '{r}'" for i, e, r in diferencias)
        raise ValueError(f"Los encabezados del CSV no coinciden con el mapeo: {detalle}")
