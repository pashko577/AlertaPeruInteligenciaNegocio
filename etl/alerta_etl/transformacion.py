"""Lectura del CSV y transformación de cada fila a un documento de MongoDB.

Reglas aplicadas (ver DATASET_AUDIT.md):
  1. Codificación Latin-1 y separador ';'.
  2. Fechas en dos formatos: DD/MM/AAAA (2003-2024) y MM/DD/AA (2025).
     El formato se decide por la longitud del año y la fecha resultante
     se valida contra las columnas AÑO y MES.
  3. Ubigeo siempre como texto de 6 dígitos.
  4. Celda vacía -> el campo se omite. Nunca se convierte en 0.
  5. _id = codigoSinpad-AAAA-MM-DD-ubigeo (el código SINPAD solo no es único).
"""

from __future__ import annotations

import csv
import hashlib
import re
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Iterator

from .columnas import COLUMNAS_NUMERICAS, INICIO_NUMERICAS, validar_encabezados

CODIFICACION = "latin-1"
SEPARADOR = ";"

MESES = {
    "ENERO": 1, "FEBRERO": 2, "MARZO": 3, "ABRIL": 4, "MAYO": 5, "JUNIO": 6,
    "JULIO": 7, "AGOSTO": 8, "SETIEMBRE": 9, "SEPTIEMBRE": 9, "OCTUBRE": 10,
    "NOVIEMBRE": 11, "DICIEMBRE": 12,
}

_RE_UBIGEO = re.compile(r"^\d{6}$")
_RE_CODIGO = re.compile(r"^\d+$")


class FilaInvalida(Exception):
    """La fila no cumple una regla de validación y no se carga."""


@dataclass
class ResultadoLectura:
    documentos: list[dict] = field(default_factory=list)
    rechazos: list[tuple[int, str, list[str]]] = field(default_factory=list)  # (línea, motivo, fila)
    advertencias: dict[str, int] = field(default_factory=dict)
    filas_leidas: int = 0

    def advertir(self, clave: str) -> None:
        self.advertencias[clave] = self.advertencias.get(clave, 0) + 1


# ---------------------------------------------------------------------------
# Conversión de valores
# ---------------------------------------------------------------------------

def convertir_fecha(texto: str, anio_col: str, mes_col: str) -> datetime:
    """Convierte la fecha según su formato y la valida contra AÑO y MES."""
    partes = texto.strip().split("/")
    if len(partes) != 3 or not all(p.isdigit() for p in partes):
        raise FilaInvalida(f"fecha con formato desconocido: '{texto}'")
    a, b, c = partes
    try:
        if len(c) == 4:            # DD/MM/AAAA  (2003-2024)
            fecha = datetime(int(c), int(b), int(a))
        elif len(c) == 2:          # MM/DD/AA    (2025)
            fecha = datetime(2000 + int(c), int(a), int(b))
        else:
            raise FilaInvalida(f"año de la fecha con {len(c)} dígitos: '{texto}'")
    except ValueError as exc:
        raise FilaInvalida(f"fecha inexistente '{texto}': {exc}") from exc

    try:
        anio = int(anio_col.strip())
    except ValueError as exc:
        raise FilaInvalida(f"AÑO no numérico: '{anio_col}'") from exc
    mes = MESES.get(mes_col.strip().upper())
    if mes is None:
        raise FilaInvalida(f"MES desconocido: '{mes_col}'")
    if fecha.year != anio or fecha.month != mes:
        raise FilaInvalida(
            f"la fecha '{texto}' ({fecha:%Y-%m-%d}) no coincide con AÑO={anio_col} MES={mes_col}"
        )
    return fecha


def convertir_numero(texto: str) -> int | float | None:
    """'' -> None (sin dato). Entero si no tiene decimales, float si los tiene."""
    texto = texto.strip()
    if texto == "":
        return None
    try:
        valor = float(texto)
    except ValueError as exc:
        raise FilaInvalida(f"valor numérico inválido: '{texto}'") from exc
    return int(valor) if valor.is_integer() else valor


