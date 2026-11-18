package pe.alertaperu.domain.model;

/**
 * Ubicación de una emergencia tal como la registra INDECI.
 *
 * @param ubigeo          código de distrito del INEI (6 dígitos, texto)
 * @param codDepartamento primeros 2 dígitos del ubigeo
 * @param codProvincia    primeros 4 dígitos del ubigeo
 * @param departamento    nombre INDECI (incluye LIMA METROPOLITANA y LIMA PROVINCIAS)
 * @param provincia       nombre original de la fila
 * @param distrito        nombre original de la fila
 * @param regionNatural   COSTA, SIERRA, SELVA ALTA o SELVA BAJA
 */
public record Ubicacion(
        String ubigeo,
        String codDepartamento,
        String codProvincia,
        String departamento,
        String provincia,
        String distrito,
        String regionNatural) {
}
