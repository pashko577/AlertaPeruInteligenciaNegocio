# ALERTA PERÚ

Consulta y análisis de las **142 139 emergencias** registradas por INDECI en el
SINPAD entre 2003 y 2025. Los datos están en **MongoDB Atlas**. Un backend
**Spring Boot con arquitectura hexagonal** expone una API REST, y una página web
HTML/JS permite filtrar los datos por territorio (departamento, provincia,
distrito), años, región natural y peligro.

Proyecto académico del curso de Inteligencia de Negocios. Fuente: INDECI,
*Emergencias y daños a nivel nacional*, Plataforma Nacional de Datos Abiertos.

```text
BD_2003-2025_EMERGENCIAS.csv
        │  etl/ (Python): limpia, valida y carga por lotes
        ▼
MongoDB Atlas (M0, gratuito) ── base alerta_peru: emergencias, territorios, cargas
        │  usuario de solo lectura
        ▼
backend/ (Spring Boot 4, Java 25, hexagonal) ── /api/...
        │  mismo servidor
        ▼
Página web (HTML + CSS + JS, Chart.js, Leaflet) ── http://localhost:8080
```

## Estructura

| Carpeta o archivo | Contenido |
|---|---|
| `etl/` | Carga del CSV en Atlas, verificación y cuadre de totales ([README](etl/README.md)) |
| `backend/` | API REST y página web ([README](backend/README.md)) |
| `docs/` | Documentación: decisiones, auditoría, contexto y guion de demo |
| `data/referencias/` | Límites territoriales INEI 2023 (GeoJSON, licencia MIT) |
| `BD_2003-2025_EMERGENCIAS.csv` | Datos originales de INDECI |
| `Diccionario de datos_9.xlsx` | Diccionario oficial del dataset |

## Documentación

| Documento | Para qué sirve |
|---|---|
| [ALERTA_PERU_CONTEXTO_PROYECTO.md](docs/ALERTA_PERU_CONTEXTO_PROYECTO.md) | Documento maestro: alcance, arquitectura, modelo de datos y fases |
| [DATASET_AUDIT.md](docs/DATASET_AUDIT.md) | Auditoría del CSV: problemas encontrados y cómo se resolvió cada uno |
| [DECISION_MAPA_COROPLETICO.md](docs/DECISION_MAPA_COROPLETICO.md) | Por qué el mapa pinta territorios y no usa puntos |
| [DECISION_DISENO_FRONTEND.md](docs/DECISION_DISENO_FRONTEND.md) | Decisiones de diseño de la página |
| [DECISION_ARQUITECTURA_HEXAGONAL.md](docs/DECISION_ARQUITECTURA_HEXAGONAL.md) | Por qué el backend es hexagonal y cómo se organiza |
| [GUION_DEMO.md](docs/GUION_DEMO.md) | Guion para la exposición |

## Cómo ejecutarlo

Requisitos: **Python 3.12+**, **JDK 25 o superior** y una cuenta de MongoDB
Atlas. No hace falta instalar Maven, porque el proyecto incluye el *wrapper*.

### 1. MongoDB Atlas

1. Crea un clúster **M0 (Free)** y, en *Network Access*, permite tu IP.
2. En *Database Access*, crea **dos usuarios**:
   - uno con permisos de escritura, para el ETL;
   - uno con el rol *Only read any database*, para el backend.

   Así la aplicación web no puede modificar los datos.

### 2. Cargar los datos (una sola vez)

```bash
cd etl
pip install -r requirements.txt
copy .env.example .env          # completa MONGODB_URI con el usuario del ETL
python cargar.py --dry-run      # valida las 142 139 filas sin conectarse
python cargar.py                # carga real (~27 min, respeta el límite de M0)
python verificar.py             # compara Atlas con el CSV
```

### 3. Levantar el backend y la página

```bash
cd backend
copy .env.example .env          # completa MONGODB_URI con el usuario de solo lectura
mvnw.cmd test                   # 34 pruebas, no necesitan MongoDB
mvnw.cmd spring-boot:run
```

Abre <http://localhost:8080>. En Linux o macOS usa `./mvnw` en lugar de `mvnw.cmd`.

> Los archivos `.env` contienen contraseñas y están en `.gitignore`. Nunca se
> suben al repositorio.

## Cómo se comprueba que los datos son correctos

| Comprobación | Comando | Resultado |
|---|---|---|
| Reglas de limpieza (fechas, vacíos, ubigeos) | `cd etl && python -m unittest` | 23 pruebas OK |
| CSV frente a Atlas: conteos, sumas, vacíos, índices, espacio | `python etl/verificar.py` | Todo OK; 183,8 MB de 512 MB |
| **CSV frente a lo que muestra la página** | `python etl/cuadrar_api.py` | **15 de 15 cuadran** |
| Backend: arquitectura, filtros, casos de uso, API | `mvnw.cmd test` | 34 pruebas OK |

`cuadrar_api.py` es la prueba más importante para el objetivo del curso. Lee el
CSV con la biblioteca estándar de Python, sin pasar por el código del ETL, y
compara sus cálculos con las respuestas JSON que usa la página. Revisa:

- los 10 indicadores nacionales;
- los 23 años;
- los 22 peligros;
- los 26 departamentos (Lima separada en Metropolitana y Provincias);
- las 13 provincias de Cusco;
- 5 consultas con filtros combinados, por ejemplo Puno con bajas temperaturas,
  selva baja con peligros de origen humano entre 2010 y 2015, o un distrito.

En las consultas con filtros también compara el conteo de la pestaña Registros.

## Decisiones importantes sobre los datos

- **Vacío no es cero.** Si una celda del CSV está vacía, la página muestra "sin dato".
- **Educación cambia en 2019.** Antes se contaban centros educativos y desde
  2019 se cuentan aulas. Las dos series no se suman.
- **El identificador combina código, fecha y ubigeo.** El código SINPAD no es
  único: 7 códigos se repiten en filas distintas.
- **Dos formatos de fecha.** De 2003 a 2024 las fechas son DD/MM/AAAA y en 2025
  son MM/DD/AA. El ETL comprueba cada fecha contra las columnas `AÑO` y `MES`.
- **Lima tiene dos partes.** El INDECI separa Lima Metropolitana y Lima
  Provincias. El mapa las pinta como un solo departamento (Lima) y lo indica.
- **Las unidades de la ayuda humanitaria no están confirmadas.** El diccionario
  dice toneladas y miles de soles, pero las magnitudes sugieren kilos y soles.
  Se muestran con esa advertencia.

El detalle de cada punto está en [DATASET_AUDIT.md](docs/DATASET_AUDIT.md).

## Límites conocidos

- **Atlas M0** admite 100 operaciones por segundo y se pausa tras 30 días sin
  uso. Si la página no carga datos, revisa en Atlas que el clúster esté activo.
- **Ordenar por cifras** (fallecidos, damnificados, afectados, viviendas
  destruidas) permite llegar hasta el registro 10 000. Ordenar por fecha no
  tiene ese límite.
- **Un distrito no tiene polígono.** El distrito 130112 Alto Trujillo (11
  registros de 2025) no está en el GeoJSON del INEI 2023. Sus datos sí
  aparecen en totales y tablas.
- No es un sistema oficial de alertas ni de predicción: analiza registros históricos.
