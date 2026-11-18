# Auditoría del dataset — ALERTA PERÚ

**Fecha de la auditoría:** 18 de noviembre de 2026
**Estado:** completa, incluida la estimación de almacenamiento en Atlas M0

Todas las cifras de este documento se obtuvieron ejecutando consultas
directamente sobre los archivos indicados. Ninguna es estimada.

---

## 1. Archivos auditados

| Archivo | Tamaño | Fuente |
|---|---|---|
| `BD_2003-2025_EMERGENCIAS.csv` | 27 040 446 bytes | https://www.datosabiertos.gob.pe/sites/default/files/BD_2003-2025_EMERGENCIAS.csv |
| `Diccionario de datos_9.xlsx` | 13 752 bytes | Página del dataset en la PNDA |

Página del dataset:
https://www.datosabiertos.gob.pe/dataset/emergencias-y-daños-nivel-nacional-por-departamento

El diccionario identifica el recurso como *"Emergencias y daños a nivel
nacional por departamento"* y describe las 49 variables del CSV.

---

## 2. Estructura

| Característica | Valor |
|---|---|
| Filas de datos | **142 139** |
| Columnas | **49**; todas las filas tienen exactamente 49 campos |
| Codificación | ISO-8859-1 (Latin-1) |
| Separador | `;` |
| Fin de línea | CRLF |
| Periodo | 2003 a 2025 |
| Filas exactamente duplicadas | **0** |

### Filas por año

| Año | Filas | Año | Filas | Año | Filas |
|---|---|---|---|---|---|
| 2003 | 3 306 | 2011 | 4 865 | 2019 | 4 492 |
| 2004 | 4 045 | 2012 | 5 140 | 2020 | 5 210 |
| 2005 | 4 765 | 2013 | 4 389 | 2021 | 7 799 |
| 2006 | 4 497 | 2014 | 3 775 | 2022 | 7 251 |
| 2007 | 4 539 | 2015 | 4 317 | 2023 | 12 388 |
| 2008 | 4 543 | 2016 | 5 144 | 2024 | 13 667 |
| 2009 | 4 035 | 2017 | 7 580 | 2025 | 16 401 |
| 2010 | 4 502 | 2018 | 5 489 | | |

### Diferencias de nombre entre el diccionario y el CSV

El ETL debe usar los nombres exactos del CSV:

| Diccionario | CSV (literal) |
|---|---|
| `CODIGO DE EMERGENCIA SINPAD` | `CODIGO DE EMERGENCIA-SINPAD` |
| `FECHA DE LA EMERGENCIA` | `FECHA  DE LA EMER` (doble espacio) |
| `CARRETERA DESTRUIDA KM` | `CARRETERA DESTRUIDA KM ` (espacio final) |
| `CAMINO RURAL DESTRUIDO` | `CAMINO RURAL DESTRUIDO KM` |
| `CAMINO RURAL AFECTADO` | `CAMINO RURAL AFECTADO KM` |

---

## 3. Hallazgos críticos

### 3.1 Dos formatos de fecha según el año ⚠️

El diccionario indica *"Formato: DD/MM/AAAA o MM/DD/AA"*, pero no dice
qué filas usan cada uno. Al comprobarlo:

| Años | Filas | Formato | Ejemplo |
|---|---|---|---|
| 2003–2024 | 125 738 | `DD/MM/AAAA` | `11/02/2003` = 11 de febrero |
| 2025 | 16 401 | `MM/DD/AA` | `02/15/25` = 15 de febrero (columna `MES` = `FEBRERO`) |

Verificación: con esta regla, **las 142 139 fechas coinciden con las
columnas `AÑO` y `MES`**, sin ninguna excepción.

**Regla del ETL:** el formato se decide por la longitud del año: 4
dígitos → `DD/MM/AAAA`; 2 dígitos → `MM/DD/AA` (año 20AA). Después de
convertir, se valida que coincida con `AÑO` y `MES`, y la fila se
rechaza si no coincide. Si se aplicara un solo formato a todo el
archivo, unas 15 800 fechas de 2025 quedarían con el día y el mes
intercambiados sin producir ningún error.

### 3.2 El código SINPAD no es único

El diccionario lo describe como *"Código único identificador… Clave
primaria transaccional"*. En el CSV hay 142 132 códigos distintos: **7
aparecen dos veces**, en eventos claramente distintos:

