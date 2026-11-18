# ALERTA PERÚ

## Documento de contexto y guía de desarrollo del proyecto

**Versión:** 1.2\
**Fecha:** 18 de noviembre de 2026\
**Propósito:** servir como documento maestro de contexto para trabajar
el proyecto con Claude y mantener decisiones, alcance y criterios
técnicos consistentes.

**Cambios de la versión 1.2:**

-   El alcance se ajusta a lo que pide el docente: integración de
    MongoDB Atlas con un frontend en HTML, CSS y JavaScript simple
    servido por Spring Boot. Se quitan React, Vite y Recharts.
-   El backend usa **arquitectura hexagonal** (sección 7).
-   Los filtros quedan definidos: rango de años, departamento →
    provincia → distrito, región natural, tipo de peligro y peligro.
-   Se incorporan los límites oficiales de Atlas Free y la medición real
    de almacenamiento (\~45 % del límite).
-   Se presenta el modelo de datos final con sus índices, **pendiente de
    aprobación** (sección 9).
-   La colección `agregados` pasa a ser una optimización opcional, que
    solo se crea si las mediciones la justifican.

**Documentos relacionados:**

-   `DATASET_AUDIT.md`: auditoría completa del CSV y del diccionario.
-   `DECISION_MAPA_COROPLETICO.md`: justificación del tipo de mapa.
-   `DECISION_DISENO_FRONTEND.md`: decisiones de diseño de la página.
-   `DECISION_ARQUITECTURA_HEXAGONAL.md`: justificación y estructura del backend.
-   `README.md`: instalación, ejecución y verificación.
-   `GUION_DEMO.md`: guion de la exposición.

------------------------------------------------------------------------

## 1. Resumen del proyecto

**ALERTA PERÚ** es un proyecto universitario del curso de **Inteligencia
de Negocios**. Consiste en una aplicación web para analizar las
emergencias y desastres registrados en el Perú entre 2003 y 2025, a
partir de los datos abiertos oficiales del Instituto Nacional de Defensa
Civil (INDECI).

**Lo que pide el docente:** integrar **MongoDB Atlas** con un frontend
básico que represente **correctamente** todos los datos y permita
filtrarlos por territorio (departamento, provincia, distrito), años y
otros criterios.

El proyecto carga los **142 139 registros reales** del dataset en
MongoDB Atlas (clúster gratuito, gestionado desde la web de Atlas), los
expone con una API REST en Spring Boot y los muestra en un dashboard
web con indicadores, gráficos, mapa y tabla de registros.

> Regla fundamental: no inventar registros, no duplicarlos y no asumir
> el significado de una columna sin contrastarlo con el diccionario
> oficial. Toda cifra presentada debe poder rastrearse hasta el CSV de
> origen.

La plataforma es **analítica e informativa**. No es un sistema oficial
de alertas en tiempo real, de predicción ni de despacho de ayuda.

## 2. Preguntas que responde

-   ¿Cuántas emergencias se registraron en un periodo y territorio
    determinados?
-   ¿Qué tipos de peligro son más frecuentes?
-   ¿Qué departamentos, provincias o distritos registran más eventos?
-   ¿Cómo evoluciona la cantidad de emergencias año a año?
-   ¿Cuántos fallecidos, damnificados, afectados y viviendas dañadas
    reporta la fuente según el filtro aplicado?
-   ¿Qué registros concretos corresponden a un filtro?

## 3. Fuente de datos

-   **Dataset:** Emergencias y Evaluación de Daños -- SINPAD 2003--2025.
-   **Entidad:** Instituto Nacional de Defensa Civil (INDECI).
-   **Página del dataset:**\
    https://www.datosabiertos.gob.pe/dataset/emergencias-y-daños-nivel-nacional-por-departamento
-   **CSV:**\
    https://www.datosabiertos.gob.pe/sites/default/files/BD_2003-2025_EMERGENCIAS.csv
