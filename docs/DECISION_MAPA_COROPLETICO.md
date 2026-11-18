# Decisión técnica: mapa coroplético por ubigeo en lugar de marcadores

**Proyecto:** ALERTA PERÚ
**Tipo de documento:** registro de decisión técnica
**Fecha:** 18 de noviembre de 2026
**Estado:** aprobada
**Módulo afectado:** Módulo 2, mapa interactivo

---

## 1. Resumen

El mapa de ALERTA PERÚ **no mostrará un marcador por emergencia**. En su lugar
usará un **mapa coroplético**: cada departamento, provincia o distrito se pinta
con un color más o menos intenso según la cantidad de emergencias registradas
(u otro indicador seleccionado).

El motivo es que el dataset oficial **no contiene coordenadas geográficas**. La
ubicación más precisa disponible es el **distrito**, identificado por su código
de ubigeo. Un mapa coroplético representa exactamente esa precisión, sin
inventar posiciones.

---

## 2. Contexto: qué información geográfica trae la fuente

Fuente: *Emergencias y Evaluación de Daños – SINPAD 2003–2025*, publicada por
INDECI en la Plataforma Nacional de Datos Abiertos
(<https://www.datosabiertos.gob.pe/dataset/emergencias-y-daños-nivel-nacional-por-departamento>).

Al inspeccionar el archivo `BD_2003-2025_EMERGENCIAS.csv` (142 139 filas,
49 columnas), las únicas columnas de ubicación son:

| Columna          | Ejemplo        | Significado                         |
|------------------|----------------|-------------------------------------|
| `COD. DISTRITO`  | `010101`       | Ubigeo del distrito (6 dígitos)     |
| `DPTO.`          | `AMAZONAS`     | Nombre del departamento             |
| `PROV.`          | `CHACHAPOYAS`  | Nombre de la provincia              |
| `DIST.`          | `CHACHAPOYAS`  | Nombre del distrito                 |
| `REGIÓN NATURAL` | `SIERRA`       | Costa, sierra o selva               |

**No existe ninguna columna de latitud, longitud, dirección ni centro poblado.**

Comprobaciones realizadas sobre el archivo:

- Las 142 139 filas tienen un ubigeo de exactamente 6 dígitos; no hay valores vacíos ni mal formados.
- Hay 1 888 códigos de distrito distintos, 196 de provincia y 25 de departamento.

---

## 3. El problema con los marcadores

La propuesta inicial era colocar un marcador por emergencia y agruparlos
(*clustering*) cuando hubiera muchos. Para eso cada registro necesita un punto
exacto en el mapa. Como la fuente no lo tiene, habría que fabricarlo. Las
opciones analizadas fueron:

### Opción A: geocodificar el nombre del distrito

Consiste en convertir "Chachapoyas, Amazonas" en una coordenada usando un
servicio de geocodificación.

- **Resultado:** el servicio devuelve **un solo punto por distrito**, normalmente
  su capital o su centro geométrico.
- **Consecuencia:** todas las emergencias de un distrito quedan apiladas en el
  mismo punto exacto. Por ejemplo, los 848 registros de Abancay aparecerían como
  si todos hubieran ocurrido en el mismo lugar.
- **Problema principal:** el mapa aparenta una **precisión que el dato no tiene**.
  Un usuario podría interpretar que una inundación ocurrió en la plaza central
  cuando en realidad pudo ocurrir en cualquier punto del distrito.
- Además añade dependencia de un servicio externo, errores de coincidencia de
  nombres y almacenamiento extra.

### Opción B: dispersar puntos aleatoriamente dentro del distrito (*jitter*)

Consiste en asignar a cada emergencia una posición aleatoria dentro del límite
de su distrito, para que los puntos no se superpongan.

- **Problema principal:** se estarían **inventando ubicaciones**. Visualmente
  parecen datos reales, pero son posiciones ficticias. Esto contradice la
  regla central del proyecto: no inventar datos.

### Opción C: mapa coroplético por ubigeo (elegida)

En lugar de puntos, se colorea cada territorio completo según su valor:

```text
┌──────────────────────────────┐
│   ░░ Tumbes                  │     Leyenda (ejemplo)
│  ▒▒▒ Piura     ░ Amazonas    │     ░  menos registros
│   ▒▒ Lima                    │     ▒
│      ░ Ica     ▓▓▓ Cusco     │     ▓
│           ▓▓▓ Apurímac       │     █  más registros
└──────────────────────────────┘
```

- **Representa exactamente lo que dice el dato:** "esta emergencia ocurrió en
  este distrito".
- **No inventa ninguna coordenada.**

---

## 4. Comparación de opciones

| Criterio                                   | A. Geocodificar     | B. Puntos aleatorios | C. Coroplético |
|--------------------------------------------|---------------------|----------------------|----------------|
| Respeta la precisión real del dato         | No                  | No                   | **Sí**         |
| Inventa información                        | Parcialmente        | Sí                   | **No**         |
| Depende de servicios externos              | Sí                  | No                   | **No**         |
| Legible con 142 139 registros              | Baja (apilamiento)  | Media                | **Alta**       |
| Permite comparar territorios               | Difícil             | Difícil              | **Directo**    |
| Consumo de almacenamiento en Atlas M0      | Mayor               | Mayor                | **Nulo** (\*)  |
| Encaja con el análisis de BI por dimensión | Débil               | Débil                | **Fuerte**     |

(\*) Los límites territoriales se sirven como archivo estático del frontend, no
se guardan en MongoDB.

---

## 5. Cómo funciona el ubigeo

El ubigeo es el código oficial del INEI para identificar cada distrito. Es
jerárquico:

```text
 01  01  01
 │   │   └── distrito      (Chachapoyas)
 │   └────── provincia     (Chachapoyas)
 └────────── departamento  (Amazonas)
```

Con un único campo se puede agregar en tres niveles, sin columnas adicionales:

| Nivel        | Regla                    | Ejemplo  |
|--------------|--------------------------|----------|
| Departamento | primeros 2 dígitos       | `01`     |
| Provincia    | primeros 4 dígitos       | `0101`   |
| Distrito     | código completo          | `010101` |

Por eso el ubigeo **debe guardarse como texto**: si se convierte a número,
`010101` se convierte en `10101` y se pierde la jerarquía.

---

## 6. Implementación prevista

1. **Límites territoriales:** se usan los límites del **IDE-INEI, actualización
   2023**, en la versión GeoJSON publicada por
   [lincolneulogio/peru-geojson](https://github.com/lincolneulogio/peru-geojson)
   (licencia MIT). Cada polígono incluye su ubigeo. Cobertura verificada contra
   el CSV: 25/25 departamentos, 196/196 provincias y 1 887/1 888 distritos.
2. **Simplificación:** se usa la variante ligera de la fuente, ya simplificada
   (en total, unos 2,2 MB para los tres niveles). El nivel distrital (1,6 MB)
   solo se carga al bajar a ese nivel.
3. **Backend:** un endpoint devuelve solo los totales agregados por ubigeo y
   nivel, respetando los filtros de fecha y tipo de peligro. Ejemplo de
   respuesta:

   ```json
   [
     { "ubigeo": "03", "nombre": "APURIMAC", "total": 14321 },
     { "ubigeo": "09", "nombre": "HUANCAVELICA", "total": 11981 }
   ]
   ```

   La respuesta tiene como máximo 25 elementos a nivel departamental, no
   142 139 registros.
4. **Frontend:** Leaflet dibuja el GeoJSON, cruza cada polígono con su total
   mediante el ubigeo y le asigna un color según una escala con leyenda.
5. **Navegación por niveles:** la vista inicial muestra departamentos. Al hacer
   clic en uno se muestran sus provincias y luego sus distritos. Cada territorio
   tiene un popup con sus totales y un enlace a sus registros en el explorador.
6. **Territorios sin registros:** se pintan con un color neutro distinto, para no
   confundir "sin registros" con "valor bajo".

---

## 7. Limitaciones y cómo se manejan

| Limitación | Tratamiento |
|------------|-------------|
| **Cambios de ubigeo en el tiempo:** entre 2003 y 2025 se crearon distritos nuevos. | Verificado: el CSV usa los ubigeos actuales en todos los años, y los límites del INEI de 2023 cubren 1 887 de 1 888 distritos. Con límites de 2007 habrían faltado 66. El único sin polígono es Alto Trujillo (`130112`, 11 registros de 2025), que se muestra como "sin polígono" y se cuenta en su provincia y departamento. No se reasigna a otro distrito. |
| **Lima dividida en dos:** el CSV usa `LIMA METROPOLITANA` (5 900 registros) y `LIMA PROVINCIAS` (3 767) dentro del mismo código de departamento `15`. | Las capas de límites suelen tener un solo departamento Lima. A nivel departamental se sumarán ambos bajo el código `15`. A nivel provincial y distrital la separación se mantiene de forma natural por ubigeo. |
| **Precisión limitada al distrito:** no se puede saber dónde ocurrió el evento dentro del distrito. | Es una limitación de la fuente y se declara en la sección "Acerca de los datos". |
| **Los conteos absolutos favorecen territorios grandes o muy poblados.** | La primera versión muestra conteos absolutos. Más adelante podría añadirse una tasa por población o por superficie, siempre con una fuente oficial documentada. |
| **Un color más intenso no implica mayor riesgo.** | El conteo depende también de cuántos eventos se reportan al SINPAD. El mapa muestra registros, no niveles de riesgo, y así se indicará en la leyenda. |

---

## 8. Conclusión

El mapa coroplético es la representación **más fiel** a los datos disponibles:

- muestra la información con la precisión real de la fuente (el distrito);
- no inventa coordenadas ni usa aproximaciones que puedan inducir a error;
- es más simple de implementar y no consume almacenamiento en Atlas M0;
- permite comparar territorios directamente y analizar en tres niveles
  (departamento, provincia y distrito), lo que se alinea con el enfoque de
  Inteligencia de Negocios del proyecto.

Si en el futuro se dispusiera de una fuente oficial con coordenadas por evento,
se podría añadir una capa de puntos como complemento, sin reemplazar esta vista.
