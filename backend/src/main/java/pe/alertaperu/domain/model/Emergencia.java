package pe.alertaperu.domain.model;

import java.time.LocalDate;

/**
 * Registro de emergencia del SINPAD (una fila del CSV de INDECI).
 *
 * @param id           clave compuesta codigoSinpad-AAAA-MM-DD-ubigeo
 *                     (el código SINPAD solo no es único en la fuente)
 * @param codigoSinpad código original de la emergencia
 */
public record Emergencia(
        String id,
        String codigoSinpad,
        LocalDate fecha,
        Ubicacion ubicacion,
        Peligro peligro,
        Indicadores indicadores) {
}