| Código | Registro 1 | Registro 2 |
|---|---|---|
| 97549 | 22/12/2018 · Huancavelica / La Merced · Sequía | 04/01/2019 · Lambayeque / La Victoria · Incendio urb. e indust. |
| 97553 | 23/12/2018 · Huancavelica / Pilchaca · Sequía | 14/01/2019 · Lima Metropolitana / Lima · Incendio urb. e indust. |
| 97558 | 22/12/2018 · Huancavelica / Conayca · Sequía | 13/01/2019 · Lima Metropolitana / Rímac · Incendio urb. e indust. |
| 97561 | 23/12/2018 · Huancavelica / Pilpichaca · Sequía | 07/01/2019 · Pasco / Villa Rica · Lluvia intensa |
| 153378 | 09/05/2022 · Ayacucho / Chuschi · Vientos fuertes | 20/08/2023 · Ayacucho / Chuschi · Vientos fuertes |
| 154908 | 03/07/2022 · Huánuco / San Francisco de Cayrán · Bajas temperaturas | 12/06/2023 · Huánuco / San Francisco de Cayrán · Bajas temperaturas |
| 155509 | 22/07/2022 · Cusco / Condoroma · Bajas temperaturas | 16/07/2023 · Cusco / Condoroma · Bajas temperaturas |

**Decisión:** no son duplicados y se cargan todos. La combinación
**código + fecha + ubigeo es única en las 142 139 filas** y será el
`_id` del documento.

### 3.3 Las unidades de la ayuda no son coherentes con el diccionario ⚠️

| Columna | Unidad según el diccionario | Mediana (valores > 0) | Percentil 90 | Máximo |
|---|---|---|---|---|
| `PESO DE LA AYUDA` | toneladas métricas | 136,16 | 1 529,5 | 112 093 964,2 |
| `COSTO DE LA AYUDA` | miles de soles | 981,12 | 9 469,9 | 47 933 190,46 |

Si se aplicaran las unidades del diccionario:

- Una emergencia típica habría recibido **136 toneladas** de ayuda. La
  primera fila del CSV (4 damnificados) registra 363 "toneladas".
- El máximo de peso equivaldría a **112 millones de toneladas** para un
  solo evento (contaminación, Lambayeque, 2018, 0 damnificados).
- El máximo de costo equivaldría a **unos 48 000 millones de soles** para una
  inundación en Pasco (2019) sin damnificados.

Las magnitudes son mucho más coherentes con **kilogramos** y **soles**,
pero eso es una hipótesis y no está confirmado por la fuente.

**Decisión:** las columnas se cargan tal cual, con sus valores
originales. En el dashboard **no se muestran como KPI ni con unidad**
hasta confirmar las unidades con INDECI. Si se muestran, será con la
advertencia "unidad no verificada".

Además hay **1 valor negativo** en `COSTO DE LA AYUDA` (código 5420,
20/04/2004, Rodríguez de Mendoza, −265,2536). Se conserva en la base y
se excluye de las sumas, y queda registrado como anomalía.

### 3.4 Cambio de indicadores educativos en 2019 ⚠️

Los vacíos de las columnas educativas siguen un patrón por año, no son
aleatorios:

| Columnas | 2003–2018 | 2019–2024 | 2025 |
|---|---|---|---|
| `CENTROS EDUCATIVOS DESTRUIDOS/AFECTADOS` | con dato | **vacías** | **vacías** |
| `SCANT_CCEE`, `SAFEC_AULA`, `SDESTRU_AULA`, `SINHAB_AULA` | **vacías** | con dato | con dato |
| `AULA_DES+INHA` | **vacía** | con dato | **vacía** |

Es decir, en 2019 el sistema **cambió de contar centros educativos a
contar aulas**.

**Consecuencias:**

- No existe una serie educativa continua de 2003 a 2025. Los gráficos
  educativos se separan en dos periodos y no se comparan entre sí.
- Rellenar estos vacíos con 0 sería falso, porque afirmaría que no hubo
  daños educativos en 16 años. Se aplica la regla de vacío frente a cero.
- `AULA_DES+INHA` cumple `= SDESTRU_AULA + SINHAB_AULA` en **las 50 807
  filas** donde están las tres columnas. En 2025 está vacía, pero no se
  rellenará en el dataset: si se necesita, se calcula en la consulta y
  se marca como derivada.

### 3.5 Unidades ambiguas en caminos rurales y canales

- **Caminos rurales:** el diccionario dice *"Longitud en Km o metros"*,
  mientras que el encabezado del CSV dice `KM`. Se asume km, por el
  encabezado, y se anota como supuesto.
