# Guion de la demostración: ALERTA PERÚ

Duración: unos **10 minutos**. Es el bloque 11 de [GUIA_PRESENTACION.md](GUIA_PRESENTACION.md), que explica el proyecto antes de la demo. Todas las cifras de este guion salen de la
aplicación con los datos cargados en Atlas y cuadran con el CSV original
(`python etl/cuadrar_api.py`).

Cada paso trae un enlace directo como respaldo. Si un selector tarda o te
equivocas, abre el enlace y sigues desde ahí.

## Antes de empezar (5 minutos antes)

1. En Atlas, confirma que **Cluster0** está activo. Se pausa tras 30 días sin uso.
2. Levanta el backend: `cd backend` y luego `mvnw.cmd spring-boot:run`.
3. Abre <http://localhost:8080> y espera a que cargue la cifra **142,139**.
   Así la primera consulta de la exposición no será la lenta.
4. Elige el tema **Claro** (botón arriba a la derecha). En un proyector se lee mejor.
5. Deja abierta, en otra pestaña, la página de **Data Explorer** de Atlas con
   la colección `alerta_peru.emergencias`.

## 1. El problema y los datos (1 minuto)

**Mostrar:** la página inicial, sin filtros.

**Decir:**

- El INDECI publica cada emergencia registrada en el SINPAD: 142 139 filas,
  de 2003 a 2025, con 49 columnas de daños.
- Con el CSV solo no se puede responder rápido dónde, cuándo y qué peligro.
  Por eso los datos se cargaron en MongoDB Atlas y se consultan con filtros.
- La frase de arriba **es** el filtro: cada parte subrayada se puede cambiar.

**Señalar:** el peligro más frecuente es la **lluvia intensa** (42 824
emergencias). En promedio, cada emergencia dejó **17,3 damnificados**.

## 2. MongoDB Atlas por dentro (2 minutos)

**Mostrar:** en Atlas, **Browse Collections** → `alerta_peru` → `emergencias`.
Abrir un documento.

**Decir:**

- Cada fila del CSV es un documento. Los daños están agrupados por tema:
  personas, vivienda, educación, salud, agricultura, infraestructura, ganadería
  y ayuda.
- Si una celda estaba vacía en el CSV, el campo **no se guarda**. Así "sin
  dato" no se confunde con 0.
- Usa 184 MB de los 512 MB del plan gratuito. Los filtros usan índices,
  comprobado con `explain()`.
- El backend se conecta con un usuario de **solo lectura**.

**Hacer: una consulta en vivo y comparar con la página.** Se puede hacer de
dos formas.

**Opción A: en la web, sin instalar nada.** En la barra **Filter** escribir lo
siguiente y pulsar **Apply**:

```js
{ "ubicacion.departamento": "PUNO", "peligro.nombre": "BAJAS TEMPERATURAS" }
```

Atlas muestra **1824** documentos, la misma cifra que la página para Puno con
bajas temperaturas. Para ordenar, pulsar **Options** y escribir en **Sort**
`{ "personas.fallecidos": -1 }`.

**Opción B: en la terminal con `mongosh`.** Requiere instalarlo antes con
`winget install MongoDB.Shell`. El comando de conexión está en Atlas, en
**Connect → Shell**. Usar el usuario `api_alerta`, que es de solo lectura. La
contraseña se escribe cuando la pide, no en el comando.

```js
use alerta_peru

db.emergencias.countDocuments()
// 142139

// Emergencias por departamento, de mayor a menor
db.emergencias.aggregate([
  { $group: { _id: "$ubicacion.departamento", total: { $sum: 1 } } },
  { $sort: { total: -1 } },
  { $limit: 3 }
])
// APURIMAC 14321, HUANCAVELICA 11981, CUSCO 11398

// Piura en el Niño Costero de 2017
db.emergencias.aggregate([
  { $match: { "ubicacion.departamento": "PIURA", anio: 2017 } },
  { $group: { _id: null, emergencias: { $sum: 1 }, damnificados: { $sum: "$personas.damnificados" } } }
])
// 461 emergencias, 103411 damnificados

// Las 3 emergencias con más fallecidos
db.emergencias.find({}, { fecha: 1, "ubicacion.distrito": 1, "peligro.nombre": 1, "personas.fallecidos": 1 })
  .sort({ "personas.fallecidos": -1 }).limit(3)
// Pisco 2007 (299), Ica 2007 (73), Santa Teresa 2013 (51)
```

**Decir:** son las mismas cifras que muestra la página, así que la página
consulta los datos reales de Atlas.

Todos los resultados de los comentarios se comprobaron contra Atlas. En la base
el peligro "otros" se guarda como `zOTROS`, para que quede al final de la lista
ordenada. La página lo muestra como "Otros".

## 3. Panorama: cuándo y qué (1,5 minutos)

**Mostrar:** el gráfico **Por año**.

**Decir:**

- Los picos de damnificados coinciden con eventos conocidos:
  - **2007:** 491 701 damnificados (año del sismo de Pisco);
  - **2017:** 7 580 emergencias y 299 882 damnificados (Niño Costero).
- Desde 2023 se registran muchas más emergencias, con 16 401 en 2025.
  **Cuidado al interpretarlo:** puede reflejar más eventos o también más
  registro. Los datos no permiten separar las dos causas.

**Hacer:** pulsar **Ver como tabla**. Cada gráfico tiene su tabla, por
accesibilidad y para leer el valor exacto.

## 4. Filtros combinados: dónde (2 minutos)

