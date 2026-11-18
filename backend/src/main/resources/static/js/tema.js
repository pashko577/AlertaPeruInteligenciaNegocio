// Lee los colores del tema desde las variables CSS (claro u oscuro) y configura Chart.js.

export function color(nombre) {
  return getComputedStyle(document.documentElement).getPropertyValue(`--${nombre}`).trim();
}

export function rampa() {
  return [1, 2, 3, 4, 5, 6].map((i) => color(`rampa-${i}`));
}

export function configurarChart() {
  const C = window.Chart;
  C.defaults.font.family = '"Archivo", system-ui, sans-serif';
  C.defaults.font.size = 13;
  C.defaults.color = color('tinta-2');
  C.defaults.borderColor = color('reticula');
  C.defaults.animation.duration = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 350;
  C.defaults.maintainAspectRatio = false;
  C.defaults.plugins.legend.display = false;
  const t = C.defaults.plugins.tooltip;
  t.backgroundColor = color('superficie');
  t.titleColor = color('tinta');
  t.bodyColor = color('tinta-2');
  t.borderColor = color('linea');
  t.borderWidth = 1;
  t.padding = 10;
  t.cornerRadius = 6;
  t.displayColors = false;
  t.titleFont = { weight: '650', size: 13 };
  t.bodyFont = { size: 13 };
}

const CLAVE = 'alerta-tema';
const sistema = window.matchMedia('(prefers-color-scheme: dark)');
const guardado = () => { try { return localStorage.getItem(CLAVE); } catch { return null; } };

function aplicar(tema, alRedibujar) {
  document.documentElement.dataset.tema = tema;
  document.getElementById(`tema-${tema}`).checked = true;
  configurarChart();
  alRedibujar();
}

/**
 * Botón Claro/Oscuro. La elección se guarda en el navegador; mientras el usuario
 * no elija, la página sigue el esquema del sistema.
 */
export function iniciarTema(alRedibujar) {
  document.getElementById(`tema-${document.documentElement.dataset.tema}`).checked = true;
  document.querySelectorAll('input[name="tema"]').forEach((r) => r.addEventListener('change', () => {
    try { localStorage.setItem(CLAVE, r.value); } catch { /* sin almacenamiento: solo esta visita */ }
    aplicar(r.value, alRedibujar);
  }));
  sistema.addEventListener('change', (ev) => {
    if (!['claro', 'oscuro'].includes(guardado())) aplicar(ev.matches ? 'oscuro' : 'claro', alRedibujar);
  });
}
