"""Operaciones contra MongoDB Atlas: conexión, carga por lotes, índices y metadatos.

Diseñado para el clúster gratuito (M0):
  - Límite de 100 operaciones por segundo: la carga se hace por lotes y con
    un ritmo máximo configurable (documentos por segundo).
  - Reintentos con espera exponencial ante errores de red transitorios.
    Reintentar es seguro porque cada escritura es un upsert por _id.
"""

from __future__ import annotations

import logging
import time
from datetime import datetime, timezone
from typing import Callable, Iterable

from pymongo import ASCENDING, DESCENDING, MongoClient, ReplaceOne
from pymongo.collection import Collection
from pymongo.database import Database
from pymongo.errors import AutoReconnect, BulkWriteError, NetworkTimeout, OperationFailure

log = logging.getLogger(__name__)

COLECCION_EMERGENCIAS = "emergencias"
COLECCION_TERRITORIOS = "territorios"
COLECCION_METADATOS = "metadatos_carga"

# Índices aprobados (ALERTA_PERU_CONTEXTO_PROYECTO.md, sección 9)
INDICES_EMERGENCIAS = [
    ("idx_fecha", [("fecha", DESCENDING)]),
    ("idx_departamento_fecha", [("ubicacion.departamento", ASCENDING), ("fecha", DESCENDING)]),
    ("idx_ubigeo_fecha", [("ubicacion.ubigeo", ASCENDING), ("fecha", DESCENDING)]),
    ("idx_peligro_fecha", [("peligro.nombre", ASCENDING), ("fecha", DESCENDING)]),
    ("idx_codigo_sinpad", [("codigoSinpad", ASCENDING)]),
]

INDICES_TERRITORIOS = [
    ("idx_nivel_departamento", [("nivel", ASCENDING), ("departamento", ASCENDING)]),
    ("idx_nivel_provincia", [("nivel", ASCENDING), ("codProvincia", ASCENDING)]),
]

ERRORES_TRANSITORIOS = (AutoReconnect, NetworkTimeout)
MAX_REINTENTOS = 5


def conectar(uri: str, base_datos: str) -> tuple[MongoClient, Database]:
    """Abre la conexión y verifica que Atlas responde."""
    cliente = MongoClient(
        uri,
        appname="alerta-peru-etl",
        serverSelectionTimeoutMS=15_000,
        retryWrites=True,
    )
    cliente.admin.command("ping")
    return cliente, cliente[base_datos]


def _en_lotes(elementos: list, tamano: int) -> Iterable[list]:
    for i in range(0, len(elementos), tamano):
        yield elementos[i:i + tamano]


def _escribir_lote(coleccion: Collection, lote: list[dict]) -> tuple[int, int]:
    """Upsert de un lote con reintentos. Devuelve (insertados, modificados)."""
    operaciones = [ReplaceOne({"_id": d["_id"]}, d, upsert=True) for d in lote]
    for intento in range(1, MAX_REINTENTOS + 1):
        try:
            r = coleccion.bulk_write(operaciones, ordered=False)
            return r.upserted_count, r.modified_count
        except ERRORES_TRANSITORIOS as exc:
            espera = min(2 ** intento, 60)
            log.warning("Error de red (intento %d/%d): %s. Reintento en %ds.",
                        intento, MAX_REINTENTOS, exc, espera)
            time.sleep(espera)
        except BulkWriteError as exc:
            # Errores de escritura no transitorios: se informan y se detiene la carga
            detalles = exc.details.get("writeErrors", [])[:3]
            raise RuntimeError(f"Error de escritura en el lote: {detalles}") from exc
    raise RuntimeError(f"El lote falló tras {MAX_REINTENTOS} intentos")


def cargar_por_lotes(
    coleccion: Collection,
    documentos: list[dict],
    tamano_lote: int,
    docs_por_segundo: float,
    progreso: Callable[[int, int], None] | None = None,
) -> tuple[int, int]:
    """Carga todos los documentos respetando un ritmo máximo. Devuelve (insertados, modificados)."""
    insertados = modificados = 0
    procesados = 0
    for lote in _en_lotes(documentos, tamano_lote):
        inicio = time.monotonic()
        ins, mod = _escribir_lote(coleccion, lote)
        insertados += ins
        modificados += mod
        procesados += len(lote)
        if progreso:
            progreso(procesados, len(documentos))
        if docs_por_segundo > 0:
            minimo = len(lote) / docs_por_segundo
            transcurrido = time.monotonic() - inicio
            if transcurrido < minimo:
                time.sleep(minimo - transcurrido)
    return insertados, modificados


def reemplazar_territorios(db: Database, catalogo: list[dict]) -> None:
    """El catálogo es pequeño (~2 100 documentos): se reemplaza completo."""
    coleccion = db[COLECCION_TERRITORIOS]
    coleccion.delete_many({})
    for lote in _en_lotes(catalogo, 500):
        coleccion.insert_many(lote, ordered=True)
        time.sleep(len(lote) / 90)  # respeta el límite de operaciones de M0
    crear_indices(coleccion, INDICES_TERRITORIOS)


def crear_indices(coleccion: Collection, indices: list[tuple[str, list]]) -> None:
    for nombre, claves in indices:
        try:
            coleccion.create_index(claves, name=nombre)
        except OperationFailure as exc:
            log.warning("No se pudo crear el índice %s: %s", nombre, exc)


def ids_existentes(coleccion: Collection) -> set[str]:
    return {d["_id"] for d in coleccion.find({}, {"_id": 1})}


def registrar_metadatos(db: Database, metadatos: dict) -> None:
    metadatos = {**metadatos, "registradoEn": datetime.now(timezone.utc)}
    db[COLECCION_METADATOS].insert_one(metadatos)