- **Canales de regadío:** el diccionario dice *"Longitud o tramos…
  (Metros/Km)"*. No se puede saber si cada valor es metros, km o número
  de tramos. **No se sumarán ni se mostrarán con unidad.**

---

## 4. Completitud por columna

Todas las columnas de identificación, tiempo, territorio y peligro están
completas (0 vacíos). En las numéricas:

| Columna | Vacíos | Ceros | > 0 | Negativos | Máximo |
|---|---|---|---|---|---|
| FALLECIDOS | 0 | 140 565 | 1 574 | 0 | 299 |
| DESAPARECIDOS | 0 | 141 968 | 171 | 0 | 37 |
| LESIONADOS | 0 | 139 496 | 2 643 | 0 | 2 230 |
| DAMNIFICADOS | 0 | 88 365 | 53 774 | 0 | 52 085 |
| AFECTADOS | 0 | 72 659 | 69 480 | 0 | 300 000 |
| VIVIENDAS DESTRUIDAS | 0 | 93 915 | 48 224 | 0 | 10 417 |
| VIVIENDAS AFECTADAS | 0 | 89 034 | 53 105 | 0 | 41 824 |
| CENTROS EDUCATIVOS DESTRUIDOS | 67 208 | 74 391 | 540 | 0 | 33 |
| CENTROS EDUCATIVOS AFECTADOS | 67 208 | 71 295 | 3 636 | 0 | 140 |
| SCANT_CCEE | 74 931 | 63 480 | 3 728 | 0 | 104 |
| SAFEC_AULA | 74 931 | 64 198 | 3 010 | 0 | 263 |
| AULA_DES+INHA | 91 332 | 49 429 | 1 378 | 0 | 202 |
| SDESTRU_AULA | 74 931 | 66 666 | 542 | 0 | 50 |
| SINHAB_AULA | 74 931 | 65 801 | 1 407 | 0 | 191 |
| CENTROS SALUD DESTRUIDOS | 0 | 141 780 | 359 | 0 | 27 |
| CENTROS SALUD AFECTADOS | 0 | 139 024 | 3 115 | 0 | 48 |
| HAS CULTIVO DESTRUIDO | 0 | 129 999 | 12 140 | 0 | 20 000 |
| HAS CULTIVO AFECTADO | 0 | 125 518 | 16 621 | 0 | 124 000 |
| PUENTE DESTRUIDO | 0 | 140 147 | 1 992 | 0 | 100 |
| PUENTE AFECTADO | 0 | 138 623 | 3 516 | 0 | 1 000 |
| CARRETERA DESTRUIDA KM | 0 | 134 842 | 7 297 | 0 | 122,92 |
| CARRETERA AFECTADA KM | 0 | 126 294 | 15 845 | 0 | 669,10 |
| CAMINO RURAL DESTRUIDO KM | 0 | 140 402 | 1 737 | 0 | 112 |
| CAMINO RURAL AFECTADO KM | 0 | 138 424 | 3 715 | 0 | 463 |
| CANAL DE REGADIO COLAPSADO | 0 | 137 762 | 4 377 | 0 | 204,70 |
| CANAL DE REGADIO AFECTADO | 0 | 135 877 | 6 262 | 0 | 920 |
| PERDIDA VACUNO | 0 | 139 178 | 2 961 | 0 | 32 985 |
| AFECTADOS VACUNO | 0 | 136 761 | 5 378 | 0 | 94 800 |
| PERDIDA CAMELIDO | 0 | 139 322 | 2 817 | 0 | 81 470 |
| AFECTADOS CAMELIDO | 0 | 138 182 | 3 957 | 0 | 1 411 000 |
| PERDIDA CAPRINO | 0 | 138 482 | 3 657 | 0 | 86 561 |
| AFECTADOS CAPRINO | 0 | 138 824 | 3 315 | 0 | 69 765 |
| PERDIDA PORCINO | 0 | 141 107 | 1 032 | 0 | 12 434 |
| AFECTADOS PORCINO | 0 | 140 298 | 1 841 | 0 | 45 000 |
| PERDIDA DE ANIMALES MENORES | 0 | 139 550 | 2 589 | 0 | 19 979 |
| AFECTA DE ANIMALES MENORES | 0 | 139 404 | 2 735 | 0 | 1 000 000 |
| PESO DE LA AYUDA | 0 | 75 580 | 66 559 | 0 | 112 093 964,2 |
| COSTO DE LA AYUDA | 0 | 76 124 | 66 014 | 1 | 47 933 190,46 |

