"""Configuración del ETL leída desde variables de entorno (archivo .env)."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

ETL_DIR = Path(__file__).resolve().parent.parent
PROYECTO_DIR = ETL_DIR.parent

CSV_POR_DEFECTO = PROYECTO_DIR / "BD_2003-2025_EMERGENCIAS.csv"
GEOJSON_POR_DEFECTO = (
    PROYECTO_DIR / "data" / "referencias" / "peru-geojson-2023" / "data" / "variants" / "light"
)
LOGS_DIR = ETL_DIR / "logs"

# Valores verificados en la auditoría (DATASET_AUDIT.md)
FILAS_ESPERADAS = 142_139
COLUMNAS_ESPERADAS = 49


@dataclass(frozen=True)
class Config:
    mongodb_uri: str | None
    mongodb_database: str
    csv_path: Path
    geojson_dir: Path


def _ruta(valor: str | None, por_defecto: Path) -> Path:
    if not valor:
        return por_defecto
    ruta = Path(valor)
    return ruta if ruta.is_absolute() else (ETL_DIR / ruta).resolve()


def cargar_config() -> Config:
    load_dotenv(ETL_DIR / ".env")
    return Config(
        mongodb_uri=os.getenv("MONGODB_URI") or None,
        mongodb_database=os.getenv("MONGODB_DATABASE", "alerta_peru"),
        csv_path=_ruta(os.getenv("CSV_PATH"), CSV_POR_DEFECTO),
        geojson_dir=_ruta(os.getenv("GEOJSON_DIR"), GEOJSON_POR_DEFECTO),
    )
