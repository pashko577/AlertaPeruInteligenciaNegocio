// Mapa coroplético por ubigeo (ver DECISION_MAPA_COROPLETICO.md).
// Sin teselas comerciales: solo los límites del INEI sobre el papel del atlas.
import { obtener, obtenerArchivo, obtenerUnaVez } from './api.js';
import { aplicar, estado, medida, parametros } from './consulta.js';
import { escapar, fmt, nombrePropio } from './formato.js';
import { color, rampa } from './tema.js';

const $ = (id) => document.getElementById(id);
const ARCHIVOS = { departamento: 'geo/departamentos.geojson', provincia: 'geo/provincias.geojson', distrito: 'geo/distritos.geojson' };

let mapa = null;
let capa = null;
let vista = { nivel: 'departamento', dep: null, prov: null };
let datos = new Map();     // código → conteo agregado
let nombresIndeci = new Map(); // código de departamento → nombres INDECI (Lima tiene dos)
let clases = [];
let pendiente = false;

function crearMapa() {
  mapa = window.L.map('mapa', {
    zoomSnap: 0.25, zoomDelta: 0.5, minZoom: 4, maxZoom: 11,
    attributionControl: true, scrollWheelZoom: true,
  });
  mapa.attributionControl.setPrefix(false);
  mapa.attributionControl.addAttribution('Límites: IDE-INEI 2023. Datos: INDECI, SINPAD');
  mapa.setView([-9.2, -75.0], 5);
}

/** Vista inicial coherente con la consulta. */
async function vistaDesdeConsulta() {
  if (estado.provincia) return { nivel: 'distrito', dep: estado.provincia.slice(0, 2), prov: estado.provincia };
  if (estado.departamento) {
    const deps = await obtenerUnaVez('deps', () => obtener('/catalogos/departamentos'));
    const d = deps.find((x) => x.codigo === estado.departamento);
    return { nivel: 'provincia', dep: d?.codDepartamento ?? null, prov: null };
  }
  return { nivel: 'departamento', dep: null, prov: null };
}

async function cargarDatos() {
  const m = medida();
  let conteos;
  if (vista.nivel === 'distrito') {
    conteos = await obtener('/estadisticas/por-territorio', { ...parametros(['distrito', 'provincia']), provincia: vista.prov, nivel: 'distrito' });
  } else {
    conteos = await obtener('/estadisticas/por-territorio', { ...parametros(['distrito', 'provincia']), nivel: vista.nivel });
  }
  datos = new Map();
  nombresIndeci = new Map();
  for (const c of conteos) {
    // Departamento: la API agrupa por nombre INDECI; el mapa suma por código (Lima = 15)
    const codigo = vista.nivel === 'departamento' ? c.codigo : c.clave;
    if (vista.nivel === 'provincia' && vista.dep && !codigo.startsWith(vista.dep)) continue;
    if (vista.nivel === 'departamento') {
      nombresIndeci.set(codigo, [...(nombresIndeci.get(codigo) ?? []), c.clave]);
    }
    const previo = datos.get(codigo) ?? { emergencias: 0, damnificados: 0, fallecidos: 0, afectados: 0, viviendasDestruidas: 0, nombre: c.nombre };
    for (const k of ['emergencias', 'damnificados', 'fallecidos', 'afectados', 'viviendasDestruidas']) previo[k] += c[k];
    datos.set(codigo, previo);
  }
  // Un polígono que suma varios departamentos INDECI (Lima = Metropolitana + Provincias)
  // se nombra con todos, para que la cifra no parezca de uno solo.
  for (const [codigo, nombres] of nombresIndeci) {
    if (nombres.length > 1 && datos.has(codigo)) {
      datos.get(codigo).nombre = `${nombres.map(nombrePropio).join(' + ')}`;
    }
  }
  calcularClases([...datos.values()].map((d) => d[m.campo]).filter((v) => v > 0));
}

/** Cortes por cuantiles (hasta 6 clases), redondeados a enteros. */
function calcularClases(valores) {
  valores.sort((a, b) => a - b);
  const n = valores.length;
  if (!n) { clases = []; return; }
  const k = Math.min(6, n);
  const cortes = [];
  for (let i = 1; i <= k; i++) {
    const v = Math.ceil(valores[Math.min(n - 1, Math.ceil((i / k) * n) - 1)]);
    if (!cortes.includes(v)) cortes.push(v);
  }
  const colores = rampa();
  // Repartir los colores de la rampa a lo ancho si hay menos clases que pasos
  clases = cortes.map((hasta, i) => ({
    desde: i === 0 ? 1 : cortes[i - 1] + 1,
    hasta,
    color: colores[Math.round((i * (colores.length - 1)) / Math.max(1, cortes.length - 1))],
  }));
  if (clases.length === 1) clases[0].color = colores[3];
}

function colorDe(valor) {
  if (!valor) return color('sin-registros');
  return (clases.find((c) => valor <= c.hasta) ?? clases.at(-1)).color;
}

