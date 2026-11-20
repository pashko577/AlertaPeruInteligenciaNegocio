# Guía de presentación: ALERTA PERÚ

Esta guía sirve para **explicar el proyecto**: el problema, los datos, MongoDB,
la arquitectura y cómo se conectan las piezas. La demostración en vivo, con
clics y enlaces, está en [GUION_DEMO.md](GUION_DEMO.md).

**Duración sugerida:** 15 a 20 minutos en total.

| Bloque | Tema | Tiempo |
|---|---|---|
| 1 | El problema | 2 min |
| 2 | La fuente: datos abiertos del INDECI | 2 min |
| 3 | Lo que encontramos al auditar los datos | 2 min |
| 4 | Arquitectura general | 1 min |
| 5 | ETL: del CSV a MongoDB Atlas | 2 min |
| 6 | MongoDB Atlas: colecciones, documentos e índices | 3 min |
| 7 | Backend: arquitectura hexagonal | 3 min |
| 8 | Cómo el backend consume MongoDB | 2 min |
| 9 | API REST y frontend | 1 min |
| 10 | Cómo sabemos que los datos son correctos | 1 min |
| 11 | Demo en vivo ([GUION_DEMO.md](GUION_DEMO.md)) | 8-10 min |
| 12 | Cierre y conclusiones | 1 min |

Cada bloque trae **qué mostrar**, **qué decir** y, si hace falta, **detalle por
si preguntan**.

---

## 1. El problema

**Qué decir:**

- El Perú registra miles de emergencias al año: lluvias, heladas, huaycos,
  incendios, sismos. El INDECI las registra todas en el **SINPAD** (Sistema de
  Información Nacional para la Respuesta y Rehabilitación).
- Esos registros se publican como un **CSV de 142 139 filas y 49 columnas**,
  de 2003 a 2025.
- Con el CSV solo no se pueden responder rápido preguntas como:
  - ¿Qué departamentos tienen más emergencias?
  - ¿Qué peligro afecta más a Puno? ¿Y a Lima?
  - ¿Cómo cambió la cantidad de emergencias año a año?
  - ¿Cuál fue la emergencia con más fallecidos?
- **Objetivo:** cargar los datos en una base de datos en la nube y construir
  una página con filtros que responda esas preguntas **con cifras correctas**.

**Frase clave:** *"Convertimos un CSV difícil de analizar en una herramienta de
consulta, sin perder ni alterar ningún dato."*

---

## 2. La fuente: datos abiertos del INDECI

**Qué mostrar:** la página del dataset en la Plataforma Nacional de Datos
Abiertos:
<https://www.datosabiertos.gob.pe/dataset/emergencias-y-daños-nivel-nacional-por-departamento>

**Qué decir:**

| Dato | Valor |
|---|---|
| Publicador | INDECI (Instituto Nacional de Defensa Civil) |
| Archivo | `BD_2003-2025_EMERGENCIAS.csv` (26 MB) |
| Diccionario | `Diccionario de datos_9.xlsx` |
| Filas | 142 139 emergencias |
| Columnas | 49 |
| Periodo | 2003 a 2025 |

Las 49 columnas se agrupan así:

| Grupo | Ejemplos de columnas |
|---|---|
| Identificación | código SINPAD, fecha, año, mes |
| Ubicación | ubigeo, departamento, provincia, distrito, región natural |
| Peligro | peligro (22 tipos), tipo (natural o inducido por el ser humano) |
| Personas | fallecidos, desaparecidos, lesionados, damnificados, afectados |
| Vivienda | destruidas, afectadas |
| Educación | centros educativos (hasta 2018), aulas (desde 2019) |
| Salud | centros de salud destruidos y afectados |
| Agricultura | hectáreas de cultivo destruidas y afectadas |
| Infraestructura | puentes, carreteras, caminos rurales, canales |
| Ganadería | vacunos, camélidos, caprinos, porcinos, animales menores |
| Ayuda humanitaria | peso y costo |

**Detalle por si preguntan:** ¿damnificado o afectado? Un **damnificado**
perdió su vivienda o sus medios de vida y necesita ayuda para recuperarse. Un
**afectado** sufrió alteraciones, pero puede seguir con sus actividades.

---

## 3. Lo que encontramos al auditar los datos

Antes de programar se auditó el CSV contra su diccionario. El detalle está en
[DATASET_AUDIT.md](DATASET_AUDIT.md).

