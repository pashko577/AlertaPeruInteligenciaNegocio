# Backend de ALERTA PERÚ

API REST en **Spring Boot 4.1.2 / Java 25** con **arquitectura hexagonal**.
Consulta los datos cargados por el ETL en MongoDB Atlas.

## Arquitectura hexagonal

Principio: **el dominio no conoce MongoDB ni HTTP.** Se comunica con el exterior
mediante *puertos* (interfaces) que implementan los *adaptadores*.

```text
                 ┌───────────────────── infrastructure ─────────────────────┐
                 │                                                           │
  Navegador ───▶ │ adapter/in/web          application            adapter/out/mongo │ ───▶ MongoDB Atlas
  (HTTP/JSON)    │ Controllers ──▶ port/in ──▶ service ──▶ port/out ◀── Mongo*Adapter    │
                 │  + DTOs         (casos de uso)           (interfaces)  + Documents    │
                 │                        │                                      │
                 │                        ▼                                      │
                 │                     domain  (Java puro: modelos y reglas)     │
                 └───────────────────────────────────────────────────────────┘
```

| Capa | Paquete | Contenido | Depende de |
|---|---|---|---|
| Dominio | `domain` | `Emergencia`, `FiltroEmergencias` (valida reglas), `Indicadores` ("sin dato" ≠ 0), `Peligro` ("zOTROS" → "OTROS")... | nada |
| Aplicación | `application.port.in` | Casos de uso: `ConsultarEmergenciasUseCase`, `ObtenerEstadisticasUseCase`, `ObtenerCatalogosUseCase` | dominio |
| | `application.port.out` | Puertos: `EmergenciaRepositoryPort`, `EstadisticasRepositoryPort`, `CatalogoRepositoryPort` | dominio |
| | `application.service` | Implementación de los casos de uso (KPIs, ranking, nombres oficiales) | dominio y puertos |
| Infraestructura | `infrastructure.adapter.in.web` | `@RestController`, DTOs, manejo de errores | aplicación |
| | `infrastructure.adapter.out.mongo` | `MongoTemplate`, `@Document`, criterios y agregaciones | aplicación |
| | `infrastructure.config` | Ensambla los casos de uso con los adaptadores | todo |

Las clases de `domain` y `application` **no tienen anotaciones de Spring**. Lo
verifica automáticamente la prueba `ArquitecturaHexagonalTest`.

## Endpoints

Filtros comunes (todos opcionales): `anioDesde`, `anioHasta`, `departamento`
(nombre INDECI), `provincia` (ubigeo de 4 dígitos), `distrito` (ubigeo de 6
dígitos), `regionNatural`, `tipoPeligro`, `peligro`.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/emergencias` | Listado paginado. Además: `page`, `size` (≤ 100), `sort` (`fecha`, `departamento`, `peligro`, `fallecidos`, `damnificados`, `afectados`, `viviendasDestruidas`), `dir` (`asc`/`desc`) |
| GET | `/api/emergencias/{id}` | Detalle con todos los indicadores y notas de calidad |
| GET | `/api/estadisticas/resumen` | KPIs |
| GET | `/api/estadisticas/por-anio` | Serie anual |
| GET | `/api/estadisticas/por-peligro` | Distribución por peligro |
| GET | `/api/estadisticas/por-territorio?nivel=` | `departamento`, `provincia` o `distrito` (ranking y mapa) |
| GET | `/api/catalogos/departamentos` | 26 departamentos INDECI |
| GET | `/api/catalogos/provincias?departamento=` | Provincias de un departamento |
| GET | `/api/catalogos/distritos?provincia=` | Distritos de una provincia |
| GET | `/api/catalogos/peligros` | Peligros agrupados por tipo |
| GET | `/api/catalogos/regiones` | Regiones naturales |
| GET | `/api/metadatos/ultima-carga` | Fecha y datos de la última carga del ETL |

## Frontend

La interfaz está en `src/main/resources/static/` y se sirve desde el mismo
Spring Boot en <http://localhost:8080>. Es HTML, CSS y JavaScript (módulos ES)
sin compilación:

| Archivo | Responsabilidad |
|---|---|
| `index.html` | Estructura: frase de consulta, pestañas, detalle |
| `css/alerta.css` | Sistema visual (tokens de color, tipografía, modo oscuro) |
| `js/consulta.js` | Estado de los filtros, selectores en cascada, URL compartible |
| `js/panorama.js` | Respuesta principal, cifras y gráficos (Chart.js) |
| `js/mapa.js` | Mapa coroplético por niveles (Leaflet + GeoJSON INEI) |
| `js/registros.js` | Tabla paginada y detalle de cada registro |
| `vendor/`, `geo/` | Librerías, fuente y límites territoriales locales |

Justificación del diseño: `../docs/DECISION_DISENO_FRONTEND.md`.

Ejemplos:

```text
/api/estadisticas/resumen?departamento=CUSCO&anioDesde=2020&anioHasta=2025
/api/estadisticas/por-territorio?nivel=provincia&departamento=PUNO
/api/emergencias?provincia=0801&peligro=HUAYCO&sort=damnificados&dir=desc
```

Los errores devuelven JSON: `{"estado": 400, "error": "Bad Request", "mensaje": "..."}`.

## Configuración

1. En Atlas, crea un usuario de **solo lectura** para el backend:
   **Security → Database Access → Add New Database User**, con el rol
   *Only read any database*.
2. Copia `.env.example` como `.env` en esta carpeta y completa `MONGODB_URI`
   con ese usuario. El archivo `.env` está en `.gitignore`.

También se puede definir `MONGODB_URI` como variable de entorno del sistema.

## Ejecutar

Requisitos: JDK 25 o superior (probado con JDK 26). No hace falta instalar
Maven: se usa el *wrapper* incluido.

```bash
cd backend
./mvnw test              # pruebas (no necesitan MongoDB)
./mvnw spring-boot:run   # inicia en http://localhost:8080
```

En Windows (CMD o PowerShell): `mvnw.cmd test` y `mvnw.cmd spring-boot:run`.

## Pruebas

| Prueba | Qué verifica |
|---|---|
| `ArquitecturaHexagonalTest` | `domain` y `application` no importan Spring, MongoDB ni infraestructura |
| `FiltroEmergenciasTest` | Reglas de filtros, paginación, "sin dato" ≠ 0, etiqueta "OTROS" |
| `EstadisticasServiceTest` | KPIs, variación interanual, nombres oficiales (CONTAMANA) |
| `CatalogoYConsultaServiceTest` | Lima Metropolitana / Lima Provincias, 404 y 400 |
| `ApiWebTest` | Endpoints, JSON y códigos de error, con puertos en memoria |

Los casos de uso se prueban **sin MongoDB**, con implementaciones en memoria de
los puertos (`Fakes`). Esa es la ventaja práctica de la arquitectura hexagonal.
