// Registros: tabla paginada desde el servidor y detalle de cada emergencia.
import { obtener } from './api.js';
import { ambito, parametros } from './consulta.js';
import { ESQUEMA, escapar, fmt, nombrePropio, oracion } from './formato.js';

const $ = (id) => document.getElementById(id);
const pag = { page: 0, size: 20, sort: 'fecha', dir: 'desc', total: 0, totalPaginas: 0 };
let modal = null;

const SIN_DATO = '<span class="sin-dato">sin dato</span>';
const numero = (v) => (v === null || v === undefined ? SIN_DATO : fmt.n(v));

// Texto del sentido del orden según el tipo de campo
const TEXTO = new Set(['departamento', 'peligro']);
function sentidos(campo) {
  if (campo === 'fecha') return { desc: 'más recientes primero', asc: 'más antiguas primero' };
  if (TEXTO.has(campo)) return { asc: 'de la A a la Z', desc: 'de la Z a la A' };
  return { desc: 'de mayor a menor', asc: 'de menor a mayor' };
}
const sentidoPorDefecto = (campo) => (TEXTO.has(campo) ? 'asc' : 'desc');

function pintarCabeceras() {
  document.querySelectorAll('#tabla-registros thead th').forEach((th) => {
    const b = th.querySelector('button');
    if (b && b.dataset.orden === pag.sort) th.setAttribute('aria-sort', pag.dir === 'desc' ? 'descending' : 'ascending');
    else th.removeAttribute('aria-sort');
  });
  // Control "Ordenar por" sincronizado con los títulos de columna
  $('reg-orden').value = pag.sort;
  const s = sentidos(pag.sort);
  const dir = $('reg-dir');
  const orden = TEXTO.has(pag.sort) ? ['asc', 'desc'] : ['desc', 'asc'];
  dir.replaceChildren(...orden.map((v) => new Option(s[v], v)));
  dir.value = pag.dir;
}

function ordenar(campo, dir) {
  pag.sort = campo;
  pag.dir = dir ?? sentidoPorDefecto(campo);
  return actualizarRegistros({ reiniciar: true });
}

function pintarFilas(pagina) {
  const cuerpo = $('tabla-registros').querySelector('tbody');
  if (!pagina.contenido.length) {
    cuerpo.innerHTML = '<tr><td colspan="7" class="sin-dato">No hay registros con estos criterios. Amplía el periodo o quita algún filtro.</td></tr>';
    return;
  }
  cuerpo.innerHTML = pagina.contenido.map((e) => `
    <tr tabindex="0" data-id="${escapar(e.id)}" aria-label="Ver detalle: ${escapar(oracion(e.peligro))} en ${escapar(nombrePropio(e.distrito))}, ${escapar(fmt.fechaLarga(e.fecha))}">
      <td class="num" style="text-align:left">${escapar(fmt.fechaCorta(e.fecha))}</td>
      <td class="lugar">${escapar(nombrePropio(e.distrito))}<small>${escapar(nombrePropio(e.provincia))}, ${escapar(nombrePropio(e.departamento))}</small></td>
      <td>${escapar(oracion(e.peligro))}</td>
      <td class="num">${numero(e.fallecidos)}</td>
      <td class="num">${numero(e.damnificados)}</td>
      <td class="num">${numero(e.afectados)}</td>
      <td class="num">${numero(e.viviendasDestruidas)}</td>
    </tr>`).join('');
}

function pintarPaginacion() {
  $('reg-pagina').textContent = pag.totalPaginas
    ? `Página ${fmt.n(pag.page + 1)} de ${fmt.n(pag.totalPaginas)}`
    : '';
  $('reg-anterior').disabled = pag.page === 0;
  $('reg-siguiente').disabled = pag.page + 1 >= pag.totalPaginas;
}

export async function actualizarRegistros({ reiniciar = false } = {}) {
  if (reiniciar) pag.page = 0;
  const r = await obtener('/emergencias', { ...parametros(), page: pag.page, size: pag.size, sort: pag.sort, dir: pag.dir });
  pag.total = r.totalElementos;
  pag.totalPaginas = r.totalPaginas;
  const campo = $('reg-orden').querySelector(`option[value="${pag.sort}"]`)?.text ?? pag.sort;
  $('reg-resumen').textContent = `${fmt.n(r.totalElementos)} registros en ${ambito()}, ordenados por ${campo} (${sentidos(pag.sort)[pag.dir]}). Selecciona una fila para ver todos sus datos.`;
  pintarCabeceras();
  pintarFilas(r);
  pintarPaginacion();
}