**Qué decir:** *"Los datos abiertos no vienen limpios. Encontramos problemas
que, si no se tratan, darían cifras equivocadas."*

| Hallazgo | Riesgo si se ignora | Qué hicimos |
|---|---|---|
| **Dos formatos de fecha:** DD/MM/AAAA hasta 2024 y MM/DD/AA en 2025 | 16 401 emergencias de 2025 con fechas mal leídas (día y mes intercambiados) | El ETL detecta el formato por año y compara cada fecha con las columnas AÑO y MES |
| **Celdas vacías** | Convertirlas en 0 inventa datos ("0 fallecidos" no es lo mismo que "no se registró") | Un vacío no se guarda. La página muestra "sin dato" |
| **El código SINPAD se repite** (7 códigos) | Al usarlo como clave se pierden registros | El identificador combina código, fecha y ubigeo |
| **Educación cambia en 2019:** de centros educativos a aulas | Sumar series que miden cosas distintas | Se guardan por separado y se avisa en la ficha |
| **Lima tiene dos partes:** Lima Metropolitana y Lima Provincias | Totales de Lima incompletos o duplicados | Se respetan las dos, y el mapa las une en un polígono y lo indica |
| **Nombres de distritos con variantes** (8 ubigeos) | Un distrito aparece dos veces | Se usan los nombres oficiales del INEI |
| **Unidades de la ayuda dudosas:** el diccionario dice toneladas y miles de soles, pero las magnitudes sugieren kilos y soles | Cifras de ayuda engañosas | Se muestran con una advertencia |

---

## 4. Arquitectura general

**Qué mostrar:** este diagrama.

```text
 ┌──────────────────────────────┐
 │ BD_2003-2025_EMERGENCIAS.csv │  Datos abiertos del INDECI
 └──────────────┬───────────────┘
                │ 1. ETL en Python (se ejecuta una vez)
                │    limpia, valida y carga por lotes
                ▼
 ┌──────────────────────────────┐
 │ MongoDB Atlas (nube, M0)     │  Base alerta_peru
 │ emergencias · territorios ·  │
 │ metadatos_carga              │
 └──────────────┬───────────────┘
                │ 2. Driver de MongoDB (usuario de SOLO LECTURA)
                ▼
 ┌──────────────────────────────┐
 │ Backend Spring Boot          │  Arquitectura hexagonal
 │ API REST /api/...            │  Java 25
 └──────────────┬───────────────┘
                │ 3. HTTP + JSON
                ▼
 ┌──────────────────────────────┐
 │ Página web                   │  HTML + CSS + JavaScript
 │ filtros, gráficos, mapa      │  Chart.js, Leaflet
 └──────────────────────────────┘
```

**Qué decir:** son **tres programas** con responsabilidades separadas:

1. El **ETL** prepara y carga los datos.
2. **Atlas** los guarda en la nube.
3. El **backend** los consulta y los entrega a la **página**.

---

## 5. ETL: del CSV a MongoDB Atlas

ETL significa **Extract, Transform, Load**: extraer, transformar y cargar.

| Etapa | Qué hace en este proyecto | Archivo |
|---|---|---|
| **Extraer** | Lee el CSV en codificación Latin-1, con separador `;` | `etl/alerta_etl/transformacion.py` |
| **Transformar** | Convierte cada fila en un documento, valida fechas, no convierte vacíos en 0, normaliza nombres de territorios | `transformacion.py`, `territorios.py`, `columnas.py` |
| **Cargar** | Envía lotes de 500 documentos, como máximo 90 por segundo (Atlas Free admite 100 operaciones por segundo), con reintentos | `etl/alerta_etl/carga.py`, `etl/cargar.py` |

**Qué decir:**

- La carga tardó **27 minutos**, con **0 filas rechazadas**.
- Usa *upsert* (insertar o actualizar): si se ejecuta dos veces, no duplica
  datos.
- Cada carga queda registrada en la colección `metadatos_carga`: archivo, huella
  SHA-256, filas leídas y duración.
- Tiene **23 pruebas automáticas** de las reglas de transformación.

**Detalle por si preguntan:** ¿por qué Python para el ETL y Java para el
backend? Python es más práctico para leer y limpiar archivos, y el ETL se
ejecuta una sola vez. El backend corre todo el tiempo y el curso pidió Spring
Boot.

