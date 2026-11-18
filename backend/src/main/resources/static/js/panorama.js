// Panorama: respuesta principal, cifras, serie anual, peligros y ranking territorial.
import { obtener } from './api.js';
import { anios, aplicar, estado, hayFiltros, medida, parametros, peligroSeleccionado } from './consulta.js';
import { escapar, fmt, nombrePropio, oracion, TIPOS } from './formato.js';
import { color } from './tema.js';

const $ = (id) => document.getElementById(id);
const graficos = {};
let ultimo = null; // últimos datos, para redibujar al cambiar el tema

// --- Etiquetas directas selectivas (solo las barras indicadas) -------------
const etiquetasSelectivas = {
  id: 'etiquetasSelectivas',
  afterDatasetsDraw(chart, _args, opciones) {
    const indices = opciones?.indices;
    if (!indices?.length) return;
    const { ctx } = chart;
    const horizontal = chart.options.indexAxis === 'y';
    const meta = chart.getDatasetMeta(0);
    ctx.save();
    ctx.font = `600 12px ${window.Chart.defaults.font.family}`;
    ctx.fillStyle = color('tinta');
    for (const i of indices) {
      const barra = meta.data[i];
      const valor = chart.data.datasets[0].data[i];
      if (!barra || !valor) continue;
      const texto = fmt.compacto(valor);
      if (horizontal) {
        ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
        ctx.fillText(texto, barra.x + 6, barra.y);
      } else {
        ctx.textAlign = 'center'; ctx.textBaseline = 'bottom';
        ctx.fillText(texto, barra.x, barra.y - 5);
      }
    }
    ctx.restore();
  },
};

function ejesComunes(horizontal) {
  const valor = {
    beginAtZero: true,
    grid: { color: color('reticula'), drawTicks: false },
    border: { display: false },
    ticks: { color: color('tenue'), padding: 6, callback: (v) => fmt.compacto(v), maxTicksLimit: 6 },
  };
  const categoria = {
    grid: { display: false },
    border: { color: color('eje') },
    ticks: { color: color('tinta-2'), padding: 4, autoSkip: !horizontal },
  };
  return horizontal ? { x: valor, y: categoria } : { x: categoria, y: valor };
}

function dibujar(id, config) {
  graficos[id]?.destroy();
  graficos[id] = new window.Chart($(id), config);
}

// --- Respuesta principal (la cifra protagonista) ---------------------------
function pintarRespuesta(resumen) {
  const m = medida();
  const t = resumen.totales;
  const cifra = $('r-cifra');
  const texto = $('r-texto');
  if (t.emergencias === 0) {
    cifra.textContent = 'Sin registros';
    texto.textContent = 'No hay emergencias registradas con estos criterios. Prueba con un periodo más amplio, otro territorio u otro peligro.';
  } else if (estado.medida === 'emergencias') {
    cifra.innerHTML = `${fmt.n(t.emergencias)} <small>emergencias registradas</small>`;
  } else {
    const valor = t[m.campo];
    cifra.innerHTML = `${fmt.n(valor)} <small>${m.plural}, en ${fmt.n(t.emergencias)} emergencias registradas</small>`;
  }
  if (t.emergencias > 0) {
    const frases = [];
    if (resumen.peligroMasFrecuente) frases.push(`El peligro más frecuente fue ${oracion(resumen.peligroMasFrecuente).toLocaleLowerCase('es-PE')}.`);
    frases.push(`En promedio, cada emergencia dejó ${fmt.d1(resumen.damnificadosPorEmergencia)} damnificados.`);
    const v = resumen.variacionInteranual;
    if (v !== null && v !== undefined && Number.isFinite(v)) {
      const anio = resumen.ultimoAnio;
      if (Math.abs(v) < 0.005) frases.push(`En ${anio} se registraron tantas emergencias como en ${anio - 1}.`);
      else frases.push(`En ${anio} se registraron ${fmt.pct(Math.abs(v))} ${v > 0 ? 'más' : 'menos'} emergencias que en ${anio - 1}.`);
    }
    texto.textContent = frases.join(' ');
  }
  $('b-restablecer').hidden = !hayFiltros();
}

// --- Cifras secundarias ---------------------------------------------------
function pintarCifras(t) {
  const filas = [
    ['Fallecidos', t.fallecidos],
    ['Desaparecidos', t.desaparecidos],
    ['Lesionados', t.lesionados],
    ['Damnificados', t.damnificados],
    ['Afectados', t.afectados],
    ['Viviendas destruidas', t.viviendasDestruidas],
    ['Viviendas afectadas', t.viviendasAfectadas],
  ];
  $('cifras').innerHTML = filas.map(([e, v]) =>
    `<div class="cifra"><dt>${e}</dt><dd>${fmt.n(v)}</dd></div>`).join('');
}

