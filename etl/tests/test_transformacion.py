"""Pruebas de las reglas de transformación del ETL.

Ejecutar desde la carpeta etl/:
    python -m unittest discover -s tests -v
"""

import unittest
from datetime import datetime

from alerta_etl.columnas import ENCABEZADOS_ESPERADOS, validar_encabezados
from alerta_etl.transformacion import (
    FilaInvalida,
    ResultadoLectura,
    convertir_fecha,
    convertir_numero,
    transformar_fila,
)

# Primera fila real del CSV (2003)
FILA_2003 = ("351;11/02/2003;2003;FEBRERO;010101;AMAZONAS;CHACHAPOYAS;CHACHAPOYAS;"
             "LLUVIA INTENSA;ORIGEN NATURAL;SIERRA;0;0;0;4;0;1;0;0;0;;;;;;0;0;0;0;0;0;0;0;"
             "0;0;0;0;0;0;0;0;0;0;0;0;0;0;363.24;345.4446").split(";")


def fila_con(**cambios) -> list[str]:
    fila = list(FILA_2003)
    posiciones = {"codigo": 0, "fecha": 1, "anio": 2, "mes": 3, "ubigeo": 4, "dpto": 5,
                  "peligro": 8, "aulas_afectadas": 21, "costo": 48}
    for clave, valor in cambios.items():
        fila[posiciones[clave]] = valor
    return fila


class TestFechas(unittest.TestCase):
    def test_formato_dd_mm_aaaa(self):
        self.assertEqual(convertir_fecha("11/02/2003", "2003", "FEBRERO"), datetime(2003, 2, 11))

    def test_formato_mm_dd_aa_de_2025(self):
        # Ejemplo real del CSV: 02/15/25 = 15 de febrero de 2025
        self.assertEqual(convertir_fecha("02/15/25", "2025", "FEBRERO"), datetime(2025, 2, 15))

    def test_2025_dia_menor_a_13_no_se_invierte(self):
        # 03/05/25 debe ser 5 de marzo (MM/DD), no 3 de mayo
        self.assertEqual(convertir_fecha("03/05/25", "2025", "MARZO"), datetime(2025, 3, 5))

    def test_fecha_incoherente_con_mes_se_rechaza(self):
        with self.assertRaises(FilaInvalida):
            convertir_fecha("03/05/25", "2025", "MAYO")

    def test_fecha_incoherente_con_anio_se_rechaza(self):
        with self.assertRaises(FilaInvalida):
            convertir_fecha("11/02/2003", "2004", "FEBRERO")

    def test_fecha_inexistente_se_rechaza(self):
        with self.assertRaises(FilaInvalida):
            convertir_fecha("31/02/2010", "2010", "FEBRERO")

    def test_setiembre(self):
        self.assertEqual(convertir_fecha("22/09/2003", "2003", "SETIEMBRE"), datetime(2003, 9, 22))


class TestNumeros(unittest.TestCase):
    def test_vacio_es_none_y_no_cero(self):
        self.assertIsNone(convertir_numero(""))
        self.assertIsNone(convertir_numero("  "))

    def test_cero_es_cero(self):
        self.assertEqual(convertir_numero("0"), 0)

    def test_entero_y_decimal(self):
        self.assertEqual(convertir_numero("12"), 12)
        self.assertIsInstance(convertir_numero("12"), int)
        self.assertEqual(convertir_numero("363.24"), 363.24)

    def test_texto_no_numerico_se_rechaza(self):
        with self.assertRaises(FilaInvalida):
            convertir_numero("abc")


class TestDocumento(unittest.TestCase):
    def test_documento_de_la_primera_fila(self):
        doc = transformar_fila(FILA_2003)
        self.assertEqual(doc["_id"], "351-2003-02-11-010101")
        self.assertEqual(doc["codigoSinpad"], "351")
        self.assertEqual(doc["fecha"], datetime(2003, 2, 11))
        self.assertEqual((doc["anio"], doc["mes"]), (2003, 2))
        self.assertEqual(doc["ubicacion"]["ubigeo"], "010101")
        self.assertEqual(doc["ubicacion"]["codDepartamento"], "01")
        self.assertEqual(doc["ubicacion"]["codProvincia"], "0101")
        self.assertEqual(doc["personas"]["damnificados"], 4)
        self.assertEqual(doc["vivienda"]["destruidas"], 1)
        self.assertEqual(doc["ayuda"], {"peso": 363.24, "costo": 345.4446})

    def test_ubigeo_conserva_cero_inicial(self):
        doc = transformar_fila(FILA_2003)
        self.assertIsInstance(doc["ubicacion"]["ubigeo"], str)
        self.assertTrue(doc["ubicacion"]["ubigeo"].startswith("0"))

    def test_columnas_vacias_se_omiten(self):
        # En 2003 las columnas de aulas están vacías: no deben aparecer como 0
        doc = transformar_fila(FILA_2003)
        self.assertNotIn("aulasAfectadas", doc["educacion"])
        self.assertNotIn("centrosEvaluados", doc["educacion"])
        self.assertEqual(doc["educacion"]["centrosDestruidos"], 0)

    def test_cero_explicito_se_conserva(self):
        doc = transformar_fila(fila_con(aulas_afectadas="0"))
        self.assertEqual(doc["educacion"]["aulasAfectadas"], 0)

    def test_ubigeo_invalido_se_rechaza(self):
        with self.assertRaises(FilaInvalida):
            transformar_fila(fila_con(ubigeo="10101"))

    def test_departamento_vacio_se_rechaza(self):
        with self.assertRaises(FilaInvalida):
            transformar_fila(fila_con(dpto=" "))

    def test_fila_con_campos_de_menos_se_rechaza(self):
        with self.assertRaises(FilaInvalida):
            transformar_fila(FILA_2003[:-1])

    def test_negativo_se_conserva_y_se_advierte(self):
        resultado = ResultadoLectura()
        doc = transformar_fila(fila_con(costo="-265.2536"), resultado)
        self.assertEqual(doc["ayuda"]["costo"], -265.2536)
        self.assertEqual(resultado.advertencias, {"valor negativo en COSTO DE LA AYUDA": 1})

    def test_peligro_original_se_conserva(self):
        doc = transformar_fila(fila_con(peligro="zOTROS"))
        self.assertEqual(doc["peligro"]["nombre"], "zOTROS")


class TestEncabezados(unittest.TestCase):
    def test_espacios_irregulares_se_aceptan(self):
        encabezados = list(ENCABEZADOS_ESPERADOS)
        encabezados[1] = "FECHA  DE LA EMER"
        encabezados[31] = "CARRETERA DESTRUIDA KM "
        validar_encabezados(encabezados)  # no debe lanzar error

    def test_columna_faltante_se_detecta(self):
        with self.assertRaises(ValueError):
            validar_encabezados(ENCABEZADOS_ESPERADOS[:-1])

    def test_columna_cambiada_se_detecta(self):
        encabezados = list(ENCABEZADOS_ESPERADOS)
        encabezados[14] = "DAMNIFICADOS TOTALES"
        with self.assertRaises(ValueError):
            validar_encabezados(encabezados)


if __name__ == "__main__":
    unittest.main()
