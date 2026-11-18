// La consulta escrita como frase: estado de los filtros, selectores en cascada y URL compartible.
import { obtener, obtenerUnaVez } from './api.js';
import { MEDIDAS, TIPOS, nombrePropio, oracion } from './formato.js';

const $ = (id) => document.getElementById(id);

export const estado = {
  medida: 'emergencias',
  departamento: '',
  provincia: '',
  distrito: '',
  anioDesde: '',
  anioHasta: '',
  peligro: '',        // valor del selector: "tipo:<TIPO>" o "peligro:<NOMBRE>" o ""
  regionNatural: '',
};

export let anios = []; // años con datos, en orden

const oyentes = new Set();
export function alCambiar(fn) { oyentes.add(fn); }
function avisar() { ajustarAnchos(); guardarEnUrl(); oyentes.forEach((fn) => fn(estado)); }

/**
 * Cada selector de la frase ocupa solo el ancho del texto elegido
 * (un <select> nativo toma el de su opción más larga y deja huecos).
 */
let medidor = null;
export function ajustarAnchos() {
  if (!medidor) {
    medidor = document.createElement('span');
    medidor.setAttribute('aria-hidden', 'true');
    medidor.style.cssText = 'position:absolute;visibility:hidden;white-space:pre;left:-9999px;top:0';
    document.body.append(medidor);
  }
  document.querySelectorAll('#frase .hueco').forEach((s) => {
    const cs = getComputedStyle(s);
    // Se copian una por una: el atajo "font" queda vacío con font-stretch en porcentaje
    for (const p of ['fontFamily', 'fontSize', 'fontWeight', 'fontStretch', 'fontStyle', 'letterSpacing']) {
      medidor.style[p] = cs[p];
    }
    medidor.textContent = s.options[s.selectedIndex]?.text ?? '';
    const extra = parseFloat(cs.paddingRight) + parseFloat(cs.paddingLeft) + 2;
    s.style.width = `${Math.ceil(medidor.getBoundingClientRect().width + extra)}px`;
  });
}
window.addEventListener('resize', () => ajustarAnchos());

/** Parámetros de filtro para la API. `omitir` quita claves (p. ej. para comparar con hermanos). */
export function parametros(omitir = []) {
  const p = {
    departamento: estado.departamento,
    provincia: estado.provincia,
    distrito: estado.distrito,
    regionNatural: estado.regionNatural,
  };
  if (estado.anioDesde && estado.anioDesde !== String(anios[0])) p.anioDesde = estado.anioDesde;
  if (estado.anioHasta && estado.anioHasta !== String(anios.at(-1))) p.anioHasta = estado.anioHasta;
  if (estado.peligro.startsWith('tipo:')) p.tipoPeligro = estado.peligro.slice(5);
  if (estado.peligro.startsWith('peligro:')) p.peligro = estado.peligro.slice(8);
  for (const k of omitir) delete p[k];
  return p;
}

export function medida() { return MEDIDAS[estado.medida]; }

export function hayFiltros() {
  return Object.keys(parametros()).some((k) => parametros()[k]);
}

export function peligroSeleccionado() {
  return estado.peligro.startsWith('peligro:') ? estado.peligro.slice(8) : null;
}

// ----------------------------------------------------------------------------

function opciones(select, lista, primera) {
  select.replaceChildren();
  select.add(new Option(primera, ''));
  for (const [valor, texto] of lista) select.add(new Option(texto, valor));
}

async function cargarProvincias() {
  const g = $('g-provincia');
  if (!estado.departamento) { g.hidden = true; return; }
  const lista = await obtenerUnaVez(`prov:${estado.departamento}`,
    () => obtener('/catalogos/provincias', { departamento: estado.departamento }));
  opciones($('f-provincia'), lista.map((t) => [t.codigo, nombrePropio(t.nombre)]), 'cualquiera');
  $('f-provincia').value = estado.provincia;
  g.hidden = false;
}

async function cargarDistritos() {
  const g = $('g-distrito');
  if (!estado.provincia) { g.hidden = true; return; }
  const lista = await obtenerUnaVez(`dist:${estado.provincia}`,
    () => obtener('/catalogos/distritos', { provincia: estado.provincia }));
  opciones($('f-distrito'), lista.map((t) => [t.codigo, nombrePropio(t.nombre)]), 'cualquiera');
  $('f-distrito').value = estado.distrito;
  g.hidden = false;
}

function leerUrl() {
  const q = new URLSearchParams(window.location.search);
  for (const k of Object.keys(estado)) if (q.has(k)) estado[k] = q.get(k);
  if (!MEDIDAS[estado.medida]) estado.medida = 'emergencias';
  // Enlaces escritos a mano: "peligro=SISMO" equivale a "peligro=peligro:SISMO"
  if (estado.peligro && !/^(tipo|peligro):/.test(estado.peligro)) estado.peligro = `peligro:${estado.peligro}`;
}

/** Asigna un valor al selector; si no existe (enlace con un valor inválido), vuelve a "cualquiera". */
function fijar(id, clave) {
  const sel = $(id);
  sel.value = estado[clave];
  if (sel.selectedIndex === -1) { estado[clave] = ''; sel.value = ''; }
}