function nombreFeature(props) {
  const n = vista.nivel === 'departamento' ? props.nombre_departamento
    : vista.nivel === 'provincia' ? props.nombre_provincia : props.nombre_distrito;
  return nombrePropio(n);
}

async function dibujarCapa() {
  const geo = await obtenerArchivo(ARCHIVOS[vista.nivel]);
  const prefijo = vista.nivel === 'provincia' ? vista.dep : vista.nivel === 'distrito' ? vista.prov : null;
  const features = prefijo ? geo.features.filter((f) => f.properties.ubigeo.startsWith(prefijo)) : geo.features;
  const m = medida();
  capa?.remove();
  capa = window.L.geoJSON({ type: 'FeatureCollection', features }, {
    style: (f) => ({
      fillColor: colorDe(datos.get(f.properties.ubigeo)?.[m.campo]),
      fillOpacity: 1,
      color: color('borde-mapa'),
      weight: vista.nivel === 'distrito' ? 0.6 : 0.9,
    }),
    onEachFeature: (f, l) => {
      const d = datos.get(f.properties.ubigeo);
      l.bindTooltip(`<strong>${escapar(nombreFeature(f.properties))}</strong><br>${d ? fmt.n(d[m.campo]) : 'Sin registros'}${d ? ` ${m.plural}` : ''}`,
        { sticky: true, className: 'etiqueta-mapa', direction: 'top', offset: [0, -8] });
      l.on({
        mouseover: () => { l.setStyle({ weight: 2.2, color: color('tinta') }); l.bringToFront(); mostrarFicha(f.properties); },
        mouseout: () => capa.resetStyle(l),
        click: () => entrar(f.properties),
      });
    },
  }).addTo(mapa);
  if (features.length) mapa.fitBounds(capa.getBounds(), { padding: [16, 16] });
  avisoSinPoligono(new Set(features.map((f) => f.properties.ubigeo)));
}

function avisoSinPoligono(conPoligono) {
  const sueltos = [...datos.entries()].filter(([codigo]) => !conPoligono.has(codigo));
  const nota = $('ficha').dataset.nota = sueltos.length
    ? `${sueltos.map(([, d]) => nombrePropio(d.nombre)).join(', ')} tiene registros pero no figura en la cartografía del INEI de 2023. Sus cifras aparecen en la tabla del mapa.`
    : '';
  $('ficha').querySelector('.ficha__aviso')?.remove();
  if (nota) $('ficha').insertAdjacentHTML('beforeend', `<p class="lamina__nota ficha__aviso">${escapar(nota)}</p>`);
}

function mostrarFicha(props) {
  const m = medida();
  const d = datos.get(props.ubigeo);
  const filas = d ? [
    ['Emergencias', d.emergencias], ['Damnificados', d.damnificados], ['Afectados', d.afectados],
    ['Fallecidos', d.fallecidos], ['Viviendas destruidas', d.viviendasDestruidas],
  ] : [];
  let acciones = '';
  if (vista.nivel === 'departamento') {
    const nombres = nombresIndeci.get(props.ubigeo) ?? [];
    acciones = nombres.map((n) => `<button type="button" class="boton" data-filtro-dep="${escapar(n)}">Filtrar por ${escapar(nombrePropio(n))}</button>`).join(' ');
  } else if (vista.nivel === 'provincia') {
    acciones = `<button type="button" class="boton" data-filtro-prov="${escapar(props.ubigeo)}">Filtrar por esta provincia</button>`;
  } else {
    acciones = `<button type="button" class="boton" data-filtro-dist="${escapar(props.ubigeo)}">Filtrar por este distrito</button>`;
  }
  $('ficha').innerHTML = `<p class="ficha__nombre">${escapar(nombreFeature(props))}</p>${
    d ? `<dl>${filas.map(([e, v]) => `<dt>${e}</dt><dd>${fmt.n(v)}</dd>`).join('')}</dl>`
      : `<p class="ficha__vacia">Sin registros de ${m.plural} con esta consulta.</p>`
  }${d ? `<div class="d-flex flex-wrap gap-2">${acciones}</div>` : ''}`;
  if ($('ficha').dataset.nota) $('ficha').insertAdjacentHTML('beforeend', `<p class="lamina__nota ficha__aviso">${escapar($('ficha').dataset.nota)}</p>`);
}

async function entrar(props) {
  if (vista.nivel === 'departamento') vista = { nivel: 'provincia', dep: props.ubigeo, prov: null };
  else if (vista.nivel === 'provincia') vista = { nivel: 'distrito', dep: vista.dep, prov: props.ubigeo };
  else return;
  await refrescar();
}