// --- Serie anual -----------------------------------------------------------
function pintarAnual(porAnio) {
  const m = medida();
  const desde = Number(estado.anioDesde), hasta = Number(estado.anioHasta);
  const rango = anios.filter((a) => a >= desde && a <= hasta);
  const mapa = new Map(porAnio.map((c) => [Number(c.clave), c[m.campo]]));
  const valores = rango.map((a) => mapa.get(a) ?? 0);
  $('anual-titulo').textContent = `${m.titulo} por año`;

  const maximo = valores.indexOf(Math.max(...valores));
  const indices = [...new Set([maximo, valores.length - 1])].filter((i) => i >= 0);
  dibujar('c-anual', {
    type: 'bar',
    data: { labels: rango.map(String), datasets: [{
      data: valores, backgroundColor: color('serie-1'),
      borderRadius: 4, borderSkipped: 'start', maxBarThickness: 24, categoryPercentage: .78, barPercentage: .92,
    }] },
    options: {
      layout: { padding: { top: 22 } },
      scales: ejesComunes(false),
      plugins: {
        etiquetasSelectivas: { indices },
        tooltip: { callbacks: { title: (i) => i[0].label, label: (i) => `${fmt.n(i.raw)} ${m.plural}` } },
      },
    },
    plugins: [etiquetasSelectivas],
  });
  tablaGemela('tabla-anual', ['Año', m.titulo], rango.map((a, i) => [a, valores[i]]));
}

// --- Peligros (barras horizontales coloreadas por tipo) --------------------
function pintarPeligros(porPeligro) {
  const m = medida();
  const seleccionado = peligroSeleccionado();
  const filas = [...porPeligro].sort((a, b) => b[m.campo] - a[m.campo]).filter((c) => c[m.campo] > 0 || c.emergencias > 0);
  const colorTipo = (tipo) => (tipo === 'ORIGEN NATURAL' ? color('serie-1') : color('serie-2'));
  const colores = filas.map((c) => (seleccionado && c.clave !== seleccionado ? color('atenuado') : colorTipo(c.codigo)));
  $('peligro-titulo').textContent = `${m.titulo} por peligro`;
  $('caja-peligro').style.height = `${Math.max(140, filas.length * 26 + 40)}px`;

  const tipos = [...new Set(filas.map((c) => c.codigo))];
  $('leyenda-peligro').innerHTML = tipos.map((t) =>
    `<li><i style="background:${colorTipo(t)}"></i>${escapar(TIPOS[t] ?? oracion(t))}</li>`).join('');

  dibujar('c-peligro', {
    type: 'bar',
    data: { labels: filas.map((c) => oracion(c.nombre)), datasets: [{
      data: filas.map((c) => c[m.campo]), backgroundColor: colores,
      borderRadius: 4, borderSkipped: 'start', maxBarThickness: 18, categoryPercentage: .8, barPercentage: .9,
    }] },
    options: {
      indexAxis: 'y',
      layout: { padding: { right: 48 } },
      scales: ejesComunes(true),
      onClick: (_e, el) => {
        if (!el.length) return;
        const clave = filas[el[0].index].clave;
        aplicar({ peligro: seleccionado === clave ? '' : `peligro:${clave}` });
      },
      onHover: (e, el) => { e.native.target.style.cursor = el.length ? 'pointer' : 'default'; },
      plugins: {
        etiquetasSelectivas: { indices: [0, 1, 2].filter((i) => i < filas.length) },
        tooltip: { callbacks: {
          label: (i) => `${fmt.n(i.raw)} ${m.plural}`,
          afterLabel: (i) => TIPOS[filas[i.dataIndex].codigo] ?? '',
          footer: () => 'Clic para filtrar por este peligro',
        } },
      },
    },
    plugins: [etiquetasSelectivas],
  });
  tablaGemela('tabla-peligro', ['Peligro', 'Tipo', m.titulo],
    filas.map((c) => [oracion(c.nombre), TIPOS[c.codigo] ?? c.codigo, c[m.campo]]));
}

// --- Ranking territorial ---------------------------------------------------
/** Nivel del ranking según la consulta: compara hermanos del territorio elegido. */
function nivelRanking() {
  if (estado.provincia) return { nivel: 'distrito', omitir: ['distrito'], titulo: 'distrito', resaltar: estado.distrito };
  if (estado.departamento) return { nivel: 'provincia', omitir: [], titulo: 'provincia', resaltar: null };
  return { nivel: 'departamento', omitir: [], titulo: 'departamento', resaltar: null };
}

