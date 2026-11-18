package pe.alertaperu.domain.model;

/**
 * Sumas de los indicadores principales para un conjunto de emergencias.
 * Los vacíos de la fuente no se suman (no cuentan como cero ni como dato).
 */
public record TotalesIndicadores(
        long emergencias,
        long fallecidos,
        long desaparecidos,
        long lesionados,
        long damnificados,
        long afectados,
        long viviendasDestruidas,
        long viviendasAfectadas,
        double hectareasCultivoAfectadas,
        double hectareasCultivoDestruidas) {

    public static TotalesIndicadores ceros() {
        return new TotalesIndicadores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