// --- Detalle --------------------------------------------------------------
function valorDetalle(valor, unidad) {
  if (valor === null || valor === undefined) return SIN_DATO;
  const texto = Number.isInteger(valor) ? fmt.n(valor) : fmt.d2(valor);
  return unidad ? `${texto} ${unidad}` : texto;
}

async function abrirDetalle(id) {
  const e = await obtener(`/emergencias/${encodeURIComponent(id)}`);
  const anio = Number(e.fecha.slice(0, 4));
  $('detalle-titulo').textContent = `${oracion(e.peligro.etiqueta)}, ${fmt.fechaLarga(e.fecha)}`;
  const u = e.ubicacion;
  $('detalle-lugar').textContent = `${nombrePropio(u.distrito)}, ${nombrePropio(u.provincia)}, ${nombrePropio(u.departamento)}. Región ${oracion(u.regionNatural).toLocaleLowerCase('es-PE')}.`;

  const grupos = ESQUEMA.map((g) => {
    const valores = e.indicadores[g.grupo] ?? {};
    const filas = g.campos.map(([campo, etiqueta, extra = {}]) => {
      const fuera = (extra.hasta && anio > extra.hasta) || (extra.desde && anio < extra.desde);
      const v = valores[campo];
      const celda = (v === undefined || v === null) && fuera
        ? `<span class="sin-dato">${extra.hasta ? `solo hasta ${extra.hasta}` : `desde ${extra.desde}`}</span>`
        : valorDetalle(v, extra.unidad);
      return `<dt>${escapar(etiqueta)}</dt><dd>${celda}</dd>`;
    }).join('');
    return `<section class="detalle__grupo"><h3>${escapar(g.titulo)}</h3><dl>${filas}</dl>${
      g.nota ? `<p class="detalle__nota">${escapar(g.nota)}</p>` : ''}</section>`;
  }).join('');

  $('detalle-cuerpo').innerHTML = `${grupos}<p class="detalle__ident">Código SINPAD ${escapar(e.codigoSinpad)}. Ubigeo ${escapar(u.ubigeo)}. Tipo de peligro: ${escapar(oracion(e.peligro.tipo))}.</p>`;
  modal ??= new window.bootstrap.Modal($('modal-detalle'));
  modal.show();
}

export function iniciarRegistros(alError) {
  $('tabla-registros').querySelector('thead').addEventListener('click', (ev) => {
    const b = ev.target.closest('button[data-orden]');
    if (!b) return;
    const campo = b.dataset.orden;
    // Mismo campo: invierte el sentido. Campo nuevo: su sentido por defecto.
    const dir = pag.sort === campo ? (pag.dir === 'desc' ? 'asc' : 'desc') : undefined;
    ordenar(campo, dir).catch(alError);
  });
  $('reg-orden').addEventListener('change', (ev) => ordenar(ev.target.value).catch(alError));
  $('reg-dir').addEventListener('change', (ev) => ordenar(pag.sort, ev.target.value).catch(alError));
  pintarCabeceras();
  const cuerpo = $('tabla-registros').querySelector('tbody');
  const abrir = (tr) => tr?.dataset.id && abrirDetalle(tr.dataset.id).catch(alError);
  cuerpo.addEventListener('click', (ev) => abrir(ev.target.closest('tr')));
  cuerpo.addEventListener('keydown', (ev) => {
    if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); abrir(ev.target.closest('tr')); }
  });
  $('reg-anterior').addEventListener('click', () => { pag.page -= 1; actualizarRegistros().catch(alError); });
  $('reg-siguiente').addEventListener('click', () => { pag.page += 1; actualizarRegistros().catch(alError); });
  $('reg-tamano').addEventListener('change', (ev) => { pag.size = Number(ev.target.value); actualizarRegistros({ reiniciar: true }).catch(alError); });
}
