package pe.alertaperu.domain.model;

/**
 * Totales de un grupo (un año, un peligro, un territorio...).
 *
 * @param clave  identificador del grupo (año, nombre del peligro, ubigeo o nombre de departamento)
 * @param nombre nombre para mostrar
 * @param codigo código auxiliar (p. ej. código de departamento para el mapa); puede ser null
 */
public record ConteoAgrupado(
        String clave,
        String nombre,
        String codigo,
        long emergencias,
        long fallecidos,
        long damnificados,
        long afectados,
        long viviendasDestruidas) {

    public ConteoAgrupado conNombre(String nuevoNombre) {
        return new ConteoAgrupado(clave, nuevoNombre, codigo, emergencias, fallecidos,
                damnificados, afectados, viviendasDestruidas);
    }
}
