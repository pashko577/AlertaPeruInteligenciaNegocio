package pe.alertaperu.domain.model;

import java.util.regex.Pattern;

import pe.alertaperu.domain.exception.FiltroInvalidoException;

/**
 * Filtros comunes a listados, estadísticas y mapa. Todos son opcionales
 * ({@code null} = sin filtrar por ese criterio).
 *
 * @param anioDesde     año inicial (incluido)
 * @param anioHasta     año final (incluido)
 * @param departamento  nombre INDECI (p. ej. "CUSCO", "LIMA METROPOLITANA")
 * @param provincia     ubigeo de provincia (4 dígitos)
 * @param distrito      ubigeo de distrito (6 dígitos)
 * @param regionNatural COSTA, SIERRA, SELVA ALTA o SELVA BAJA
 * @param tipoPeligro   ORIGEN NATURAL o INDUCIDOS POR LA ACCIÓN HUMANA
 * @param peligro       nombre original del peligro
 */
public record FiltroEmergencias(
        Integer anioDesde,
        Integer anioHasta,
        String departamento,
        String provincia,
        String distrito,
        String regionNatural,
        String tipoPeligro,
        String peligro) {

    public static final int ANIO_MINIMO = 2000;
    public static final int ANIO_MAXIMO = 2100;

    private static final Pattern UBIGEO_PROVINCIA = Pattern.compile("\\d{4}");
    private static final Pattern UBIGEO_DISTRITO = Pattern.compile("\\d{6}");

    public FiltroEmergencias {
        departamento = normalizar(departamento);
        provincia = normalizar(provincia);
        distrito = normalizar(distrito);
        regionNatural = normalizar(regionNatural);
        tipoPeligro = normalizar(tipoPeligro);
        peligro = normalizar(peligro);

        validarAnio(anioDesde, "anioDesde");
        validarAnio(anioHasta, "anioHasta");
        if (anioDesde != null && anioHasta != null && anioDesde > anioHasta) {
            throw new FiltroInvalidoException("anioDesde no puede ser mayor que anioHasta");
        }
        if (provincia != null && !UBIGEO_PROVINCIA.matcher(provincia).matches()) {
            throw new FiltroInvalidoException("provincia debe ser un ubigeo de 4 dígitos");
        }
        if (distrito != null && !UBIGEO_DISTRITO.matcher(distrito).matches()) {
            throw new FiltroInvalidoException("distrito debe ser un ubigeo de 6 dígitos");
        }
        if (provincia != null && distrito != null && !distrito.startsWith(provincia)) {
            throw new FiltroInvalidoException("el distrito " + distrito
                    + " no pertenece a la provincia " + provincia);
        }
    }

    public static FiltroEmergencias sinFiltros() {
        return new FiltroEmergencias(null, null, null, null, null, null, null, null);
    }

    private static String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private static void validarAnio(Integer anio, String nombre) {
        if (anio != null && (anio < ANIO_MINIMO || anio > ANIO_MAXIMO)) {
            throw new FiltroInvalidoException(nombre + " fuera de rango: " + anio);
        }
    }
}
