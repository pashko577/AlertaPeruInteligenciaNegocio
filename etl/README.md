# ETL de ALERTA PERÚ

Carga los 142 139 registros del CSV de emergencias de INDECI en MongoDB
Atlas, aplicando las reglas de la auditoría (`../docs/DATASET_AUDIT.md`).

## Qué hace

1. Lee el CSV con codificación Latin-1 y valida que las 49 columnas sean las
   esperadas.
2. Transforma cada fila en un documento:
   - fechas en dos formatos (`DD/MM/AAAA` hasta 2024 y `MM/DD/AA` en 2025),
     validadas contra `AÑO` y `MES`;
   - ubigeo como texto;
   - celdas vacías omitidas (nunca se guardan como 0);
   - `_id` = `codigoSinpad-AAAA-MM-DD-ubigeo`.
3. Construye el catálogo `territorios` (26 departamentos INDECI, 196
   provincias, 1 888 distritos) con los nombres oficiales del IDE-INEI 2023.
4. Carga todo en Atlas por lotes, con *upsert* (no duplica al reejecutar),
   un ritmo máximo para respetar el límite de Atlas Free y reintentos ante
   errores de red.
5. Crea los índices aprobados y verifica el conteo final.
6. Registra la ejecución en `metadatos_carga`.

Si alguna fila no cumple las reglas, la carga **se detiene** antes de
escribir nada y el detalle queda en `logs/rechazados.csv`.

## Requisitos

- Python 3.10 o superior.
- El CSV en la raíz del proyecto (`../BD_2003-2025_EMERGENCIAS.csv`).
- Los límites del INEI en `../data/referencias/peru-geojson-2023/`.

```bash
cd etl
python -m pip install -r requirements.txt
```

## 1. Configurar MongoDB Atlas (en la web)

1. Entra a <https://cloud.mongodb.com> y crea un proyecto.
2. **Create cluster → Free (M0).** Elige una región cercana (por ejemplo,
   AWS São Paulo).
3. **Security → Database Access → Add New Database User:**
   - usuario del ETL, por ejemplo `etl_alerta`, con el rol
     *Read and write to any database*;
   - más adelante, el usuario del backend con *Only read any database*.
4. **Security → Network Access → Add IP Address → Add Current IP Address.**
5. **Clusters → Connect → Drivers → Python:** copia la cadena de conexión.

## 2. Crear el archivo `.env`

```bash
cp .env.example .env      # en Windows: copy .env.example .env
```

Edita `.env` y pega la cadena de conexión en `MONGODB_URI`, reemplazando
el usuario y la contraseña. **No compartas este archivo**: ya está en
`.gitignore`.

## 3. Ejecutar

```bash
# Pruebas de las reglas de transformación
python -m unittest discover -s tests -v

# Ensayo completo sin conectarse a Atlas (valida y transforma las 142 139 filas)
python cargar.py --dry-run

# Carga real
python cargar.py

# Verificación: compara Atlas con cálculos directos sobre el CSV
python verificar.py

# Cuadre final: CSV crudo frente a la API que usa la página (backend levantado)
python cuadrar_api.py
```

### Tiempo de carga

Por defecto se cargan como máximo **90 documentos por segundo**, por debajo
del límite de 100 operaciones por segundo de Atlas Free. Así, la carga
completa tarda **unos 26 minutos**. Si se interrumpe, basta con volver a
ejecutar `python cargar.py`: los documentos ya cargados se reemplazan por sí
mismos y no se duplican.

Opciones:

| Opción | Uso |
|---|---|
| `--dry-run` | Valida y transforma, pero no escribe en Atlas |
| `--lote N` | Documentos por lote (por defecto 500) |
| `--ritmo N` | Máximo de documentos por segundo (por defecto 90; `0` = sin límite) |
| `--permitir-rechazos` | Carga aunque haya filas rechazadas (no recomendado) |

## Archivos generados (`logs/`, fuera del repositorio)

| Archivo | Contenido |
|---|---|
| `etl_AAAAMMDD_HHMMSS.log` | Registro completo de la ejecución |
| `muestra_documentos.json` | Un documento de 2003, uno de 2019 y uno de 2025 |
| `rechazados.csv` | Filas rechazadas y su motivo (solo si las hay) |

## Estructura

```text
etl/
├── cargar.py               script principal
├── verificar.py            verificación posterior a la carga
├── cuadrar_api.py          cuadre del CSV crudo contra la API (lo que ve la página)
├── requirements.txt
├── .env.example
├── alerta_etl/
│   ├── config.py           variables de entorno y rutas
│   ├── columnas.py         mapeo de las 49 columnas del CSV
│   ├── transformacion.py   lectura, validación y fila → documento
│   ├── territorios.py      catálogo de departamentos, provincias y distritos
│   └── carga.py            conexión, carga por lotes, índices y metadatos
└── tests/
    └── test_transformacion.py
```