-   **Diccionario:** `Diccionario de datos_9.xlsx`, en la carpeta del
    proyecto.

### Resumen de la auditoría

Detalle completo en `DATASET_AUDIT.md`.

  Característica                 Valor verificado
  ------------------------------ ------------------------------------------
  Filas                          **142 139**
  Columnas                       49 (todas las filas completas)
  Codificación / separador       ISO-8859-1 (Latin-1) / `;`
  Periodo                        2003 a 2025
  Filas duplicadas               0
  Territorio                     26 departamentos según INDECI (25 códigos), 196 provincias, 1 888 distritos
  Peligros                       22, en 2 tipos
  Regiones naturales             Costa, Sierra, Selva alta, Selva baja

### Reglas obligatorias para el ETL

1.  **Codificación:** leer con `encoding="latin-1"` y separador `;`.
2.  **Nombres de columna:** mapeo explícito, porque hay encabezados con
    espacios irregulares (`FECHA  DE LA EMER`,
    `CARRETERA DESTRUIDA KM `).
3.  **Fechas con dos formatos:** de 2003 a 2024 vienen como
    `DD/MM/AAAA`; en 2025, como `MM/DD/AA`. El formato se decide por la
    longitud del año y después se valida contra `AÑO` y `MES`.
4.  **Ubigeo como texto:** `010101`, nunca como número.
5.  **Vacío frente a cero:** una celda vacía se guarda como `null` (o
    se omite el campo) y **nunca como 0**.
6.  **Clave:** el código SINPAD no es único (7 se repiten en eventos
    distintos). El `_id` es `codigoSinpad-fecha-ubigeo`, que es único en
    las 142 139 filas.
7.  **Nombres originales:** se conservan tal cual en cada documento. Los
    filtros usan el catálogo de territorios, con un nombre oficial por
    ubigeo.

### Hallazgos que condicionan la presentación

-   **Ayuda (peso y costo):** las unidades del diccionario no son
    coherentes con las magnitudes del CSV. Se cargan, pero **no se
    muestran como KPI** ni con unidad.
-   **Educación:** hasta 2018 se cuentan centros educativos; desde 2019,
    aulas. Se muestran como **dos series separadas**.
-   **Canales de regadío:** unidad ambigua, así que no se suman.
-   **Valores atípicos** (por ejemplo, 1 411 000 camélidos afectados):
    no se modifican; se señalan cuando dominan un total.
-   **8 ubigeos con nombres distintos** (cortados, cambios de nombre y un
    error): manda el ubigeo.

## 4. Objetivos

### Objetivo general

Integrar MongoDB Atlas con una aplicación web que permita consultar y
analizar, con filtros, los 142 139 registros históricos de emergencias
del Perú, representando los datos de forma fiel a la fuente.

### Objetivos específicos

-   Auditar el dataset oficial y documentar su calidad. *(Completado)*
-   Cargar los 142 139 registros en MongoDB Atlas mediante un ETL
    reproducible en Python.
-   Diseñar documentos e índices adecuados a los filtros.
-   Implementar una API REST en Spring Boot con arquitectura hexagonal.
-   Construir un frontend en HTML, CSS y JavaScript con indicadores,
    gráficos, mapa y tabla filtrables.
-   Medir el rendimiento de las consultas y el consumo de Atlas.

## 5. Enfoque de Inteligencia de Negocios

### Dimensiones

  ---------------------------------------------------------------------------
  Dimensión          Jerarquía                         Origen
  ------------------ --------------------------------- ----------------------
  Tiempo             Año → Mes → Fecha                 `FECHA  DE LA EMER`

  Territorio         Departamento → Provincia →        `COD. DISTRITO` y
                     Distrito                          nombres

  Región natural     Costa / Sierra / Selva alta /     `REGIÓN NATURAL`
                     Selva baja

  Peligro            Tipo de peligro → Peligro         `TIPO DE PELIGRO`,
                                                       `PELIGRO`
  ---------------------------------------------------------------------------

### Medidas

