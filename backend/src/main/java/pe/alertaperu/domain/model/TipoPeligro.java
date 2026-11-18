package pe.alertaperu.domain.model;

import java.util.List;

/** Tipo de peligro con los peligros que agrupa (catálogo para los filtros). */
public record TipoPeligro(String tipo, List<String> peligros) {

    public TipoPeligro {
        peligros = List.copyOf(peligros);
    }
}