def _texto_obligatorio(valor: str, nombre: str) -> str:
    valor = valor.strip()
    if not valor:
        raise FilaInvalida(f"{nombre} vacío")
    return valor


# ---------------------------------------------------------------------------
# Transformación de una fila
# ---------------------------------------------------------------------------

def transformar_fila(fila: list[str], resultado: ResultadoLectura | None = None) -> dict:
    """Convierte una fila del CSV en un documento de la colección 'emergencias'."""
    if len(fila) != INICIO_NUMERICAS + len(COLUMNAS_NUMERICAS):
        raise FilaInvalida(f"la fila tiene {len(fila)} campos")

    codigo = fila[0].strip()
    if not _RE_CODIGO.match(codigo):
        raise FilaInvalida(f"código SINPAD inválido: '{fila[0]}'")

    ubigeo = fila[4].strip()
    if not _RE_UBIGEO.match(ubigeo):
        raise FilaInvalida(f"ubigeo inválido: '{fila[4]}'")

    fecha = convertir_fecha(fila[1], fila[2], fila[3])

    documento: dict = {
        "_id": f"{codigo}-{fecha:%Y-%m-%d}-{ubigeo}",
        "codigoSinpad": codigo,
        "fecha": fecha,
        "anio": fecha.year,
        "mes": fecha.month,
        "ubicacion": {
            "ubigeo": ubigeo,
            "codDepartamento": ubigeo[:2],
            "codProvincia": ubigeo[:4],
            "departamento": _texto_obligatorio(fila[5], "DPTO."),
            "provincia": _texto_obligatorio(fila[6], "PROV."),
            "distrito": _texto_obligatorio(fila[7], "DIST."),
            "regionNatural": _texto_obligatorio(fila[10], "REGIÓN NATURAL"),
        },
        "peligro": {
            "nombre": _texto_obligatorio(fila[8], "PELIGRO"),
            "tipo": _texto_obligatorio(fila[9], "TIPO DE PELIGRO"),
        },
    }

    grupos: dict[str, dict] = {}
    for i, columna in enumerate(COLUMNAS_NUMERICAS, start=INICIO_NUMERICAS):
        valor = convertir_numero(fila[i])
        grupo = grupos.setdefault(columna.grupo, {})
        if valor is None:
            continue  # vacío: se omite el campo, nunca se guarda 0
        if valor < 0 and resultado is not None:
            resultado.advertir(f"valor negativo en {columna.encabezado}")
        grupo[columna.campo] = valor
    documento.update(grupos)
    return documento


# ---------------------------------------------------------------------------
# Lectura del archivo completo
# ---------------------------------------------------------------------------

def calcular_sha256(ruta: Path) -> str:
    h = hashlib.sha256()
    with open(ruta, "rb") as f:
        for bloque in iter(lambda: f.read(1 << 20), b""):
            h.update(bloque)
    return h.hexdigest()


def iterar_filas(ruta: Path) -> Iterator[tuple[int, list[str]]]:
    """Devuelve (número de línea, fila) después de validar los encabezados."""
    with open(ruta, encoding=CODIFICACION, newline="") as f:
        lector = csv.reader(f, delimiter=SEPARADOR)
        encabezados = next(lector)
        validar_encabezados(encabezados)
        for numero, fila in enumerate(lector, start=2):
            yield numero, fila


def leer_y_transformar(ruta: Path) -> ResultadoLectura:
    """Lee el CSV completo, transforma cada fila y acumula rechazos y advertencias."""
    resultado = ResultadoLectura()
    ids_vistos: set[str] = set()
    for numero, fila in iterar_filas(ruta):
        resultado.filas_leidas += 1
        try:
            documento = transformar_fila(fila, resultado)
        except FilaInvalida as exc:
            resultado.rechazos.append((numero, str(exc), fila))
            continue
        if documento["_id"] in ids_vistos:
            resultado.rechazos.append((numero, f"_id duplicado: {documento['_id']}", fila))
            continue
        ids_vistos.add(documento["_id"])
        resultado.documentos.append(documento)
    return resultado