-   **Conteo de emergencias.**
-   **Personas:** fallecidos, desaparecidos, lesionados, damnificados,
    afectados.
-   **Vivienda:** destruidas y afectadas.
-   **Salud:** centros destruidos y afectados.
-   **Educación:** centros (2003–2018) y aulas (2019–2025), por
    separado.
-   **Agricultura:** hectáreas destruidas y afectadas.
-   **Infraestructura vial:** puentes, km de carretera y de camino rural.
-   **No sumables:** canales de regadío y peso y costo de la ayuda.

### KPIs del dashboard

  -----------------------------------------------------------------------
  KPI                         Fórmula
  --------------------------- -------------------------------------------
  Total de emergencias        conteo de registros

  Fallecidos                  suma de fallecidos

  Damnificados                suma de damnificados

  Viviendas destruidas        suma de viviendas destruidas

  Damnificados por emergencia suma de damnificados / total de emergencias

  Variación interanual        (año N − año N−1) / año N−1, cuando el
                              filtro abarca al menos dos años

  Peligro más frecuente       peligro con mayor conteo
  -----------------------------------------------------------------------

Cada KPI se muestra con el periodo y los filtros aplicados.

## 6. Alcance funcional

Una sola aplicación web con una **barra de filtros común**. Al aplicar
un filtro se actualizan juntos todos los componentes.

### Filtros

  ------------------------------------------------------------------------------
  Filtro              Control                           Origen de las opciones
  ------------------- --------------------------------- --------------------------
  Años                desde / hasta (2003–2025)         fijo

  Departamento        lista (26, como INDECI)           API de catálogos

  Provincia           lista dependiente del             API de catálogos
                      departamento

  Distrito            lista dependiente de la           API de catálogos
                      provincia

  Región natural      lista (4)                         API de catálogos

  Tipo de peligro     lista (2)                         API de catálogos

  Peligro             lista dependiente del tipo (22)   API de catálogos
  ------------------------------------------------------------------------------

### Secciones

1.  **Dashboard:** tarjetas KPI, evolución anual (líneas), distribución
    por peligro (barras) y ranking territorial (barras).
2.  **Mapa:** mapa coroplético por departamento, provincia o distrito
    (ver `DECISION_MAPA_COROPLETICO.md`). Es el **primer elemento que se
    recorta** si falta tiempo.
3.  **Registros:** tabla paginada desde el servidor, ordenable, con
    vista de detalle de cada registro.
4.  **Acerca de los datos:** fuente, atribución a INDECI, fecha de carga
    y limitaciones (las de la sección 3).

### Criterios de representación correcta

-   Los valores vacíos se muestran como "sin dato", no como 0.
-   Educación aparece en dos series separadas.
-   Peso y costo de la ayuda aparecen solo en el detalle del registro,
    con la nota "unidad no verificada".
-   `zOTROS` se muestra como "Otros".
-   Siempre se indican el periodo y los filtros aplicados.
-   El mapa muestra cantidad de registros, no niveles de riesgo.

### Fuera del alcance

Alertas en tiempo real, predicciones, recomendaciones de evacuación,
despacho de recursos y afirmaciones de causalidad o riesgo.

## 7. Arquitectura

### Componentes

  -----------------------------------------------------------------------
  Capa                    Tecnología              Responsabilidad
  ----------------------- ----------------------- -----------------------
  ETL                     Python 3 (librería      Leer, validar,
                          estándar) + pymongo     transformar y cargar
                                                  el CSV

  Base de datos           MongoDB Atlas Free      Documentos e índices
                          (M0), gestionado desde
                          la web

  Backend                 Java 25 / Spring Boot   API REST, filtros y
                          4.1.2, arquitectura     agregaciones
                          hexagonal

  Frontend                HTML + CSS + JavaScript Interfaz y filtros
                          simple, servido por
                          Spring Boot
                          (`static/`)

  Gráficos                Chart.js (CDN)          Gráficos estadísticos

  Mapa                    Leaflet (CDN) + GeoJSON Mapa coroplético
                          INEI
  -----------------------------------------------------------------------

