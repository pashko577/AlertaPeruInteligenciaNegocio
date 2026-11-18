package pe.alertaperu.domain.model;

/**
 * KPIs del dashboard para un filtro dado (sección 5 del documento del proyecto).
 *
 * @param damnificadosPorEmergencia damnificados / emergencias (0 si no hay emergencias)
 * @param peligroMasFrecuente       etiqueta del peligro con más registros, o null si no hay datos
 * @param variacionInteranual       (último año − año anterior) / año anterior, o null si
 *                                  el filtro abarca menos de dos años con datos
 */
public record ResumenEstadistico(
        FiltroEmergencias filtro,
        TotalesIndicadores totales,
        double damnificadosPorEmergencia,
        String peligroMasFrecuente,
        Integer ultimoAnio,
        Double variacionInteranual) {
}