---

## 6. MongoDB Atlas: colecciones, documentos e índices

**Qué mostrar:** Atlas → **Browse Collections** → `alerta_peru`.

### Las tres colecciones

| Colección | Documentos | Para qué sirve |
|---|---|---|
| `emergencias` | 142 139 | Una emergencia por documento: los datos principales |
| `territorios` | 2 110 (26 departamentos, 196 provincias, 1 888 distritos) | Catálogo con nombres oficiales del INEI para los filtros en cascada y el mapa |
| `metadatos_carga` | 1 por ejecución del ETL | Registro de cada carga: cuándo, qué archivo, cuántas filas |

### Cómo es un documento

**Qué mostrar:** abrir el sismo de Pisco. En **Filter** escribir
`{ "_id": "22607-2007-08-15-110501" }`.

```json
{
  "_id": "22607-2007-08-15-110501",
  "codigoSinpad": "22607",
  "fecha": "2007-08-15",
  "anio": 2007, "mes": 8,
  "ubicacion": {
    "ubigeo": "110501", "departamento": "ICA", "provincia": "PISCO",
    "distrito": "PISCO", "regionNatural": "COSTA"
  },
  "peligro": { "nombre": "SISMO", "tipo": "ORIGEN NATURAL" },
  "personas":  { "fallecidos": 299, "lesionados": 100, "damnificados": 21739, "afectados": 19994 },
  "vivienda":  { "destruidas": 3814, "afectadas": 3508 },
  "educacion": { ... }, "salud": { ... }, "agricultura": { ... },
  "infraestructura": { ... }, "ganaderia": { ... }, "ayuda": { ... }
}
```

**Qué decir:**

- Las 49 columnas planas del CSV se organizan en **subdocumentos por tema**.
  Así es más fácil de leer y de consultar.
- **Por qué MongoDB y no SQL:**
  - Si una celda estaba vacía, el campo **no existe** en el documento. No hace
    falta llenar con NULL ni con 0.
  - Educación tiene campos distintos antes y después de 2019. En un documento
    no es problema; en SQL habría muchas columnas vacías.
  - Cada emergencia es una unidad completa que se lee de una sola vez.
- El `_id` combina código SINPAD, fecha y ubigeo, porque el código solo no es
  único.

### Los índices

| Índice | Campos | Para qué consulta |
|---|---|---|
| `idx_fecha` | `fecha` | Rango de años y orden por fecha |
| `idx_departamento_fecha` | `ubicacion.departamento`, `fecha` | Filtrar por departamento |
| `idx_ubigeo_fecha` | `ubicacion.ubigeo`, `fecha` | Filtrar por provincia o distrito |
| `idx_peligro_fecha` | `peligro.nombre`, `fecha` | Filtrar por peligro |
| `idx_codigo_sinpad` | `codigoSinpad` | Buscar por código |

**Qué decir:** sin índices, Mongo revisa los 142 139 documentos en cada
consulta. Con índices va directo a los que necesita. Se comprobó con
`explain()` que las consultas principales los usan. Las consultas con filtro
tardan entre 0,1 y 0,6 segundos.

### El plan gratuito (M0)

| Límite | Valor | Cómo lo manejamos |
|---|---|---|
| Almacenamiento | 512 MB | Usamos **184 MB (34 %)**, datos e índices incluidos |
| Operaciones | 100 por segundo | El ETL carga a 90 por segundo |
| Memoria para ordenar | 32 MB | Ordenar por cifras se limita a los primeros 10 000 registros |
| Inactividad | Se pausa a los 30 días | Entrar a Atlas antes de la presentación |

### Seguridad

- **Dos usuarios:** uno con permisos de escritura, solo para el ETL, y otro de
  **solo lectura** (`api_alerta`) para el backend. La página no puede borrar ni
  modificar datos.
- Las contraseñas están en archivos `.env`, que **no se suben a GitHub**.

---

## 7. Backend: arquitectura hexagonal

El documento completo está en
[DECISION_ARQUITECTURA_HEXAGONAL.md](DECISION_ARQUITECTURA_HEXAGONAL.md).

### La idea

**Qué decir:** *"En el centro van las reglas del negocio. Alrededor va la
tecnología. El centro no conoce la tecnología: define interfaces llamadas
**puertos**, y la tecnología se conecta a ellas mediante **adaptadores**."*

