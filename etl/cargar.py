"""Carga el CSV de emergencias de INDECI en MongoDB Atlas.

Uso:
    python cargar.py --dry-run        # valida y transforma todo, sin conectarse
    python cargar.py                  # carga completa en Atlas
    python cargar.py --ritmo 0        # sin límite de ritmo (no recomendado en M0)

El proceso es idempotente: ejecutarlo varias veces con el mismo archivo no
crea duplicados (upsert por _id).
"""

from __future__ import annotations

import argparse
import csv
import json
import logging
import sys
import time
from collections import Counter
from datetime import datetime
from pathlib import Path

from alerta_etl import carga
from alerta_etl.config import FILAS_ESPERADAS, LOGS_DIR, cargar_config
from alerta_etl.territorios import construir_territorios
from alerta_etl.transformacion import calcular_sha256, leer_y_transformar

log = logging.getLogger("alerta_etl")


def configurar_logs() -> Path:
    LOGS_DIR.mkdir(exist_ok=True)
    archivo = LOGS_DIR / f"etl_{datetime.now():%Y%m%d_%H%M%S}.log"
    formato = logging.Formatter("%(asctime)s %(levelname)-7s %(message)s", "%H:%M:%S")
    raiz = logging.getLogger()
    raiz.setLevel(logging.INFO)
    for handler in (logging.StreamHandler(sys.stdout), logging.FileHandler(archivo, encoding="utf-8")):
        handler.setFormatter(formato)
        raiz.addHandler(handler)
    return archivo


def guardar_rechazos(rechazos: list[tuple[int, str, list[str]]]) -> Path:
    ruta = LOGS_DIR / "rechazados.csv"
    with open(ruta, "w", encoding="utf-8", newline="") as f:
        escritor = csv.writer(f, delimiter=";")
        escritor.writerow(["linea", "motivo", "fila_original"])
        for linea, motivo, fila in rechazos:
            escritor.writerow([linea, motivo, ";".join(fila)])
    return ruta


def guardar_muestra(documentos: list[dict]) -> Path:
    """Guarda algunos documentos de ejemplo (uno de 2003, uno de 2019 y uno de 2025)."""
    muestra, anios = [], set()
    for doc in documentos:
        if doc["anio"] in (2003, 2019, 2025) and doc["anio"] not in anios:
            anios.add(doc["anio"])
            muestra.append(doc)
    ruta = LOGS_DIR / "muestra_documentos.json"
    with open(ruta, "w", encoding="utf-8") as f:
        json.dump(muestra, f, ensure_ascii=False, indent=2, default=str)
    return ruta


