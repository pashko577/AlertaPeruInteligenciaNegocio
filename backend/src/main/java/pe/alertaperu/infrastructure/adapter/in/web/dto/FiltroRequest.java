package pe.alertaperu.infrastructure.adapter.in.web.dto;

import pe.alertaperu.domain.model.FiltroEmergencias;

/**
 * Parámetros de filtro comunes recibidos por query string.
 * La validación de reglas la hace el dominio ({@link FiltroEmergencias}).
 */
public record FiltroRequest(
        Integer anioDesde,
        Integer anioHasta,
        String departamento,
        String provincia,
        String distrito,
        String regionNatural,
        String tipoPeligro,
        String peligro) {

    public FiltroEmergencias aDominio() {
        return new FiltroEmergencias(anioDesde, anioHasta, departamento, provincia, distrito,
                regionNatural, tipoPeligro, peligro);
    }
}
