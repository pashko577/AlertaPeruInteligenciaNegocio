// Punto de entrada: pestañas, carga inicial y coordinación entre secciones.
import { obtener } from './api.js';
import { alCambiar, iniciarConsulta } from './consulta.js';
import { fmt } from './formato.js';
import { actualizarMapa, iniciarMapa, mapaPendiente, redibujarMapa } from './mapa.js';
import { activarAlternadores, actualizarPanorama, redibujarPanorama } from './panorama.js';
import { actualizarRegistros, iniciarRegistros } from './registros.js';
import { configurarChart, iniciarTema } from './tema.js';

const $ = (id) => document.getElementById(id);
const PESTANAS = ['panorama', 'mapa', 'registros', 'acerca'];
let activa = 'panorama';
let registrosPendientes = true;

// --- Errores visibles y recuperables ------------------------------------
function mostrarError(e) {
  console.error(e);
  $('aviso-texto').textContent = e?.message ?? 'Ocurrió un error inesperado.';
  $('aviso-error').hidden = false;
  document.body.classList.remove('actualizando');
}
function ocultarError() { $('aviso-error').hidden = true; }

// --- Pestañas (ARIA tabs con teclado) -----------------------------------
function mostrarPestana(nombre, { foco = false } = {}) {
  activa = nombre;
  for (const p of PESTANAS) {
    const tab = $(`t-${p}`);
    const sel = p === nombre;
    tab.setAttribute('aria-selected', String(sel));
    tab.tabIndex = sel ? 0 : -1;
    $(`p-${p}`).hidden = !sel;
    if (sel && foco) tab.focus();
  }
  history.replaceState(null, '', `${location.pathname}${location.search}${nombre === 'panorama' ? '' : `#${nombre}`}`);
  if (nombre === 'mapa' && mapaPendiente()) actualizarMapa({ visible: true }).catch(mostrarError);
  if (nombre === 'registros' && registrosPendientes) { registrosPendientes = false; actualizarRegistros({ reiniciar: true }).catch(mostrarError); }
}

function iniciarPestanas() {
  const lista = document.querySelector('.pestanas');
  lista.addEventListener('click', (e) => {
    const tab = e.target.closest('[role="tab"]');
    if (tab) mostrarPestana(tab.id.slice(2));
  });
  lista.addEventListener('keydown', (e) => {
    const i = PESTANAS.indexOf(activa);
    const destinos = { ArrowRight: i + 1, ArrowLeft: i - 1, Home: 0, End: PESTANAS.length - 1 };
    if (!(e.key in destinos)) return;
    e.preventDefault();
    mostrarPestana(PESTANAS[(destinos[e.key] + PESTANAS.length) % PESTANAS.length], { foco: true });
  });
}

// --- Curvas de nivel de la banda (dibujadas una vez, deterministas) -----
function dibujarCurvas() {
  const svg = document.querySelector('.banda__curvas');
  const ancho = 1600, alto = 220;
  svg.setAttribute('viewBox', `0 0 ${ancho} ${alto}`);
  svg.setAttribute('preserveAspectRatio', 'xMaxYMid slice');
  const cumbres = [{ x: 1180, y: 150, r: 26, n: 11 }, { x: 1460, y: 40, r: 20, n: 8 }, { x: 860, y: 230, r: 22, n: 7 }];
  let paths = '';
  for (const [k, c] of cumbres.entries()) {
    for (let i = 1; i <= c.n; i++) {
      const radio = c.r * i;
      let d = '';
      for (let a = 0; a <= 64; a++) {
        const t = (a / 64) * Math.PI * 2;
        const ondula = 1 + 0.12 * Math.sin(3 * t + k + i * 0.35) + 0.07 * Math.sin(5 * t + i * 0.6 + k * 2) + 0.04 * Math.sin(9 * t + i);
        const x = c.x + Math.cos(t) * radio * 1.55 * ondula;
        const y = c.y + Math.sin(t) * radio * 0.8 * ondula;
        d += `${a ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`;
      }
      paths += `<path class="${i % 5 === 0 ? 'maestra' : ''}" d="${d}Z"/>`;
    }
  }
  svg.innerHTML = paths;
}

// --- Actualización coordinada al cambiar la consulta --------------------
async function actualizarTodo() {
  ocultarError();
  document.body.classList.add('actualizando');   // conserva la vista anterior atenuada, sin saltos
  try {
    registrosPendientes = activa !== 'registros';
    await Promise.all([
      actualizarPanorama(),
      actualizarMapa({ visible: activa === 'mapa' }),
      activa === 'registros' ? actualizarRegistros({ reiniciar: true }) : Promise.resolve(),
    ]);
  } catch (e) {
    mostrarError(e);
  } finally {
    document.body.classList.remove('actualizando');
  }
}

async function cargarInfo() {
  try {
    const i = await obtener('/metadatos/ultima-carga');
    const fecha = new Intl.DateTimeFormat('es-PE', { dateStyle: 'long', timeStyle: 'short', timeZone: 'America/Lima' }).format(new Date(i.fechaCarga));
    $('acerca-carga').textContent = `Última carga: ${fecha} (hora de Lima), desde el archivo ${i.archivo}. Se leyeron ${fmt.n(i.filasLeidas)} filas y la base contiene ${fmt.n(i.documentos)} registros.`;
  } catch {
    $('acerca-carga').textContent = 'No se pudo consultar la fecha de la última carga.';
  }
}

async function iniciar() {
  dibujarCurvas();
  iniciarPestanas();
  activarAlternadores();
  iniciarMapa();
  iniciarRegistros(mostrarError);
  $('b-reintentar').addEventListener('click', () => arrancar());
  await document.fonts.ready;
  configurarChart();
  iniciarTema(() => { redibujarPanorama(); redibujarMapa(); });
  alCambiar(() => actualizarTodo());
  await arrancar();
}

let iniciado = false;
async function arrancar() {
  ocultarError();
  try {
    if (!iniciado) { await iniciarConsulta(); iniciado = true; }
    const inicial = location.hash.slice(1);
    if (PESTANAS.includes(inicial)) mostrarPestana(inicial);
    await actualizarTodo();
    cargarInfo();
  } catch (e) {
    mostrarError(e);
  }
}

iniciar();