Al servir el frontend desde el propio Spring Boot hay un solo proyecto
que ejecutar, no se necesita CORS y no hace falta compilar el frontend
(sin npm).

### Flujo de datos

``` text
CSV INDECI ──> ETL Python ──> MongoDB Atlas (nube)
                                     │
                                     ▼
                     Spring Boot (arquitectura hexagonal)
                     ├── API REST  /api/...
                     └── static/   index.html + JS
                                     │
                                     ▼
                          Navegador del usuario
                  (Chart.js · Leaflet · tabla con filtros)
```

### Arquitectura hexagonal (backend)

Principio: **el dominio no conoce MongoDB ni HTTP**. Se comunica con el
exterior mediante puertos (interfaces) que implementan los adaptadores.

``` text
pe.alertaperu
├── domain/                        Java puro: sin Spring ni Mongo
│   ├── model/                     Emergencia, Ubicacion, Peligro,
│   │                              Indicadores, FiltroEmergencias,
│   │                              ResumenEstadistico, Pagina<T>
│   └── exception/                 EmergenciaNoEncontradaException, ...
├── application/
│   ├── port/in/                   ConsultarEmergenciasUseCase
│   │                              ObtenerEstadisticasUseCase
│   │                              ObtenerCatalogosUseCase
│   ├── port/out/                  EmergenciaRepositoryPort
│   │                              EstadisticasRepositoryPort
│   │                              CatalogoRepositoryPort
│   └── service/                   implementaciones de los casos de uso
└── infrastructure/
    ├── adapter/in/web/            @RestController, DTOs, validación
    ├── adapter/out/mongo/         @Document, MongoTemplate, mappers
    └── config/                    configuración de Mongo y manejo de errores
```

Decisiones:

1.  Las clases `@Document` viven en el adaptador de Mongo, no en el
    dominio. Un *mapper* traduce documento ↔ modelo de dominio.
2.  Las consultas con filtros opcionales y las agregaciones se hacen con
    `MongoTemplate` + `Criteria` / `Aggregation`, no con métodos
    derivados de Spring Data (`findByXAndY...`).
3.  Un solo módulo Maven organizado por paquetes, sin multi-módulo.
4.  Los casos de uso se prueban con implementaciones falsas de los
    puertos, sin Mongo. Los adaptadores se prueban aparte.

Ventaja: el dominio es independiente y fácil de probar. Desventaja: más
clases y traducciones que una arquitectura en capas; con 3 casos de uso
es manejable.

### Estructura del repositorio

``` text
ProyPaInteligenciaNegocios/
├── ALERTA_PERU_CONTEXTO_PROYECTO.md
├── DATASET_AUDIT.md
├── DECISION_MAPA_COROPLETICO.md
├── README.md
├── data/                      CSV y diccionario (fuera del repositorio si pesan)
├── etl/                       ver etl/README.md
│   ├── cargar.py              carga en Atlas (--dry-run para ensayar)
│   ├── verificar.py           compara Atlas con el CSV
│   ├── alerta_etl/            módulos del ETL
│   └── tests/
└── backend/
    ├── pom.xml
    └── src/main/
        ├── java/pe/alertaperu/...
        └── resources/
            ├── application.yml
            └── static/        index.html, css/, js/, geo/ (GeoJSON)
```

## 8. MongoDB Atlas Free (M0)