let filasTerritorio = [];
function pintarTerritorio(conteos, cfg) {
  const m = medida();
  const ordenadas = [...conteos].sort((a, b) => b[m.campo] - a[m.campo]);
  const visibles = ordenadas.slice(0, 20);
  filasTerritorio = visibles;
  $('territorio-titulo').textContent = `${m.titulo} por ${cfg.titulo}`;
  $('territorio-nota').textContent = ordenadas.length > visibles.length
    ? `Se muestran los ${visibles.length} con más ${m.plural} de ${ordenadas.length}. La tabla incluye todos.`
    : (cfg.resaltar ? 'El distrito elegido aparece resaltado frente a los demás de su provincia.' : '');
  $('caja-territorio').style.height = `${Math.max(140, visibles.length * 26 + 40)}px`;
  const colores = visibles.map((c) => (cfg.resaltar && c.clave !== cfg.resaltar ? color('atenuado') : color('serie-1')));

  dibujar('c-territorio', {
    type: 'bar',
    data: { labels: visibles.map((c) => nombrePropio(c.nombre)), datasets: [{
      data: visibles.map((c) => c[m.campo]), backgroundColor: colores,
      borderRadius: 4, borderSkipped: 'start', maxBarThickness: 18, categoryPercentage: .8, barPercentage: .9,
    }] },
    options: {
      indexAxis: 'y',
      layout: { padding: { right: 48 } },
      scales: ejesComunes(true),
      onClick: (_e, el) => { if (el.length) filtrarTerritorio(cfg.nivel, filasTerritorio[el[0].index]); },
      onHover: (e, el) => { e.native.target.style.cursor = el.length ? 'pointer' : 'default'; },
      plugins: {
        etiquetasSelectivas: { indices: [0, 1, 2].filter((i) => i < visibles.length) },
        tooltip: { callbacks: {
          label: (i) => `${fmt.n(i.raw)} ${m.plural}`,
          footer: () => (cfg.nivel === 'distrito' ? 'Clic para ver este distrito' : 'Clic para ver su interior'),
        } },
      },
    },
    plugins: [etiquetasSelectivas],
  });
  tablaGemela('tabla-territorio', [oracion(cfg.titulo), m.titulo],
    ordenadas.map((c) => [nombrePropio(c.nombre), c[m.campo]]));
}

function filtrarTerritorio(nivel, conteo) {
  if (nivel === 'departamento') aplicar({ departamento: conteo.clave, provincia: '', distrito: '' });
  else if (nivel === 'provincia') aplicar({ provincia: conteo.clave, distrito: '' });
  else aplicar({ distrito: estado.distrito === conteo.clave ? '' : conteo.clave });
}

// --- Tablas gemelas (accesibles; misma información que el gráfico) ---------
function tablaGemela(id, cabeceras, filas) {
  const num = (v) => typeof v === 'number';
  $(id).innerHTML = `<table class="tabla-datos"><thead><tr>${cabeceras.map((c, i) =>
    `<th scope="col" class="${i === cabeceras.length - 1 ? 'num' : ''}">${escapar(c)}</th>`).join('')}</tr></thead><tbody>${
    filas.map((f) => `<tr>${f.map((v, i) => `<td class="${num(v) && i > 0 ? 'num' : ''}">${num(v) && i > 0 ? fmt.n(v) : escapar(v)}</td>`).join('')}</tr>`).join('')
  }</tbody></table>`;
}

export function activarAlternadores() {
  document.querySelectorAll('[data-alterna]').forEach((boton) => {
    boton.addEventListener('click', () => {
      const clave = boton.dataset.alterna;
      const tabla = $(`tabla-${clave}`);
      const grafico = clave === 'mapa' ? document.querySelector('.mapa-disposicion') : boton.closest('figure')?.querySelector('.lamina__grafico');
      const verTabla = tabla.hidden;
      tabla.hidden = !verTabla;
      if (grafico && clave !== 'mapa') grafico.hidden = verTabla;
      boton.textContent = verTabla ? (clave === 'mapa' ? 'Ocultar tabla' : 'Ver gráfico') : 'Ver como tabla';
    });
  });
}

// --- Orquestación ----------------------------------------------------------
export async function actualizarPanorama() {
  const p = parametros();
  const cfg = nivelRanking();
  const [resumen, porAnio, porPeligro, porTerritorio] = await Promise.all([
    obtener('/estadisticas/resumen', p),
    obtener('/estadisticas/por-anio', p),
    obtener('/estadisticas/por-peligro', parametros(['peligro'])),
    obtener('/estadisticas/por-territorio', { ...parametros(cfg.omitir), nivel: cfg.nivel }),
  ]);
  ultimo = { resumen, porAnio, porPeligro, porTerritorio, cfg };
  redibujarPanorama();
}

export function redibujarPanorama() {
  if (!ultimo) return;
  pintarRespuesta(ultimo.resumen);
  pintarCifras(ultimo.resumen.totales);
  pintarAnual(ultimo.porAnio);
  pintarPeligros(ultimo.porPeligro);
  pintarTerritorio(ultimo.porTerritorio, ultimo.cfg);
}