**Caso A: Piura en 2017.** Cambiar la frase a *Mostrar damnificados en Piura,
entre 2017 y 2017*.

- Resultado: 461 emergencias y **103 411 damnificados**, unos 224 por
  emergencia (el promedio nacional es 17).
- Enlace de respaldo:
  <http://localhost:8080/?medida=damnificados&departamento=PIURA&anioDesde=2017&anioHasta=2017>

**Caso B: Puno y el frío.** *Mostrar afectados en Puno, por bajas temperaturas.*

- Resultado: 1 824 emergencias y **2 075 097 afectados**.
- Enlace: <http://localhost:8080/?medida=afectados&departamento=PUNO&peligro=BAJAS%20TEMPERATURAS>

**Caso C: Lima Metropolitana.** El peligro más frecuente ya no es natural: son
los **incendios urbanos e industriales** (4 743 de 5 900 emergencias).

- Enlace: <http://localhost:8080/?departamento=LIMA%20METROPOLITANA>

**Decir:** la URL guarda la consulta, así que se puede compartir.

## 5. Mapa: bajar de nivel (1 minuto)

**Mostrar:** la pestaña **Mapa**, sin filtros.

**Decir:**

- Es un mapa coroplético: pinta cada territorio según su total, en una sola
  gama de azules. Más oscuro significa más.
- Se usa este tipo de mapa porque el CSV no trae coordenadas, solo el ubigeo
  del distrito. El detalle está en `DECISION_MAPA_COROPLETICO.md`.
- **Apurímac** es el departamento con más emergencias registradas (14 321).

**Hacer:** clic en un departamento, luego en una provincia. El mapa baja a
provincias y distritos, y la frase de arriba se actualiza sola.

## 6. Registros: el caso concreto (1 minuto)

**Hacer:** quitar los filtros, ir a **Registros** y elegir *Ordenar por
fallecidos, de mayor a menor*.

- El primero es el **sismo del 15 de agosto de 2007 en Pisco (Ica)**: 299
  fallecidos y 21 739 damnificados.

**Hacer:** abrir la fila. La ficha muestra los 49 campos.

**Decir:**

- "Sin dato" significa que la celda venía vacía.
- En educación, los registros anteriores a 2019 dicen "desde 2019" en las
  aulas, porque el INDECI cambió de contar centros a contar aulas.

## 7. Cómo sé que las cifras son correctas (30 segundos)

**Mostrar:** la terminal con `python etl/cuadrar_api.py` ya ejecutado.

**Decir:**

- El script recalcula los totales leyendo el CSV directamente, sin usar el
  código del ETL, y los compara con lo que entrega la API a la página.
- **15 de 15 comprobaciones cuadran:** totales nacionales, los 23 años, los
  22 peligros, los 26 departamentos y consultas con filtros combinados.

## 8. Arquitectura hexagonal (1 minuto)

**Mostrar:** en el editor, la carpeta `backend/src/main/java/pe/alertaperu/`
con sus tres paquetes, y el diagrama de `DECISION_ARQUITECTURA_HEXAGONAL.md`.

**Decir:**

- **`domain`** tiene las reglas de los datos en Java puro. Por ejemplo, "vacío
  no es cero" o "el año desde no puede ser mayor que el año hasta".
- **`application`** tiene los casos de uso y los **puertos**, que son
  interfaces: qué ofrece el sistema y qué necesita de afuera.
- **`infrastructure`** tiene los **adaptadores**: los controladores REST por
  un lado y MongoDB por el otro.
- Las dependencias apuntan hacia el centro: el dominio no sabe que existen
  Spring ni MongoDB.
- Para cambiar MongoDB por otra base, solo se escribe otro adaptador.
- La regla no es solo una promesa: `ArquitecturaHexagonalTest` falla si
  alguien la rompe. Además, las 34 pruebas corren sin conectarse a Atlas.

**Si piden un ejemplo:** recorrer `EmergenciaController` →
`ConsultarEmergenciasService` → `EmergenciaRepositoryPort` →
`MongoEmergenciaAdapter`. El recorrido está en la sección 4 del documento.

## Preguntas probables

| Pregunta | Respuesta corta |
|---|---|
| ¿Por qué MongoDB y no SQL? | Cada emergencia es un documento con grupos de daños que cambian según el periodo (educación desde 2019). Los campos vacíos simplemente no se guardan. |
| ¿Por qué no hay puntos en el mapa? | El CSV no trae coordenadas, solo el ubigeo del distrito. Inventar puntos daría una precisión falsa. |
| ¿Por qué Lima aparece como dos departamentos? | El INDECI separa Lima Metropolitana (provincia 1501) y Lima Provincias. El mapa las une en un polígono y lo indica. |
| ¿Las cifras de ayuda están en toneladas? | El diccionario dice toneladas y miles de soles, pero las magnitudes sugieren kilos y soles. Por eso se muestran con advertencia. |
| ¿Por qué hexagonal y no un CRUD normal? | Las reglas de los datos son lo valioso y se prueban sin base de datos. Además, la base se puede cambiar tocando solo un adaptador. Ver `DECISION_ARQUITECTURA_HEXAGONAL.md`. |
| ¿Dónde se conecta todo? | En `CasosDeUsoConfig`, el único lugar donde Spring crea los servicios y les pasa los adaptadores Mongo. |
| ¿Cuánto tarda una consulta? | Entre 0,1 y 0,6 s con filtros, y alrededor de 1 s sin filtros, en el plan gratuito. |