Límites oficiales consultados el 18/11/2026 en
https://www.mongodb.com/docs/atlas/reference/free-shared-limitations/

  ------------------------------------------------------------------------------
  Límite                    Valor                     Impacto en el proyecto
  ------------------------- ------------------------- --------------------------
  Almacenamiento            0,5 GB (documentos +      Se usarán \~210–230 MB
                            índices, sin comprimir)   (\~45 %)

  Operaciones               100 por segundo; si se    Carga por lotes con
                            superan, Atlas frena la   pausas y reintentos
                            conexión

  Conexiones                500                       Sin impacto

  Transferencia             10 GB de entrada y 10 GB  Sin impacto gracias a la
                            de salida cada 7 días     paginación

  Inactividad               se pausa tras 30 días sin Conectarse al menos una
                            conexiones                vez al mes

  Clústeres gratuitos       1 por proyecto            Suficiente

  Agregación                50 etapas; ordenamiento   Ordenar sobre índices o
                            en memoria ≤ 32 MB; sin   sobre resultados ya
                            `allowDiskUse`            filtrados y limitados

  JavaScript en el servidor no disponible             No se usa

  Versión                   MongoDB 8.0               ---
  ------------------------------------------------------------------------------

La documentación no indica si `$merge` y `$out` están disponibles en el
plan gratuito, así que el proyecto no depende de ellos.

### Configuración en la web de Atlas

1.  Crear un proyecto y un clúster **Free**.
2.  **Database Access:** crear un usuario para el ETL (lectura y
    escritura) y otro para el backend (solo lectura).
3.  **Network Access:** permitir solo las IP necesarias.
4.  Copiar la cadena de conexión en variables de entorno, **nunca en el
    código**.
5.  Usar **Data Explorer** para revisar la carga y **Metrics** para
    vigilar almacenamiento y operaciones.

## 9. Modelo de datos (aprobado el 18/11/2026)

### Colección `emergencias`: un documento por fila del CSV

``` json
{
  "_id": "351-2003-02-11-010101",
  "codigoSinpad": "351",
  "fecha": { "$date": "2003-02-11T00:00:00Z" },
  "anio": 2003,
  "mes": 2,
  "ubicacion": {
    "ubigeo": "010101",
    "codDepartamento": "01",
    "codProvincia": "0101",
    "departamento": "AMAZONAS",
    "provincia": "CHACHAPOYAS",
    "distrito": "CHACHAPOYAS",
    "regionNatural": "SIERRA"
  },
  "peligro": { "nombre": "LLUVIA INTENSA", "tipo": "ORIGEN NATURAL" },
  "personas": {
    "fallecidos": 0, "desaparecidos": 0, "lesionados": 0,
    "damnificados": 4, "afectados": 0
  },
  "vivienda": { "destruidas": 1, "afectadas": 0 },
  "educacion": { "centrosDestruidos": 0, "centrosAfectados": 0 },
  "salud": { "centrosDestruidos": 0, "centrosAfectados": 0 },
  "agricultura": { "hasDestruidas": 0, "hasAfectadas": 0 },
  "infraestructura": {
    "puentesDestruidos": 0, "puentesAfectados": 0,
    "carreteraDestruidaKm": 0, "carreteraAfectadaKm": 0,
    "caminoRuralDestruidoKm": 0, "caminoRuralAfectadoKm": 0,
    "canalColapsado": 0, "canalAfectado": 0
  },
  "ganaderia": {
    "vacunoPerdida": 0, "vacunoAfectado": 0,
    "camelidoPerdida": 0, "camelidoAfectado": 0,
    "caprinoPerdida": 0, "caprinoAfectado": 0,
    "porcinoPerdida": 0, "porcinoAfectado": 0,
    "menoresPerdida": 0, "menoresAfectado": 0
  },
  "ayuda": { "peso": 363.24, "costo": 345.4446 }
}
```

-   Los campos vacíos en el CSV no aparecen en el documento. En este
    ejemplo de 2003 faltan las columnas de aulas, que empiezan en 2019.
-   `ubicacion.departamento`, `provincia` y `distrito` conservan el
    nombre original de la fila.
-   `codDepartamento` y `codProvincia` se derivan del ubigeo para
    agrupar.
-   `ayuda` guarda los valores originales, sin convertir unidades.
-   Tamaño medido: **166,9 MB** en total, 1 174 bytes por documento.

### Colección `territorios`: catálogo para los filtros y el mapa