function guardarEnUrl() {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(estado)) {
    if (v && !(k === 'medida' && v === 'emergencias')
        && !(k === 'anioDesde' && v === String(anios[0]))
        && !(k === 'anioHasta' && v === String(anios.at(-1)))) q.set(k, v);
  }
  const texto = q.toString();
  history.replaceState(null, '', `${location.pathname}${texto ? `?${texto}` : ''}${location.hash}`);
}

/** Cambia filtros desde otros módulos (mapa, ranking) y notifica. */
export async function aplicar(cambios) {
  Object.assign(estado, cambios);
  await sincronizarControles();
  avisar();
}

async function sincronizarControles() {
  $('f-medida').value = estado.medida;
  fijar('f-departamento', 'departamento');
  $('f-desde').value = estado.anioDesde;
  $('f-hasta').value = estado.anioHasta;
  fijar('f-peligro', 'peligro');
  fijar('f-region', 'regionNatural');
  await cargarProvincias();
  await cargarDistritos();
  ajustarAnchos();
}

export async function iniciarConsulta() {
  const [departamentos, peligros, regiones, porAnio] = await Promise.all([
    obtener('/catalogos/departamentos'),
    obtener('/catalogos/peligros'),
    obtener('/catalogos/regiones'),
    obtener('/estadisticas/por-anio'),
  ]);
  anios = porAnio.map((c) => Number(c.clave));

  opciones($('f-departamento'), departamentos.map((d) => [d.codigo, nombrePropio(d.nombre)]), 'todo el Perú');

  const selPeligro = $('f-peligro');
  selPeligro.replaceChildren(new Option('cualquier peligro', ''));
  for (const tipo of peligros) {
    const grupo = document.createElement('optgroup');
    grupo.label = TIPOS[tipo.tipo] ?? oracion(tipo.tipo);
    grupo.append(new Option(`cualquier peligro ${tipo.tipo === 'ORIGEN NATURAL' ? 'natural' : 'inducido por la acción humana'}`, `tipo:${tipo.tipo}`));
    // En minúscula: se lee dentro de la frase ("por lluvia intensa")
    for (const p of tipo.peligros) grupo.append(new Option(p.etiqueta.toLocaleLowerCase('es-PE'), `peligro:${p.valor}`));
    selPeligro.append(grupo);
  }

  opciones($('f-region'), regiones.map((r) => [r, `la ${oracion(r).toLocaleLowerCase('es-PE')}`]), 'cualquier región natural');

  for (const id of ['f-desde', 'f-hasta']) {
    $(id).replaceChildren(...anios.map((a) => new Option(String(a), String(a))));
  }

  leerUrl();
  if (!anios.includes(Number(estado.anioDesde))) estado.anioDesde = String(anios[0]);
  if (!anios.includes(Number(estado.anioHasta))) estado.anioHasta = String(anios.at(-1));
  await sincronizarControles();

  // --- eventos ---
  $('frase').addEventListener('submit', (e) => e.preventDefault());
  $('f-medida').addEventListener('change', (e) => { estado.medida = e.target.value; avisar(); });
  $('f-departamento').addEventListener('change', async (e) => {
    Object.assign(estado, { departamento: e.target.value, provincia: '', distrito: '' });
    await cargarProvincias(); await cargarDistritos(); avisar();
  });
  $('f-provincia').addEventListener('change', async (e) => {
    Object.assign(estado, { provincia: e.target.value, distrito: '' });
    await cargarDistritos(); avisar();
  });
  $('f-distrito').addEventListener('change', (e) => { estado.distrito = e.target.value; avisar(); });
  $('f-desde').addEventListener('change', (e) => {
    estado.anioDesde = e.target.value;
    if (Number(estado.anioDesde) > Number(estado.anioHasta)) { estado.anioHasta = estado.anioDesde; $('f-hasta').value = estado.anioHasta; }
    avisar();
  });
  $('f-hasta').addEventListener('change', (e) => {
    estado.anioHasta = e.target.value;
    if (Number(estado.anioHasta) < Number(estado.anioDesde)) { estado.anioDesde = estado.anioHasta; $('f-desde').value = estado.anioDesde; }
    avisar();
  });
  $('f-peligro').addEventListener('change', (e) => { estado.peligro = e.target.value; avisar(); });
  $('f-region').addEventListener('change', (e) => { estado.regionNatural = e.target.value; avisar(); });
  $('b-restablecer').addEventListener('click', () => aplicar({
    departamento: '', provincia: '', distrito: '', peligro: '', regionNatural: '',
    anioDesde: String(anios[0]), anioHasta: String(anios.at(-1)),
  }));
}

/** Descripción breve del ámbito actual, para títulos ("en Cusco, 2020–2025"). */
export function ambito() {
  const partes = [];
  const sel = (id) => { const s = $(id); return s.options[s.selectedIndex]?.text; };
  if (estado.distrito) partes.push(sel('f-distrito'));
  else if (estado.provincia) partes.push(`provincia de ${sel('f-provincia')}`);
  else if (estado.departamento) partes.push(sel('f-departamento'));
  else partes.push('todo el Perú');
  const desde = estado.anioDesde, hasta = estado.anioHasta;
  partes.push(desde === hasta ? desde : `${desde}–${hasta}`);
  return partes.join(', ');
}