```text
                ┌─────────────── infrastructure ───────────────┐
                │                                               │
 Página ──HTTP──▶ adapter/in/web              adapter/out/mongo ──▶ Atlas
                │  (controladores)            (MongoTemplate)  │
                │        │                          ▲          │
                │        ▼                          │          │
                │   ┌──────────── application ───────────┐    │
                │   │ port/in ──▶ service ──▶ port/out   │    │
                │   │        ┌──── domain ────┐          │    │
                │   │        │ reglas puras   │          │    │
                │   │        └────────────────┘          │    │
                │   └────────────────────────────────────┘    │
                └───────────────────────────────────────────────┘
          Las dependencias apuntan siempre hacia el centro.
```

### Para qué sirve cada capa

**Qué mostrar:** en el editor, la carpeta `backend/src/main/java/pe/alertaperu/`.

| Capa y paquete | Para qué sirve | Clases de ejemplo |
|---|---|---|
| **`domain/model`** | Los conceptos del negocio y sus reglas, en Java puro | `Emergencia`, `Ubicacion`, `Peligro` ("zOTROS" → "OTROS"), `Indicadores` (vacío ≠ 0), `FiltroEmergencias` (año desde ≤ año hasta, ubigeo de 6 dígitos), `Paginacion` (máximo 100 por página) |
| **`domain/exception`** | Errores del negocio | `FiltroInvalidoException` (→ 400), `RecursoNoEncontradoException` (→ 404) |
| **`application/port/in`** | **Qué ofrece** el sistema: los casos de uso, como interfaces | `ConsultarEmergenciasUseCase`, `ObtenerEstadisticasUseCase`, `ObtenerCatalogosUseCase`, `ObtenerInfoCargaUseCase` |
| **`application/port/out`** | **Qué necesita** el sistema de afuera, como interfaces | `EmergenciaRepositoryPort`, `EstadisticasRepositoryPort`, `CatalogoRepositoryPort`, `InfoCargaRepositoryPort` |
| **`application/service`** | La lógica de los casos de uso | `EstadisticasService` (KPIs, variación interanual, peligro más frecuente), `ConsultarEmergenciasService` |
| **`infrastructure/adapter/in/web`** | **Adaptador de entrada:** recibe HTTP y devuelve JSON | `EmergenciaController`, `EstadisticasController`, `CatalogoController`, `ManejadorErrores`, DTOs |
| **`infrastructure/adapter/out/mongo`** | **Adaptador de salida:** habla con MongoDB | `MongoEmergenciaAdapter`, `MongoEstadisticasAdapter`, `CriteriosMongo`, `MongoMapper` |
| **`infrastructure/config`** | Conecta todo: el único lugar donde se crean los servicios | `CasosDeUsoConfig` |

### Por qué la usamos

1. **Las reglas de los datos son lo más importante** en un proyecto de BI, y
   quedan separadas de la tecnología.
2. **Se prueba sin base de datos:** las 34 pruebas usan repositorios falsos en
   memoria (`Fakes.java`) y corren en segundos sin conectarse a Atlas.
3. **La base de datos se puede cambiar:** para usar PostgreSQL, solo se
   escribe otro adaptador en `adapter/out/`.
4. **Está garantizado:** `ArquitecturaHexagonalTest` falla si `domain` o
   `application` importan Spring o MongoDB.

**Detalle por si preguntan:** ¿por qué los servicios no tienen `@Service`?
Porque `@Service` es de Spring, y la capa de aplicación no debe depender de
Spring. Se registran a mano en `CasosDeUsoConfig`.

---

## 8. Cómo el backend consume MongoDB

**Qué decir primero:** *"MongoDB no se consume como una API REST. El backend
usa el **driver oficial de MongoDB para Java**, que habla el protocolo propio de
MongoDB por una conexión cifrada con TLS."*

```text
Página ──HTTP/JSON──▶ Spring Boot ──protocolo MongoDB (BSON) + TLS──▶ Atlas
```

### Al arrancar el backend