``` json
{
  "_id": "160601",
  "nivel": "distrito",
  "departamento": "LORETO",
  "provincia": "UCAYALI",
  "nombre": "CONTAMANA",
  "codDepartamento": "16",
  "codProvincia": "1606"
}
```

-   Un documento por departamento (26, como INDECI), provincia (196) y
    distrito (1 888).
-   Un nombre oficial por ubigeo, tomado de los límites del IDE-INEI 2023
    (ver `DATASET_AUDIT.md`, "Fuente territorial de referencia").
-   Ocupa menos de 1 MB.

### Fuente de límites para el mapa

[lincolneulogio/peru-geojson](https://github.com/lincolneulogio/peru-geojson)
(IDE-INEI 2023, licencia MIT), en su variante ligera: 25 departamentos,
196 provincias y 1 890 distritos, unos 2,2 MB en total. Cubre todos los
ubigeos del CSV salvo Alto Trujillo (`130112`, 11 registros). Copia
local en `data/referencias/peru-geojson-2023/`; en la Fase 5 se copia a
`backend/src/main/resources/static/geo/`.

### Colección `metadatos_carga`

Una entrada por ejecución del ETL: fecha, archivo, tamaño, hash SHA-256,
filas leídas, documentos insertados o actualizados, filas rechazadas y
duración.

### Índices

  ---------------------------------------------------------------------------------
  Índice                                     Filtro u operación que atiende
  ------------------------------------------ --------------------------------------
  `_id` (automático)                         detalle de un registro y *upsert*
                                             del ETL

  `{ fecha: -1 }`                            rango de años y orden por defecto de
                                             la tabla

  `{ "ubicacion.departamento": 1,            filtro de departamento (26 nombres
  fecha: -1 }`                               INDECI)

  `{ "ubicacion.ubigeo": 1, fecha: -1 }`     distrito (igualdad) y provincia
                                             (prefijo `^0101`, que también usa el
                                             índice)

  `{ "peligro.nombre": 1, fecha: -1 }`       filtro de peligro

  `{ codigoSinpad: 1 }`                      búsqueda por código SINPAD
  ---------------------------------------------------------------------------------

-   `regionNatural` y `peligro.tipo` no tienen índice propio: tienen
    pocos valores distintos (4 y 2), así que un índice no aportaría
    selectividad.
-   Estimación: \~25–40 MB en total. Se verificará con `explain()` y
    `db.stats()` después de la carga.

### Colección `agregados` (opcional): descartada tras medir

Solo se crearía si las agregaciones directas sobre `emergencias`
tardaran más de \~1 segundo con los filtros más usados. Mediciones del
18/11/2026 con el backend contra Atlas Free (São Paulo), con la
conexión ya abierta:

  Consulta                                              Tiempo
  ----------------------------------------------------- ------------
  Resumen KPI sin filtros (3 agregaciones, 142 139 docs) \~1,0 s
  Resumen KPI de Cusco 2020–2025                         0,31 s
  Resumen KPI de Puno 2023                               0,26 s
  Distribución por peligro / por departamento            0,33 s
  Listado paginado sin filtros                           0,30 s
  Detalle de un registro                                 0,09 s

**Decisión:** no se crea la colección. Solo la vista inicial sin
filtros llega a \~1 s y el resto queda muy por debajo, así que se evita
duplicar datos y mantener un proceso adicional.

## 10. API REST

Todos los endpoints de estadísticas y listados aceptan los mismos
**filtros comunes**:

