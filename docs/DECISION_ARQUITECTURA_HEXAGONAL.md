# Decisión: arquitectura hexagonal en el backend

**Proyecto:** ALERTA PERÚ\
**Componente:** `backend/`, Spring Boot 4.1.2 y Java 25\
**Estado:** implementada y verificada con pruebas automáticas

## 1. Qué es, en una frase

La **arquitectura hexagonal** (o de *puertos y adaptadores*) pone las reglas
del negocio en el centro. Todo lo técnico queda alrededor: la web, MongoDB y
Spring. El centro **define interfaces (puertos)** y la tecnología **se conecta
a ellas (adaptadores)**. El centro nunca depende de la tecnología.

## 2. Por qué la usamos en este proyecto

| Razón | Cómo se nota en ALERTA PERÚ |
|---|---|
| **Las reglas de los datos son lo valioso** | "Vacío no es cero", "zOTROS se muestra como OTROS", "anioDesde ≤ anioHasta", "Lima Metropolitana es la provincia 1501". Viven en `domain/` y `application/`, en Java puro, y no se mezclan con consultas Mongo ni con HTTP. |
| **Se puede probar sin base de datos** | Las pruebas de los casos de uso usan repositorios falsos en memoria (`Fakes.java`). Las 34 pruebas corren en segundos, sin conexión a Atlas y sin contraseñas. |
| **La base de datos es reemplazable** | Si mañana se usa PostgreSQL en vez de MongoDB, solo se escribe un adaptador nuevo en `adapter/out/`. Dominio, casos de uso y controladores no cambian. |
| **La interfaz es reemplazable** | La página web usa la API REST. Una app móvil u otro sistema podría usar los mismos casos de uso con otro adaptador de entrada. |
| **Errores más fáciles de ubicar** | Si un total sale mal, se sabe dónde mirar: la regla está en `application/` y la consulta en el adaptador Mongo. |

**El costo:** hay más archivos e interfaces que en un CRUD simple con
`@Service` y `@Repository`. Lo aceptamos porque es un proyecto de
Inteligencia de Negocios: lo central es que las cifras sean correctas y
demostrables, y esta separación permite probar esas reglas de forma aislada.

## 3. Las capas

```text
                        ┌──────────── infrastructure ────────────┐
                        │                                        │
  Navegador ──HTTP──▶   │  adapter/in/web          adapter/out/mongo  ──▶ MongoDB Atlas
  (página)              │  (controladores)         (MongoTemplate)│
                        │        │                       ▲        │
                        │        ▼                       │        │
                        │   ┌────────── application ──────────┐   │
                        │   │ port/in ──▶ service ──▶ port/out│   │
                        │   │ (qué se puede    (interfaces que│   │
                        │   │  pedir)           Mongo cumple) │   │
                        │   │        ┌─── domain ───┐         │   │
                        │   │        │ modelos y    │         │   │
                        │   │        │ reglas puras │         │   │
                        │   │        └──────────────┘         │   │
                        │   └─────────────────────────────────┘   │
                        └────────────────────────────────────────┘

  Las dependencias apuntan siempre HACIA EL CENTRO.
```

### `domain/`: el negocio, en Java puro

No importa nada de Spring ni de MongoDB.

| Clase | Qué representa o qué regla aplica |
|---|---|
| `Emergencia`, `Ubicacion`, `Peligro` | Un registro del SINPAD. `Peligro` convierte "zOTROS" en "OTROS". |
| `Indicadores` | Los daños por grupo. Un dato ausente es "sin dato", no 0. |
| `FiltroEmergencias` | Los filtros, con sus reglas: años válidos, ubigeo de 6 dígitos, desde ≤ hasta. |
| `Paginacion` | Página, tamaño (máximo 100) y campo de orden permitido. |
| `TotalesIndicadores`, `ResumenEstadistico`, `ConteoAgrupado` | Resultados de los KPIs y agrupaciones. |
| `FiltroInvalidoException`, `RecursoNoEncontradoException` | Errores del negocio. |

### `application/`: los casos de uso

