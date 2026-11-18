package pe.alertaperu.domain.model;

import pe.alertaperu.domain.exception.FiltroInvalidoException;

/**
 * Página solicitada de un listado.
 *
 * @param pagina      número de página, empezando en 0
 * @param tamano      registros por página (1 a {@value #TAMANO_MAXIMO})
 * @param orden       campo de ordenamiento
 * @param descendente true para orden descendente
 */
public record Paginacion(int pagina, int tamano, CampoOrden orden, boolean descendente) {

    public static final int TAMANO_MAXIMO = 100;
    public static final int TAMANO_POR_DEFECTO = 20;

    /** Campos por los que se permite ordenar (nunca expresiones libres del cliente). */
    public enum CampoOrden {
        FECHA, DEPARTAMENTO, PELIGRO, FALLECIDOS, DAMNIFICADOS, AFECTADOS, VIVIENDAS_DESTRUIDAS
    }

    public Paginacion {
        if (pagina < 0) {
            throw new FiltroInvalidoException("la página no puede ser negativa");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new FiltroInvalidoException("el tamaño de página debe estar entre 1 y " + TAMANO_MAXIMO);
        }
        if (orden == null) {
            orden = CampoOrden.FECHA;
        }
    }

    public long desplazamiento() {
        return (long) pagina * tamano;
    }
}