`anioDesde`, `anioHasta`, `departamento`, `provincia` (ubigeo de 4
dígitos), `distrito` (ubigeo de 6 dígitos), `regionNatural`,
`tipoPeligro`, `peligro`.

  ------------------------------------------------------------------------------------------
  Método   Endpoint                                     Uso
  -------- -------------------------------------------- ------------------------------------
  GET      `/api/emergencias`                           Listado paginado (`page`, `size` ≤
                                                        100, `sort`)

  GET      `/api/emergencias/{id}`                      Detalle de un registro

  GET      `/api/estadisticas/resumen`                  KPIs

  GET      `/api/estadisticas/por-anio`                 Evolución anual

  GET      `/api/estadisticas/por-peligro`              Distribución por peligro

  GET      `/api/estadisticas/por-territorio`           Totales por `nivel`
                                                        (departamento/provincia/distrito);
                                                        lo usan el ranking y el mapa

  GET      `/api/catalogos/departamentos`               Lista de departamentos

  GET      `/api/catalogos/provincias?departamento=`    Provincias de un departamento

  GET      `/api/catalogos/distritos?provincia=`        Distritos de una provincia

  GET      `/api/catalogos/peligros`                    Peligros agrupados por tipo

  GET      `/api/catalogos/regiones`                    Regiones naturales
  ------------------------------------------------------------------------------------------

-   Se validan rangos, formatos de ubigeo y campos de orden permitidos.
-   El cliente nunca envía expresiones de consulta MongoDB.
-   Los errores devuelven JSON con código y mensaje claros.

## 11. Seguridad y configuración

-   La cadena de conexión va en variables de entorno, nunca en el código
    ni en el repositorio.
-   Los archivos `.env` y `application-local.yml` se excluyen con
    `.gitignore`. Se versiona un `.env.example` con valores ficticios.
-   El backend usa un usuario de Atlas de solo lectura.
-   El navegador solo habla con Spring Boot, nunca con Atlas.

``` env
MONGODB_URI=
MONGODB_DATABASE=alerta_peru
SERVER_PORT=8080
```

## 12. Despliegue

La presentación puede hacerse **en local** (Spring Boot en la PC y Atlas
en la nube). Publicarlo en internet es opcional; si se hace, el backend
puede ir en Render o Railway (verificando sus límites gratuitos).

## 13. Organización y calendario

**Por completar por el equipo:**

  Dato                     Valor
  ------------------------ ----------------------------
  Fecha de entrega final   *(pendiente)*
  Integrantes              *(pendiente)*
  Responsable de ETL       *(pendiente)*
  Responsable de backend   *(pendiente)*
  Responsable de frontend  *(pendiente)*
  Fecha de presentación    *(pendiente)*

## 14. Actualización del dataset

Si INDECI publica una versión nueva del CSV:

1.  Descargarla y repetir la auditoría.
2.  Revisar el mapeo del ETL si cambian las columnas.
3.  Ejecutar el ETL: el *upsert* por `_id` no duplica registros.
4.  Informar, sin borrar, los registros que hayan desaparecido.
5.  Registrar la carga en `metadatos_carga` y actualizar
    `DATASET_AUDIT.md`.

## 15. Plan de trabajo por fases

  --------------------------------------------------------------------------------
  Fase   Contenido                                 Estado
  ------ ----------------------------------------- -------------------------------
  1      Auditoría del CSV y del diccionario       **Completada**

  2      Modelo de datos, índices, estimación M0   **Completada** (modelo
         y fuente territorial                      aprobado)

  3      ETL en Python y carga en Atlas            **Completada**: 142 139
                                                   documentos, verificación OK

  4      Backend Spring Boot hexagonal             **Completada**: 34 pruebas OK
                                                   y endpoints probados contra
                                                   Atlas

  5      Frontend HTML/JS: filtros, KPIs,          **Completada** (ver
         gráficos, tabla y mapa                    DECISION_DISENO_FRONTEND.md)

  6      Pruebas, mediciones y documentación       **Completada**: cuadre CSV vs
                                                   API 15/15, README.md y
                                                   GUION_DEMO.md
  --------------------------------------------------------------------------------

### Fase 3: ETL

-   Aplicar las reglas obligatorias de la sección 3.
-   Generar el catálogo `territorios` con los nombres oficiales del INEI.
-   Carga por lotes con *upsert*, pausas por el límite de 100
    operaciones por segundo, reintentos y registro de filas rechazadas.