| Paquete | Contenido |
|---|---|
| `port/in` | **Lo que el sistema ofrece** (interfaces): `ConsultarEmergenciasUseCase`, `ObtenerEstadisticasUseCase`, `ObtenerCatalogosUseCase`, `ObtenerInfoCargaUseCase`. |
| `port/out` | **Lo que el sistema necesita de afuera** (interfaces): `EmergenciaRepositoryPort`, `EstadisticasRepositoryPort`, `CatalogoRepositoryPort`, `InfoCargaRepositoryPort`. |
| `service` | La implementación de los casos de uso: calcula KPIs (damnificados por emergencia, variación interanual), elige el peligro más frecuente y usa los nombres oficiales del INEI. |

### `infrastructure/`: la tecnología

| Paquete | Contenido |
|---|---|
| `adapter/in/web` | `@RestController` que reciben HTTP, convierten los parámetros a objetos del dominio y devuelven JSON (DTOs). `ManejadorErrores` traduce los errores a 400 o 404. |
| `adapter/out/mongo` | Implementan los puertos de salida con `MongoTemplate`. `CriteriosMongo` traduce el filtro a una consulta Mongo, `MongoMapper` convierte documentos en objetos del dominio y `document/` mapea las colecciones. |
| `config` | `CasosDeUsoConfig` es **el único lugar que conecta todo**: crea los servicios y les pasa los adaptadores Mongo. |

## 4. Recorrido de una consulta real

Cuando la página pide *"emergencias en Puno, ordenadas por damnificados"*:

```text
GET /api/emergencias?departamento=PUNO&sort=damnificados&dir=desc
 │
 ▼  adapter/in/web/EmergenciaController         (infraestructura)
 │  Convierte los parámetros en FiltroEmergencias + Paginacion.
 │  Si "sort" no es válido → FiltroInvalidoException → 400.
 ▼  port/in/ConsultarEmergenciasUseCase         (interfaz)
 ▼  service/ConsultarEmergenciasService         (aplicación)
 │  Aplica la regla y llama al puerto de salida. No sabe que existe Mongo.
 ▼  port/out/EmergenciaRepositoryPort           (interfaz)
 ▼  adapter/out/mongo/MongoEmergenciaAdapter    (infraestructura)
 │  CriteriosMongo: { "ubicacion.departamento": "PUNO" }
 │  orden: { "personas.damnificados": -1 }
 ▼  MongoDB Atlas
 │
 ◀── vuelve como Pagina<Emergencia> (dominio) → WebMapper → JSON → la página
```

El servicio recibe una `EmergenciaRepositoryPort`, que es una interfaz. En
producción Spring le entrega `MongoEmergenciaAdapter`. En las pruebas recibe
un repositorio falso en memoria. **El servicio es el mismo en los dos casos.**

## 5. Cómo se verifica que la regla se cumple

No basta con decir que la arquitectura es hexagonal. La prueba automática
`ArquitecturaHexagonalTest` **falla** si alguna clase de `domain/` o
`application/` importa algo de:

- `org.springframework`, `com.mongodb`, `org.bson` o `jakarta.`;
- `pe.alertaperu.infrastructure`.

También falla si `domain/` importa algo de `application/`. Por eso los
servicios no llevan `@Service`: se registran en `CasosDeUsoConfig`.

```bash
cd backend
mvnw.cmd test        # 34 pruebas, incluida ArquitecturaHexagonalTest
```

| Prueba | Capa que prueba | ¿Necesita MongoDB? |
|---|---|---|
| `FiltroEmergenciasTest` | Dominio | No |
| `EstadisticasServiceTest`, `CatalogoYConsultaServiceTest` | Aplicación, con `Fakes` | No |
| `ApiWebTest` | Adaptador web + casos de uso reales + `Fakes` | No |
| `ArquitecturaHexagonalTest` | Estructura de paquetes | No |

## 6. Comparación con la forma tradicional

| Forma tradicional (por capas) | Hexagonal (este proyecto) |
|---|---|
| `Controller → Service → Repository extends MongoRepository` | `Controller → UseCase → Service → Port ← MongoAdapter` |
| El servicio depende de la clase de Spring Data | El servicio depende de una interfaz propia |
| Para probar el servicio se necesita la base de datos o mocks de Spring | Se prueba con un `Fake` de pocas líneas |
| Cambiar de base de datos afecta al servicio | Solo cambia el adaptador |
| Menos archivos | Más archivos, pero cada uno tiene una sola responsabilidad |