def main() -> int:
    parser = argparse.ArgumentParser(description="ETL de ALERTA PERÚ hacia MongoDB Atlas")
    parser.add_argument("--dry-run", action="store_true",
                        help="valida y transforma sin conectarse a Atlas")
    parser.add_argument("--lote", type=int, default=500,
                        help="documentos por lote (por defecto 500)")
    parser.add_argument("--ritmo", type=float, default=90,
                        help="máximo de documentos por segundo; 0 = sin límite (por defecto 90, "
                             "por debajo del límite de 100 operaciones/s de Atlas M0)")
    parser.add_argument("--permitir-rechazos", action="store_true",
                        help="cargar aunque haya filas rechazadas")
    args = parser.parse_args()

    archivo_log = configurar_logs()
    cfg = cargar_config()
    inicio = time.monotonic()

    # 1. Lectura y transformación -------------------------------------------
    if not cfg.csv_path.exists():
        log.error("No se encuentra el CSV: %s", cfg.csv_path)
        return 1
    log.info("Leyendo %s", cfg.csv_path)
    sha256 = calcular_sha256(cfg.csv_path)
    resultado = leer_y_transformar(cfg.csv_path)
    docs = resultado.documentos

    log.info("Filas leídas: %d | documentos válidos: %d | rechazadas: %d",
             resultado.filas_leidas, len(docs), len(resultado.rechazos))
    for clave, n in sorted(resultado.advertencias.items()):
        log.warning("Advertencia: %s (%d filas)", clave, n)
    if resultado.filas_leidas != FILAS_ESPERADAS:
        log.warning("Se esperaban %d filas según la auditoría y se leyeron %d. "
                    "Puede ser una versión nueva del CSV: revisa DATASET_AUDIT.md.",
                    FILAS_ESPERADAS, resultado.filas_leidas)
    if resultado.rechazos:
        ruta = guardar_rechazos(resultado.rechazos)
        log.error("Hay filas rechazadas; detalle en %s", ruta)
        if not args.permitir_rechazos:
            log.error("Carga detenida. Corrige el problema o usa --permitir-rechazos.")
            return 1

    por_anio = Counter(d["anio"] for d in docs)
    log.info("Documentos por año: %s", dict(sorted(por_anio.items())))

    # 2. Catálogo de territorios ---------------------------------------------
    territorios, avisos = construir_territorios(docs, cfg.geojson_dir)
    niveles = Counter(t["nivel"] for t in territorios)
    log.info("Catálogo de territorios: %s", dict(niveles))
    for aviso in avisos:
        log.warning(aviso)

    muestra = guardar_muestra(docs)
    log.info("Muestra de documentos en %s", muestra)

    if args.dry_run:
        log.info("Modo --dry-run: no se escribió nada en Atlas. Log: %s", archivo_log)
        return 0

    # 3. Conexión ------------------------------------------------------------
    if not cfg.mongodb_uri:
        log.error("Falta MONGODB_URI. Copia etl/.env.example como etl/.env y complétalo.")
        return 1
    log.info("Conectando a Atlas (base de datos '%s')...", cfg.mongodb_database)
    try:
        cliente, db = carga.conectar(cfg.mongodb_uri, cfg.mongodb_database)
    except Exception as exc:  # noqa: BLE001 - se informa cualquier fallo de conexión
        log.error("No se pudo conectar a Atlas: %s", exc)
        log.error("Revisa la cadena de conexión, el usuario y Network Access (tu IP).")
        return 1

    with cliente:
        emergencias = db[carga.COLECCION_EMERGENCIAS]

        # Registros que estaban en Atlas y ya no vienen en el CSV (se informan, no se borran)
        existentes = carga.ids_existentes(emergencias)
        ids_csv = {d["_id"] for d in docs}
        sobrantes = existentes - ids_csv
        if sobrantes:
            log.warning("%d documentos en Atlas no están en este CSV (no se borran). Ej.: %s",
                        len(sobrantes), sorted(sobrantes)[:5])

        # 4. Territorios -----------------------------------------------------
        log.info("Cargando catálogo de territorios...")
        carga.reemplazar_territorios(db, territorios)

        # 5. Emergencias -----------------------------------------------------
        estimado = len(docs) / args.ritmo / 60 if args.ritmo > 0 else 0
        log.info("Cargando %d emergencias en lotes de %d (ritmo máx. %s docs/s, ~%.0f min)...",
                 len(docs), args.lote, args.ritmo or "sin límite", estimado)

        ultimo_aviso = [0.0]

        def progreso(hechos: int, total: int) -> None:
            ahora = time.monotonic()
            if ahora - ultimo_aviso[0] >= 15 or hechos == total:
                ultimo_aviso[0] = ahora
                log.info("  %6d / %d (%.1f %%)", hechos, total, 100 * hechos / total)

        try:
            insertados, modificados = carga.cargar_por_lotes(
                emergencias, docs, args.lote, args.ritmo, progreso)
        except RuntimeError as exc:
            log.error("Carga interrumpida: %s", exc)
            log.error("Puedes volver a ejecutar el script: los lotes ya cargados no se duplican.")
            return 1

        # 6. Índices ---------------------------------------------------------
        log.info("Creando índices...")
        carga.crear_indices(emergencias, carga.INDICES_EMERGENCIAS)

        # 7. Verificación del conteo -----------------------------------------
        total_atlas = emergencias.count_documents({})
        esperado = len(existentes | ids_csv)
        ok = total_atlas == esperado
        log.info("Documentos en Atlas: %d (esperados: %d) -> %s",
                 total_atlas, esperado, "OK" if ok else "NO COINCIDE")

        # 8. Metadatos de la carga -------------------------------------------
        duracion = round(time.monotonic() - inicio, 1)
        carga.registrar_metadatos(db, {
            "archivo": cfg.csv_path.name,
            "tamanoBytes": cfg.csv_path.stat().st_size,
            "sha256": sha256,
            "filasLeidas": resultado.filas_leidas,
            "filasRechazadas": len(resultado.rechazos),
            "documentosInsertados": insertados,
            "documentosActualizados": modificados,
            "documentosEnAtlas": total_atlas,
            "documentosNoPresentesEnCsv": len(sobrantes),
            "territorios": dict(niveles),
            "duracionSegundos": duracion,
            "verificacionConteo": ok,
        })
        log.info("Insertados: %d | actualizados: %d | duración: %.0f s | log: %s",
                 insertados, modificados, duracion, archivo_log)
        return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
