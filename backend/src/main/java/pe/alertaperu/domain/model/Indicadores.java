package pe.alertaperu.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Indicadores numéricos de una emergencia, agrupados como en la fuente
 * (personas, vivienda, educacion, salud, agricultura, infraestructura,
 * ganaderia, ayuda).
 *
 * <p>Regla del proyecto: un campo ausente significa <b>"sin dato"</b> (la
 * celda venía vacía en el CSV) y nunca equivale a cero.</p>
 */
public record Indicadores(Map<String, Map<String, Number>> grupos) {

    public Indicadores {
        Map<String, Map<String, Number>> copia = new LinkedHashMap<>();
        if (grupos != null) {
            grupos.forEach((grupo, campos) ->
                    copia.put(grupo, Collections.unmodifiableMap(new LinkedHashMap<>(campos))));
        }
        grupos = Collections.unmodifiableMap(copia);
    }

    /** Valor de un indicador; vacío si la fuente no lo informa. */
    public Optional<Number> valor(String grupo, String campo) {
        return Optional.ofNullable(grupos.getOrDefault(grupo, Map.of()).get(campo));
    }

    public static Indicadores vacios() {
        return new Indicadores(Map.of());
    }
}
