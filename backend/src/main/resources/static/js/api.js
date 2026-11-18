// Cliente de la API REST del backend (mismo origen).

export class ErrorApi extends Error {
  constructor(mensaje, estado) {
    super(mensaje);
    this.estado = estado;
  }
}

function construirUrl(ruta, parametros = {}) {
  const url = new URL(`/api${ruta}`, window.location.origin);
  for (const [clave, valor] of Object.entries(parametros)) {
    if (valor !== null && valor !== undefined && valor !== '') url.searchParams.set(clave, valor);
  }
  return url;
}

export async function obtener(ruta, parametros) {
  let respuesta;
  try {
    respuesta = await fetch(construirUrl(ruta, parametros), { headers: { Accept: 'application/json' } });
  } catch {
    throw new ErrorApi('No se pudo conectar con el servidor. Comprueba que el backend esté en marcha y vuelve a intentarlo.', 0);
  }
  if (!respuesta.ok) {
    let mensaje = `El servidor respondió con el código ${respuesta.status}.`;
    try {
      const cuerpo = await respuesta.json();
      if (cuerpo?.mensaje) mensaje = cuerpo.mensaje;
    } catch { /* respuesta sin JSON */ }
    throw new ErrorApi(mensaje, respuesta.status);
  }
  return respuesta.json();
}

// Caché simple para recursos estáticos (GeoJSON, catálogos)
const cache = new Map();
export function obtenerUnaVez(clave, cargador) {
  if (!cache.has(clave)) {
    cache.set(clave, cargador().catch((e) => { cache.delete(clave); throw e; }));
  }
  return cache.get(clave);
}

export async function obtenerArchivo(ruta) {
  return obtenerUnaVez(ruta, async () => {
    const r = await fetch(ruta);
    if (!r.ok) throw new ErrorApi(`No se pudo cargar ${ruta}`, r.status);
    return r.json();
  });
}