-   Crear los índices.
-   Verificar: 142 139 documentos y fechas coherentes con `AÑO` y `MES`.

### Fase 4: Backend

-   Proyecto Spring Boot con la estructura hexagonal de la sección 7.
-   Casos de uso, puertos, adaptadores web y Mongo.
-   Pruebas de los casos de uso (sin Mongo) y de los endpoints.

### Fase 5: Frontend

-   `index.html` con barra de filtros, secciones y estilos simples.
-   JavaScript separado por responsabilidad: `api.js`, `filtros.js`,
    `dashboard.js`, `tabla.js`, `mapa.js`.
-   Chart.js para los gráficos y Leaflet con GeoJSON para el mapa.

### Fase 6: Pruebas y documentación

-   Comparar al menos 5 totales de la aplicación con cálculos directos
    sobre el CSV.
-   Medir tiempos de consulta con y sin índices (`explain()`).
-   Medir el almacenamiento real en Atlas.
-   Completar `README.md` y preparar la demostración.

## 16. Criterios de aceptación

-   La colección `emergencias` contiene **142 139 documentos** y volver
    a ejecutar el ETL no cambia el conteo.
-   Todas las fechas coinciden con `AÑO` y `MES` del CSV, incluidas las
    16 401 de 2025.
-   Los vacíos del CSV no aparecen como ceros.
-   Los filtros por años, departamento, provincia, distrito, región
    natural y peligro funcionan, solos y combinados.
-   Al menos 5 totales de la aplicación coinciden con el cálculo directo
    sobre el CSV.
-   El dominio del backend no depende de Spring ni de MongoDB.
-   Las consultas principales usan índices (comprobado con `explain()`).
-   El almacenamiento usado en Atlas está medido y queda por debajo de
    0,5 GB.
-   La fuente está atribuida a INDECI y hay instrucciones de ejecución
    en `README.md`.

## 17. Instrucciones para Claude

Actúa como **arquitecto de software y desarrollador full-stack senior**
(MongoDB, Java/Spring Boot, Python, JavaScript e Inteligencia de
Negocios).

1.  Lee este documento antes de proponer cambios.
2.  Trabaja por fases y no avances sin verificar los entregables.
3.  No inventes columnas, datos, estadísticas ni resultados.
4.  Usa los datos de `DATASET_AUDIT.md`; si necesitas otro dato,
    compruébalo sobre el CSV.
5.  Mantén el alcance simple: lo que se evalúa es la integración Mongo +
    frontend con filtros correctos.
6.  Respeta la arquitectura hexagonal: nada de Spring ni Mongo en
    `domain/`.
7.  Prioriza la compatibilidad con Atlas Free y avisa antes de exceder
    sus límites.
8.  Propón código completo y ejecutable, con pruebas y comandos de
    verificación.
9.  No expongas secretos ni pidas que se peguen cadenas de conexión en
    el chat.
10. No afirmes que algo está terminado sin haberlo ejecutado.
11. Documenta las decisiones relevantes en archivos `.md` propios, para
    poder explicarlas al docente.
12. Mantén actualizados `README.md` y `DATASET_AUDIT.md`.

### Siguiente tarea

Las seis fases están completas. Pendiente opcional: decidir si la ayuda
humanitaria se muestra con la hipótesis de unidades (kg y soles) o se oculta;
exportar a CSV los registros filtrados.

------------------------------------------------------------------------

## 18. Referencias

-   Dataset INDECI (PNDA):
    https://www.datosabiertos.gob.pe/dataset/emergencias-y-daños-nivel-nacional-por-departamento
-   Límites de Atlas Free:
    https://www.mongodb.com/docs/atlas/reference/free-shared-limitations/
-   MongoDB: https://www.mongodb.com/docs/
-   Spring Boot: https://docs.spring.io/spring-boot/
-   Chart.js: https://www.chartjs.org/
-   Leaflet: https://leafletjs.com/

------------------------------------------------------------------------

**Fin del documento.**