function pintarEscala() {
  const m = medida();
  const etiquetas = { departamento: 'departamento', provincia: 'provincia', distrito: 'distrito' };
  $('mapa-titulo').textContent = `${m.titulo} por ${etiquetas[vista.nivel]}`;
  const fila = (c, texto) => `<div class="escala__fila"><span class="escala__muestra" style="background:${c}"></span>${texto}</div>`;
  $('escala').innerHTML = clases.slice().reverse().map((c) =>
    fila(c.color, c.desde === c.hasta ? fmt.n(c.hasta) : `${fmt.n(c.desde)} a ${fmt.n(c.hasta)}`)).join('')
    + fila(color('sin-registros'), 'Sin registros');
}

async function pintarMiga() {
  const partes = [`<button type="button" data-nivel="pais">Perú</button>`];
  if (vista.dep) {
    const geo = await obtenerArchivo(ARCHIVOS.departamento);
    const f = geo.features.find((x) => x.properties.ubigeo === vista.dep);
    const nombre = nombrePropio(f?.properties.nombre_departamento ?? vista.dep);
    partes.push(vista.nivel === 'provincia' ? `<span aria-current="location">${escapar(nombre)}</span>` : `<button type="button" data-nivel="dep">${escapar(nombre)}</button>`);
  }
  if (vista.prov) {
    const geo = await obtenerArchivo(ARCHIVOS.provincia);
    const f = geo.features.find((x) => x.properties.ubigeo === vista.prov);
    partes.push(`<span aria-current="location">${escapar(nombrePropio(f?.properties.nombre_provincia ?? vista.prov))}</span>`);
  }
  if (vista.nivel === 'departamento') partes[0] = '<span aria-current="location">Perú</span>';
  $('miga').innerHTML = partes.join('<span aria-hidden="true">/</span>');
}

function tablaMapa() {
  const m = medida();
  const filas = [...datos.entries()].sort((a, b) => b[1][m.campo] - a[1][m.campo]);
  // Si la medida elegida ya es "emergencias", no se repite la columna
  const extra = m.campo !== 'emergencias';
  $('tabla-mapa').innerHTML = `<table class="tabla-datos"><thead><tr><th scope="col">Territorio</th><th scope="col">Ubigeo</th><th scope="col" class="num">${escapar(m.titulo)}</th>${
    extra ? '<th scope="col" class="num">Emergencias</th>' : ''}</tr></thead><tbody>${
    filas.map(([codigo, d]) => `<tr><td>${escapar(nombrePropio(d.nombre))}</td><td>${escapar(codigo)}</td><td class="num">${fmt.n(d[m.campo])}</td>${
      extra ? `<td class="num">${fmt.n(d.emergencias)}</td>` : ''}</tr>`).join('')
  }</tbody></table>`;
}

async function refrescar() {
  await cargarDatos();
  pintarEscala();
  await pintarMiga();
  $('ficha').innerHTML = '<p class="ficha__vacia">Pasa el cursor o toca un territorio para ver sus cifras. Haz clic para ver su interior.</p>';
  $('ficha').dataset.nota = '';
  await dibujarCapa();
  tablaMapa();
}

// --- API del módulo -------------------------------------------------------
export function iniciarMapa() {
  $('miga').addEventListener('click', async (e) => {
    const n = e.target.dataset?.nivel;
    if (n === 'pais') vista = { nivel: 'departamento', dep: null, prov: null };
    else if (n === 'dep') vista = { nivel: 'provincia', dep: vista.dep, prov: null };
    else return;
    await refrescar();
  });
  $('ficha').addEventListener('click', (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    if (b.dataset.filtroDep) aplicar({ departamento: b.dataset.filtroDep, provincia: '', distrito: '' });
    if (b.dataset.filtroProv) {
      const depNombre = nombresIndeciDeProvincia(b.dataset.filtroProv);
      depNombre.then((dep) => aplicar({ departamento: dep, provincia: b.dataset.filtroProv, distrito: '' }));
    }
    if (b.dataset.filtroDist) {
      const prov = b.dataset.filtroDist.slice(0, 4);
      nombresIndeciDeProvincia(prov).then((dep) => aplicar({ departamento: dep, provincia: prov, distrito: b.dataset.filtroDist }));
    }
  });
}

/** Nombre INDECI del departamento al que pertenece una provincia (resuelve Lima Metropolitana / Provincias). */
async function nombresIndeciDeProvincia(prov) {
  const deps = await obtenerUnaVez('deps', () => obtener('/catalogos/departamentos'));
  for (const d of deps.filter((x) => x.codDepartamento === prov.slice(0, 2))) {
    const provs = await obtenerUnaVez(`prov:${d.codigo}`, () => obtener('/catalogos/provincias', { departamento: d.codigo }));
    if (provs.some((p) => p.codigo === prov)) return d.codigo;
  }
  return '';
}

/** Se llama al mostrar la pestaña o al cambiar la consulta. */
export async function actualizarMapa({ visible }) {
  if (!visible) { pendiente = true; return; }
  if (!mapa) crearMapa();
  mapa.invalidateSize();
  pendiente = false;
  vista = await vistaDesdeConsulta();
  await refrescar();
}

export function mapaPendiente() { return pendiente || !mapa; }
export function redibujarMapa() { if (mapa && capa) refrescar(); }