### Valores atípicos a revisar

Algunos máximos son cifras redondas muy altas: `AFECTADOS` = 300 000,
`AFECTADOS CAMELIDO` = 1 411 000, `AFECTA DE ANIMALES MENORES` =
1 000 000, `PUENTE AFECTADO` = 1 000. Podrían ser estimaciones
legítimas de eventos grandes (heladas, sequías) o errores de digitación.

**Decisión:** no se modifican ni eliminan. En la Fase 2 se listarán los
registros atípicos de cada columna (por ejemplo, por encima del
percentil 99,9). En los rankings y totales que puedan estar dominados
por un solo registro se mostrará una nota.

---

## 5. Categorías

| Columna | Valores distintos |
|---|---|
| `TIPO DE PELIGRO` | `ORIGEN NATURAL` (106 301), `INDUCIDOS POR LA ACCIÓN HUMANA` (35 838) |
| `REGIÓN NATURAL` | `SIERRA` (87 578), `COSTA` (22 322), `SELVA BAJA` (16 838), `SELVA ALTA` (15 401) |
| `MES` | 12 meses en texto; septiembre aparece como `SETIEMBRE` |
| `PELIGRO` | 22 valores (tabla siguiente) |

### Catálogo de peligros

| Tipo | Peligro | Filas |
|---|---|---|
| Natural | LLUVIA INTENSA | 42 824 |
| Humano | INCENDIO URB. E INDUST. | 30 282 |
| Natural | VIENTOS FUERTES | 18 784 |
| Natural | BAJAS TEMPERATURAS | 17 949 |
| Natural | INUNDACION | 7 614 |
| Natural | DESLIZAMIENTO | 5 911 |
| Humano | INCENDIO FORESTAL | 4 717 |
| Natural | HUAYCO | 2 753 |
| Natural | SEQUIA | 2 716 |
| Natural | EROSION | 2 663 |
| Natural | DERRUMBE DE CERRO | 1 850 |
| Natural | SISMO | 1 637 |
| Natural | TORMENTA ELECTRICA | 646 |
| Humano | zOTROS | 459 |
| Natural | PLAGAS | 416 |
| Natural | MAREJADA | 191 |
| Humano | CONTAMINACION | 172 |
| Natural | EPIDEMIAS | 150 |
| Natural | ALUD | 138 |
| Humano | EXPLOSION | 110 |
| Humano | DERRAME DE SUSTANCIAS PELIGROSAS | 98 |
| Natural | ACTIVIDAD VOLCANICA | 59 |

Cada peligro pertenece siempre al mismo tipo. `zOTROS` lleva una "z"
inicial, probablemente para que aparezca al final al ordenar. Se
conserva el valor original y en la interfaz se muestra como "Otros".

---

## 6. Territorio

| Comprobación | Resultado |
|---|---|
| Ubigeos vacíos o mal formados | 0 (todos tienen 6 dígitos) |
| Departamentos (código) | 25 |
| Provincias (código) | 196 |
| Distritos (código) | 1 888 |
| Lima | Código `15` dividido en `LIMA METROPOLITANA` (5 900) y `LIMA PROVINCIAS` (3 767) |

### Filas por departamento

| Cód. | Departamento | Filas | Cód. | Departamento | Filas |
|---|---|---|---|---|---|
| 01 | Amazonas | 5 779 | 14 | Lambayeque | 1 584 |
| 02 | Áncash | 6 137 | 15 | Lima Metropolitana | 5 900 |
| 03 | Apurímac | 14 321 | 15 | Lima Provincias | 3 767 |
| 04 | Arequipa | 4 549 | 16 | Loreto | 4 843 |
| 05 | Ayacucho | 10 304 | 17 | Madre de Dios | 1 159 |
| 06 | Cajamarca | 9 346 | 18 | Moquegua | 1 704 |
| 07 | Callao | 1 136 | 19 | Pasco | 5 879 |
| 08 | Cusco | 11 398 | 20 | Piura | 6 065 |
| 09 | Huancavelica | 11 981 | 21 | Puno | 6 327 |
| 10 | Huánuco | 7 294 | 22 | San Martín | 5 538 |
| 11 | Ica | 1 750 | 23 | Tacna | 1 274 |
| 12 | Junín | 4 972 | 24 | Tumbes | 1 971 |
| 13 | La Libertad | 2 853 | 25 | Ucayali | 4 308 |