| Paso | Dónde | Qué pasa |
|---|---|---|
| 1 | `pom.xml` | `spring-boot-starter-data-mongodb` trae el driver y Spring Data MongoDB |
| 2 | `application.yml` + `.env` | `spring.mongodb.uri: ${MONGODB_URI}`: la URI con el usuario de solo lectura |
| 3 | Autoconfiguración de Spring Boot | Crea un `MongoClient`, con un pool de conexiones al clúster |
| 4 | Autoconfiguración | Crea un `MongoTemplate` apuntando a `alerta_peru` |
| 5 | Inyección de dependencias | Entrega ese `MongoTemplate` al constructor de cada adaptador Mongo |

Con la URI `mongodb+srv://...`, el driver:

- pregunta por DNS cuáles son los **3 nodos** del clúster;
- abre conexiones cifradas;
- se autentica con usuario y contraseña;
- detecta cuál nodo es el primario.

### En cada consulta

**Qué mostrar:** `MongoEmergenciaAdapter.java`.

```java
Query consulta = new Query(CriteriosMongo.desde(filtro))   // { "ubicacion.departamento": "PUNO" }
        .with(orden(paginacion))                            // { "personas.damnificados": -1 }
        .skip(paginacion.desplazamiento())
        .limit(paginacion.tamano());

mongo.find(consulta, EmergenciaDocument.class)              // ← aquí va a Atlas
     .stream().map(MongoMapper::aDominio).toList();          // documento → objeto del dominio
```

Las estadísticas usan el **pipeline de agregación** de MongoDB
(`MongoEstadisticasAdapter`): `$match` filtra y `$group` suma. Atlas devuelve
los totales ya calculados, así que no hace falta traer los 142 139 documentos
al backend.

### Recorrido completo de una consulta

```text
1. Página        fetch('/api/emergencias?departamento=PUNO&sort=damnificados&dir=desc')
2. Controller    EmergenciaController: parámetros → FiltroEmergencias + Paginacion
3. Caso de uso   ConsultarEmergenciasService (no sabe que existe Mongo)
4. Puerto        EmergenciaRepositoryPort (interfaz)
5. Adaptador     MongoEmergenciaAdapter → MongoTemplate.find(...)
6. Atlas         usa el índice idx_departamento_fecha y devuelve documentos BSON
7. Vuelta        MongoMapper → Emergencia → WebMapper → JSON → tabla en la página
```

---

## 9. API REST y frontend

### API REST

| Ruta | Qué devuelve | Dónde lo usa la página |
|---|---|---|
| `/api/estadisticas/resumen` | Totales y KPIs | Cifra grande y tarjetas |
| `/api/estadisticas/por-anio` | Serie anual | Gráfico "Por año" |
| `/api/estadisticas/por-peligro` | Totales por peligro | Gráfico "Por peligro" |
| `/api/estadisticas/por-territorio` | Totales por departamento, provincia o distrito | Ranking y mapa |
| `/api/emergencias` | Registros paginados y ordenables | Pestaña Registros |
| `/api/emergencias/{id}` | Una emergencia completa | Ficha de detalle |
| `/api/catalogos/...` | Departamentos, provincias, distritos, peligros, regiones | Filtros en cascada |

Todas aceptan los mismos filtros: años, departamento, provincia, distrito,
región natural, tipo de peligro y peligro.

**Qué mostrar:** abrir en el navegador
<http://localhost:8080/api/estadisticas/resumen?departamento=CUSCO> para que se
vea el JSON.

### Frontend

| Característica | Por qué |
|---|---|
| **La consulta es una frase:** "Mostrar damnificados en Puno entre 2003 y 2025…" | Cada parte subrayada es un filtro. Se lee como una pregunta |
| **Mapa coroplético** (territorios pintados según su total) | El CSV no trae coordenadas, solo ubigeo. Ver [DECISION_MAPA_COROPLETICO.md](DECISION_MAPA_COROPLETICO.md) |
| **"Ver como tabla"** en cada gráfico | Accesibilidad y valores exactos |
| **Colores aptos para daltonismo** (azul natural, naranja humano) | Validados con un script |
| **Tema claro y oscuro** | Se adapta a la pantalla o al proyector |
| **La URL guarda la consulta** | Se puede compartir un enlace con los filtros puestos |

Hecho con HTML, CSS y JavaScript sin frameworks, más Bootstrap, Chart.js y
Leaflet. Spring Boot sirve la página desde `static/`.

---

## 10. Cómo sabemos que los datos son correctos

**Qué decir:** *"No basta con que la página muestre números: hay que demostrar
que son los correctos."*

