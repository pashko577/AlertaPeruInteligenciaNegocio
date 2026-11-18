// Formatos y vocabulario compartidos.

const numero = new Intl.NumberFormat('es-PE');
const decimal1 = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 1 });
const decimal2 = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 2 });
const compacto = new Intl.NumberFormat('es-PE', { notation: 'compact', maximumFractionDigits: 1 });
const porcentaje = new Intl.NumberFormat('es-PE', { style: 'percent', maximumFractionDigits: 0 });
const fechaLarga = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
const fechaCorta = new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' });

export const fmt = {
  n: (v) => numero.format(v),
  d1: (v) => decimal1.format(v),
  d2: (v) => decimal2.format(v),
  compacto: (v) => (Math.abs(v) >= 10000 ? compacto.format(v) : numero.format(v)),
  pct: (v) => porcentaje.format(v),
  fechaLarga: (iso) => fechaLarga.format(new Date(`${iso}T00:00:00Z`)),
  fechaCorta: (iso) => fechaCorta.format(new Date(`${iso}T00:00:00Z`)),
};

/** Medidas seleccionables en la frase. `campo` es el nombre en ConteoDto. */
export const MEDIDAS = {
  emergencias: { campo: 'emergencias', plural: 'emergencias', titulo: 'Emergencias' },
  damnificados: { campo: 'damnificados', plural: 'damnificados', titulo: 'Damnificados' },
  afectados: { campo: 'afectados', plural: 'afectados', titulo: 'Afectados' },
  fallecidos: { campo: 'fallecidos', plural: 'fallecidos', titulo: 'Fallecidos' },
  viviendasDestruidas: { campo: 'viviendasDestruidas', plural: 'viviendas destruidas', titulo: 'Viviendas destruidas' },
};

/** Nombre de los tipos de peligro tal como se muestran. */
export const TIPOS = {
  'ORIGEN NATURAL': 'De origen natural',
  'INDUCIDOS POR LA ACCIÓN HUMANA': 'Inducido por la acción humana',
};

/** "LLUVIA INTENSA" → "Lluvia intensa"; conserva siglas cortas. */
export function oracion(texto) {
  if (!texto) return '';
  const t = texto.toLocaleLowerCase('es-PE');
  return t.charAt(0).toLocaleUpperCase('es-PE') + t.slice(1);
}

/** "SAN JUAN DE LURIGANCHO" → "San Juan de Lurigancho". */
export function nombrePropio(texto) {
  if (!texto) return '';
  const menores = new Set(['de', 'del', 'la', 'las', 'los', 'el', 'y', 'e']);
  return texto.toLocaleLowerCase('es-PE').split(/(\s+|-|\()/).map((p, i) =>
    i > 0 && menores.has(p) ? p : p.charAt(0).toLocaleUpperCase('es-PE') + p.slice(1)).join('');
}

/**
 * Esquema de los indicadores del detalle (orden, etiqueta y unidad).
 * `periodo` marca los campos educativos que solo existen en un rango de años.
 */
export const ESQUEMA = [
  { grupo: 'personas', titulo: 'Personas', campos: [
    ['fallecidos', 'Fallecidos'], ['desaparecidos', 'Desaparecidos'], ['lesionados', 'Lesionados'],
    ['damnificados', 'Damnificados'], ['afectados', 'Afectados']] },
  { grupo: 'vivienda', titulo: 'Vivienda', campos: [
    ['destruidas', 'Viviendas destruidas'], ['afectadas', 'Viviendas afectadas']] },
  { grupo: 'educacion', titulo: 'Educación', campos: [
    ['centrosDestruidos', 'Centros educativos destruidos', { hasta: 2018 }],
    ['centrosAfectados', 'Centros educativos afectados', { hasta: 2018 }],
    ['centrosEvaluados', 'Centros educativos evaluados', { desde: 2019 }],
    ['aulasAfectadas', 'Aulas afectadas', { desde: 2019 }],
    ['aulasDestruidas', 'Aulas destruidas', { desde: 2019 }],
    ['aulasInhabitables', 'Aulas inhabitables', { desde: 2019 }],
    ['aulasDestruidasInhabitables', 'Aulas destruidas e inhabitables (suma)', { desde: 2019 }]],
    nota: 'Hasta 2018 se registraban centros educativos; desde 2019, aulas. No son comparables.' },
  { grupo: 'salud', titulo: 'Salud', campos: [
    ['centrosDestruidos', 'Establecimientos de salud destruidos'], ['centrosAfectados', 'Establecimientos de salud afectados']] },
  { grupo: 'agricultura', titulo: 'Agricultura', campos: [
    ['hasDestruidas', 'Cultivos destruidos', { unidad: 'ha' }], ['hasAfectadas', 'Cultivos afectados', { unidad: 'ha' }]] },
  { grupo: 'infraestructura', titulo: 'Infraestructura', campos: [
    ['puentesDestruidos', 'Puentes destruidos'], ['puentesAfectados', 'Puentes afectados'],
    ['carreteraDestruidaKm', 'Carretera destruida', { unidad: 'km' }],
    ['carreteraAfectadaKm', 'Carretera afectada', { unidad: 'km' }],
    ['caminoRuralDestruidoKm', 'Camino rural destruido', { unidad: 'km' }],
    ['caminoRuralAfectadoKm', 'Camino rural afectado', { unidad: 'km' }],
    ['canalColapsado', 'Canal de regadío colapsado'], ['canalAfectado', 'Canal de regadío afectado']],
    nota: 'Canales de regadío: la fuente no precisa si el valor está en metros, kilómetros o tramos.' },
  { grupo: 'ganaderia', titulo: 'Ganadería', campos: [
    ['vacunoPerdida', 'Vacunos perdidos'], ['vacunoAfectado', 'Vacunos afectados'],
    ['camelidoPerdida', 'Camélidos perdidos'], ['camelidoAfectado', 'Camélidos afectados'],
    ['caprinoPerdida', 'Caprinos perdidos'], ['caprinoAfectado', 'Caprinos afectados'],
    ['porcinoPerdida', 'Porcinos perdidos'], ['porcinoAfectado', 'Porcinos afectados'],
    ['menoresPerdida', 'Animales menores perdidos'], ['menoresAfectado', 'Animales menores afectados']] },
  { grupo: 'ayuda', titulo: 'Ayuda humanitaria', campos: [
    ['peso', 'Peso de la ayuda'], ['costo', 'Costo de la ayuda']],
    nota: 'Unidades no verificadas: el diccionario indica toneladas y miles de soles, pero las magnitudes no son coherentes con esas unidades.' },
];

export function escapar(texto) {
  return String(texto ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