### Lima Metropolitana y Lima Provincias

La división del CSV coincide exactamente con las provincias:

| Nombre en `DPTO.` | Provincias (ubigeo) | Filas |
|---|---|---|
| LIMA METROPOLITANA | `1501` | 5 900 |
| LIMA PROVINCIAS | `1502` a `1510` | 3 767 |

**Decisión:** el filtro de departamento usa los **26 nombres tal como los
publica INDECI**, con Lima Metropolitana y Lima Provincias por separado.
Los filtros de provincia y distrito usan el ubigeo. En el mapa
departamental, ambas se suman en el polígono de Lima (código `15`).

### Ubigeos con más de un nombre

8 ubigeos aparecen con dos nombres distintos en el CSV:

| Ubigeo | Variante A (filas) | Variante B (filas) | Causa probable |
|---|---|---|---|
| 050116 | ANDRES AVELINO CACERES DORREG (1) | ANDRES AVELINO CACERES DORREGARAY (12) | Nombre cortado |
| 051010 | HUALLA (27) | HUAYA (50) | Variante ortográfica |
| 060506 | SANTA CRUZ DE TOLED (9) | SANTA CRUZ DE TOLEDO (20) | Nombre cortado |
| 100106 | QUISQUI (87) | QUISQUI (KICHKI) (21) | Cambio de nombre oficial |
| 150118 | LURIGANCHO (134) | LURIGANCHO-CHOSICA (16) | Variante del nombre |
| 160601 | CONTAMANA (13) | CONTUMAZA (176) | **Error:** Contumazá es una provincia de Cajamarca |
| 190108 | SAN FRANCISCO DE ASIS DE YARU (10) | SAN FRANCISCO DE ASIS DE YARUSYACAN (209) | Nombre cortado |
| 230110 | CORONEL GREGORIO ALBARRACIN L (16) | CORONEL GREGORIO ALBARRACIN LANCHIPA (86) | Nombre cortado |

**Decisión:** el **ubigeo es la referencia**, no el nombre. Los
documentos conservan el nombre original de cada fila. Para los filtros y
el mapa se usa un catálogo con un solo nombre por ubigeo, tomado del
listado oficial del INEI. No se usa el nombre más frecuente, porque en
`160601` el más frecuente es el erróneo.

### Fuente territorial de referencia (límites y nombres oficiales)

Se evaluaron tres fuentes públicas cruzando los 1 888 ubigeos del CSV:

| Fuente | Vigencia de los límites | Cobertura de distritos del CSV | Resultado |
|---|---|---|---|
| `juaneladio/peru-geojson` | INEI 2007 | 1 822 / 1 888 (faltan 66 distritos y la provincia Putumayo; 3 545 filas) | Descartada |
| `jmcastagnetto/ubigeo-peru-aumentado` (solo lista, sin polígonos) | INEI 2015 | 1 886 / 1 888 | Descartada |
| **`lincolneulogio/peru-geojson`** | **IDE-INEI, actualización 2023** | **1 887 / 1 888** | **Elegida** |

**Fuente elegida:** https://github.com/lincolneulogio/peru-geojson

- Licencia MIT. Límites releídos del geoportal IDE-INEI (descarga del 6 de
  marzo de 2026, publicados como actualizados a 2023).
- Archivos usados (variante ligera, coordenadas con 4 decimales):

| Archivo | Polígonos | Tamaño | Cobertura del CSV |
|---|---|---|---|
| `departamentos.geojson` | 25 | 97 KB | 25 / 25 |
| `provincias.geojson` | 196 | 455 KB | 196 / 196 |
| `distritos.geojson` | 1 890 | 1,6 MB | 1 887 / 1 888 |

- Copia local: `data/referencias/peru-geojson-2023/`, con su `LICENSE` y
  su `README.md`.

**Único distrito sin polígono:** `130112` ALTO TRUJILLO (La Libertad /
Trujillo), con 11 filas, todas de 2025. Es un distrito de creación muy
reciente que aún no figura en los límites del INEI. Sus registros se
cargan normalmente y se cuentan en los niveles de provincia y
departamento. En el mapa distrital aparece como "sin polígono".

**Hallazgo:** el CSV usa los **ubigeos actuales incluso en años
antiguos**. Por ejemplo, el distrito Putumayo (`160801`, provincia creada
en 2014) aparece desde 2003. Por eso se necesitan límites recientes y no
los de 2007.