| Nivel | Prueba | Resultado |
|---|---|---|
| Reglas de limpieza | 23 pruebas del ETL | OK |
| CSV → Atlas | `verificar.py`: conteos, sumas, vacíos, índices, espacio usado | OK |
| **CSV → lo que muestra la página** | `cuadrar_api.py`: recalcula desde el CSV, sin usar el código del ETL, y compara con la API | **15 de 15** |
| Backend | 34 pruebas, incluida la de arquitectura | OK |

**Qué mostrar:** la terminal con `python etl/cuadrar_api.py` ya ejecutado.

---

## 11. Demo en vivo

Seguir [GUION_DEMO.md](GUION_DEMO.md). Incluye:

- la checklist previa;
- los casos de Piura 2017, Puno con bajas temperaturas y Lima Metropolitana;
- el mapa por niveles;
- la tabla ordenada por fallecidos;
- las consultas en vivo en Atlas.

---

## 12. Cierre y conclusiones

**Qué decir:**

1. **Integración completa:** datos abiertos → ETL → MongoDB Atlas → API
   hexagonal → página con filtros por territorio, años y peligro.
2. **Calidad de datos:** se auditó la fuente y se corrigieron 7 problemas.
   Ninguna cifra se inventa: vacío es "sin dato".
3. **Verificable:** los totales de la página cuadran con el CSV original
   (15 de 15).
4. **Arquitectura mantenible:** el negocio está separado de la tecnología y se
   prueba sin base de datos.
5. **Hallazgos del análisis:**
   - la lluvia intensa es el peligro más frecuente (42 824 emergencias);
   - Apurímac es el departamento con más registros (14 321);
   - en Lima Metropolitana predominan los incendios urbanos;
   - las emergencias registradas crecieron mucho desde 2023, aunque puede
     deberse a más eventos o también a más registro.

**Limitaciones (decirlas suma puntos):**

- El CSV no trae coordenadas, así que el máximo detalle del mapa es el distrito.
- No se pudieron confirmar las unidades de la ayuda humanitaria.
- Es una herramienta de análisis histórico, no un sistema de alertas.

**Trabajo futuro:** exportar los resultados filtrados a CSV, actualizar
automáticamente cuando el INDECI publique una nueva versión y desplegar en la
nube (el `Dockerfile` ya está listo).

---

## Preguntas difíciles

| Pregunta | Respuesta |
|---|---|
| ¿Por qué no usaron SQL? | Los documentos permiten campos que existen solo en algunos periodos (educación) y omitir los vacíos sin llenarlos con NULL. Cada emergencia se lee completa en una sola operación. |
| ¿Qué pasa si el INDECI publica un CSV nuevo? | Se ejecuta de nuevo el ETL. Gracias al *upsert* no duplica datos, y `metadatos_carga` registra la nueva carga con su huella del archivo. |
| ¿Cómo evitan que alguien borre los datos desde la página? | El backend usa un usuario de Atlas de solo lectura. Aunque hubiera un error en el código, Atlas no permitiría escribir. |
| ¿Por qué el ETL carga a 90 por segundo y no más rápido? | Atlas Free admite 100 operaciones por segundo. Ir más rápido provoca errores y la carga se frena. |
| ¿Qué es un puerto y qué es un adaptador? | El puerto es una interfaz que define el centro, por ejemplo `EmergenciaRepositoryPort`. El adaptador es la clase que lo implementa con una tecnología concreta, por ejemplo `MongoEmergenciaAdapter`. |
| ¿Dónde está la conexión a MongoDB? | La URI está en `application.yml`, que la lee del `.env`. Spring Boot crea el `MongoTemplate`, y solo el paquete `adapter/out/mongo` lo usa. |
| ¿Cómo prueban sin base de datos? | Los servicios reciben interfaces. En las pruebas se les pasa un `Fake` en memoria, en lugar del adaptador Mongo. |
| ¿Por qué Lima aparece dos veces en el ranking? | El INDECI separa Lima Metropolitana (provincia 1501) y Lima Provincias. Se respeta la fuente. |
| ¿Por qué hay "desde 2019" en educación? | El INDECI cambió de contar centros educativos a contar aulas en 2019. Los registros anteriores no tienen esos campos. |
| ¿Cuánto espacio usan en Atlas? | 184 MB de 512 MB (34 %), incluidos los índices. |
