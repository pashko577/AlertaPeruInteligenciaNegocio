# Decisión de diseño: interfaz de "atlas de campo"

**Proyecto:** ALERTA PERÚ
**Fecha:** 18 de noviembre de 2026
**Módulo afectado:** frontend (`backend/src/main/resources/static/`)

---

## 1. Objetivo

El docente evalúa que los datos se **representen correctamente** y se puedan
**filtrar**. Además, se buscó una identidad visual propia, ligada al tema
(emergencias en el territorio peruano), y no la estética genérica de panel
administrativo: tarjetas idénticas con sombra, degradados, etiquetas en
mayúsculas e iconos decorativos.

## 2. Idea central: la consulta se escribe como una frase

En lugar de una barra de filtros con desplegables sueltos, la consulta se lee
como una oración en la que cada parte resaltada es un selector:

> Mostrar **damnificados** en **Cusco**, entre **2020** y **2025**, por
> **huayco**, en **cualquier región natural**.

Debajo aparece la respuesta como cifra principal, con un resumen escrito en
lenguaje llano.

**Por qué:**

- Es una consulta de Inteligencia de Negocios expresada en lenguaje natural:
  medida (qué se cuenta) y dimensiones (dónde, cuándo, por qué peligro).
- Quien la lee entiende siempre qué filtro está aplicado, sin buscar etiquetas.
- La URL guarda la consulta, de modo que un análisis se puede compartir con un
  enlace.

## 3. Identidad visual

| Elemento | Decisión | Motivo |
|---|---|---|
| Fondo | Papel gris verdoso frío (`#edefea`) | Recuerda el papel de las cartas topográficas; evita el fondo crema típico |
| Banda superior | Pizarra (`#24343f`) con **curvas de nivel** | Referencia a la cartografía andina del IGN; es el único elemento decorativo |
| Color | Solo los datos llevan color | La interfaz es monocroma; el color siempre significa algo |
| Tipografía | **Archivo** (Omnibus-Type, Argentina) | Fundidora latinoamericana; su eje de ancho se usa de forma activa: expandido en la frase y la marca, condensado en la tabla |
| Mapa | Sin teselas comerciales: solo los límites del INEI sobre el papel | Aspecto de atlas impreso; ver `DECISION_MAPA_COROPLETICO.md` |
| Cifras | Franja con separadores finos, sin tarjetas | Jerarquía clara sin el "kit de tarjetas" genérico |

## 4. Visualización de datos

Se siguió un método de visualización con comprobaciones verificables:

- **Paleta comprobada con un validador de daltonismo.** Azul para los peligros
  de origen natural y naranja para los inducidos por la acción humana. Pasan
  todas las pruebas (separación ΔE de 24,7 en protanopía), en modo claro y oscuro.
- **Escala secuencial de un solo tono** (azul claro a oscuro) para el mapa, con
  "Sin registros" en gris neutro, distinto del valor más bajo.
- **Barras finas** (18–24 px), extremo redondeado y base recta, cuadrícula
  tenue sin líneas punteadas.
- **Etiquetas selectivas:** solo el máximo, el último año y los tres primeros
  del ranking, no un número en cada barra.
- **Énfasis por atenuación:** al elegir un peligro o un distrito, los demás
  pasan a gris y el elegido conserva su color.
- **Cada gráfico tiene su tabla gemela** ("Ver como tabla"), para
  accesibilidad y para consultar valores exactos.
- **Nunca dos ejes Y** en un mismo gráfico.

## 5. Representación correcta (reglas de la auditoría)

- Un valor vacío en la fuente se muestra como *sin dato*, nunca como 0.
- En el detalle de un registro de 2003, los campos de aulas dicen "desde 2019"
  y no "0".
- Peso y costo de la ayuda solo aparecen en el detalle, con la nota de
  "unidades no verificadas".
- "zOTROS" se muestra como "Otros".
- Los nombres territoriales provienen del catálogo oficial (CONTAMANA, no
  CONTUMAZA).

## 6. Calidad

- Adaptable a móvil: sin desplazamiento horizontal a 390 px (verificado).
- Navegable con teclado: pestañas con flechas, filas de la tabla con Enter y
  foco visible.
- Contraste AA en todos los textos (el tono más tenue tiene 4,8:1 sobre el papel).
- Modo oscuro automático según el sistema operativo, con sus propios tonos.
- Respeta "reducir movimiento" del sistema.
- Las librerías (Bootstrap 5.3.9, Chart.js 4.6.1, Leaflet 1.9.4) y la fuente
  están incluidas en el proyecto: la presentación no depende de servidores
  externos de archivos.