### Nombres oficiales para el catálogo `territorios`

El catálogo toma los nombres del IDE-INEI 2023 (en mayúsculas) y
resuelve 7 de los 8 conflictos de nombre: CONTAMANA, HUAYA,
QUISQUI (KICHKI), LURIGANCHO, etc. Excepciones:

- `060506`: el propio INEI publica "SANTA CRUZ DE TOLED" (cortado); se
  usa SANTA CRUZ DE TOLEDO, la variante completa del CSV.
- `130112` ALTO TRUJILLO: no está en la fuente; se toma el nombre del
  CSV.
- Departamentos: se usan los 26 nombres de INDECI (Lima Metropolitana y
  Lima Provincias por separado), porque así filtra la aplicación.

Los nombres del IDE-INEI vienen sin tildes ("UNION ASHANINKA"). Se
conservan así, por consistencia con el CSV, que tampoco las usa en los
nombres territoriales.

---

## 7. Estimación de almacenamiento en Atlas Free (M0)

### Límite aplicable

Según la documentación oficial (*Atlas Free Cluster Limits*, consultada
el 18/11/2026), el clúster gratuito tiene **0,5 GB**, que incluyen "todos
los documentos BSON sin comprimir de todas las colecciones, más los bytes
de sus índices".

### Medición

Se convirtieron las **142 139 filas** al esquema propuesto en
`ALERTA_PERU_CONTEXTO_PROYECTO.md` (sección 9), con la regla de vacío
frente a cero, y se codificaron con la librería `bson` de `pymongo`. Es
una medición sobre el conjunto completo, no una extrapolación.

| Concepto | Valor | Tipo |
|---|---|---|
| Documentos generados | 142 139 (`_id` único en todos) | medido |
| Tamaño BSON total | **166,9 MB** | medido |
| Tamaño medio por documento | 1 174 bytes | medido |
| Longitud media del `_id` | 23,4 caracteres | medido |
| Índices (`_id` + 5 candidatos) | \~25–40 MB | estimado |
| Colección `agregados` (año, mes, provincia, peligro: 58 563 combinaciones) | \~20 MB | estimado |
| **Total previsto** | **\~210–230 MB (\~45 % del límite)** | |

**Conclusión:** el dataset completo **cabe en el plan gratuito** con
margen, sin recortar campos.

### Medición real en Atlas (18/11/2026, después de la carga)

Resultado de `etl/verificar.py` (`dbStats`):

| Concepto | Estimado | Real |
|---|---|---|
| Datos (sin comprimir) | 166,9 MB | **167,2 MB** (incluye `territorios` y `metadatos_carga`) |
| Índices | 25–40 MB | **16,6 MB** |
| Agregados | \~20 MB | **0 MB** (no fue necesaria; ver documento principal, sección 9) |
| **Total** | 210–230 MB | **183,8 MB (34 % de 0,5 GB)** |

El panel de Atlas muestra un "Data size" menor (unos 103 MB) porque
indica el tamaño comprimido en disco. El límite de M0 se aplica sobre
el tamaño sin comprimir.

La carga completa tardó 27 minutos (1 620 s) a unos 90 documentos por
segundo, con 142 139 insertados y 0 rechazos. Todas las comprobaciones
de `verificar.py` resultaron correctas: conteos por año y departamento,
sumas de 5 indicadores, vacíos frente a ceros, fechas de 2025 y uso de
índices en las 5 consultas principales.

---

## 8. Pendientes

| Pendiente | Fase |
|---|---|
| Listado de valores atípicos por columna | 2 |
| Confirmar las unidades de peso y costo de la ayuda con INDECI | Externa, opcional |
| Revisar las mayúsculas y tildes de los nombres territoriales (codificación Latin-1) | 3 |

---

## 9. Recomendación

**Continuar con el dataset completo (142 139 registros).**

- El volumen supera los 100 000 registros exigidos, sin necesidad de
  duplicar ni completar datos.
- La estructura es consistente: siempre 49 columnas, sin filas
  duplicadas y con territorio y tiempo completos.
- Los problemas encontrados (formato de fecha, código no único, unidades
  de la ayuda y cambio educativo de 2019) **tienen tratamiento
  documentado** y no impiden el proyecto.
- El almacenamiento previsto (\~210–230 MB) cabe en los 0,5 GB de
  Atlas M0 (sección 7).
