package pe.alertaperu.domain.model;

import java.util.Locale;

import pe.alertaperu.domain.exception.FiltroInvalidoException;

/** Niveles de la dimensión territorio. */
public enum NivelTerritorial {
    DEPARTAMENTO, PROVINCIA, DISTRITO;

    public static NivelTerritorial desde(String texto) {
        if (texto == null || texto.isBlank()) {
            return DEPARTAMENTO;
        }
        try {
            return valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new FiltroInvalidoException(
                    "nivel inválido: '" + texto + "'. Use departamento, provincia o distrito");
        }
    }
}
