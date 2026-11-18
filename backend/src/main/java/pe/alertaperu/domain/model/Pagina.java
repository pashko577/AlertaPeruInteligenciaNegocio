package pe.alertaperu.domain.model;

import java.util.List;

/** Resultado paginado de una consulta. */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos) {

    public Pagina {
        contenido = List.copyOf(contenido);
    }

    public int totalPaginas() {
        return tamano == 0 ? 0 : (int) Math.ceil((double) totalElementos / tamano);
    }
}
