package pe.alertaperu.domain.model;

/**
 * Elemento del catálogo de territorios.
 *
 * <ul>
 *   <li>Departamento: {@code codigo} = nombre INDECI (p. ej. "LIMA PROVINCIAS"),
 *       {@code codDepartamento} = 2 dígitos.</li>
 *   <li>Provincia: {@code codigo} = ubigeo de 4 dígitos.</li>
 *   <li>Distrito: {@code codigo} = ubigeo de 6 dígitos.</li>
 * </ul>
 *
 * @param departamento  nombre INDECI del departamento al que pertenece
 * @param tienePoligono si existe en los límites IDE-INEI 2023 (para el mapa)
 */
public record Territorio(
        NivelTerritorial nivel,
        String codigo,
        String nombre,
        String departamento,
        String codDepartamento,
        String codProvincia,
        boolean tienePoligono) {
}
