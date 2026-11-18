package pe.alertaperu.infrastructure.adapter.in.web.dto;

import java.util.List;
import java.util.Map;

/** DTOs de salida de la API (desacoplados del modelo de dominio). */
public final class Respuestas {

    private Respuestas() {
    }

    public record PaginaDto<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {
    }

    /** Fila del listado. Un valor null significa "sin dato" en la fuente. */
    public record EmergenciaFilaDto(
            String id,
            String codigoSinpad,
            String fecha,
            String departamento,
            String provincia,
            String distrito,
            String ubigeo,
            String regionNatural,
            String peligro,
            String tipoPeligro,
            Number fallecidos,
            Number damnificados,
            Number afectados,
            Number viviendasDestruidas) {
    }

    public record EmergenciaDetalleDto(
            String id,
            String codigoSinpad,
            String fecha,
            UbicacionDto ubicacion,
            PeligroDto peligro,
            Map<String, Map<String, Number>> indicadores,
            List<String> notas) {
    }

    public record UbicacionDto(String ubigeo, String departamento, String provincia, String distrito,
                               String regionNatural) {
    }

    public record PeligroDto(String nombre, String etiqueta, String tipo) {
    }

    public record TotalesDto(long emergencias, long fallecidos, long desaparecidos, long lesionados,
                             long damnificados, long afectados, long viviendasDestruidas,
                             long viviendasAfectadas, double hectareasCultivoAfectadas,
                             double hectareasCultivoDestruidas) {
    }

    public record ResumenDto(
            Map<String, Object> filtro,
            TotalesDto totales,
            double damnificadosPorEmergencia,
            String peligroMasFrecuente,
            Integer ultimoAnio,
            Double variacionInteranual) {
    }

    public record ConteoDto(String clave, String nombre, String codigo, long emergencias, long fallecidos,
                            long damnificados, long afectados, long viviendasDestruidas) {
    }

    public record TerritorioDto(String codigo, String nombre, String departamento, String codDepartamento,
                                String codProvincia, boolean tienePoligono) {
    }

    public record TipoPeligroDto(String tipo, List<OpcionDto> peligros) {
    }

    public record OpcionDto(String valor, String etiqueta) {
    }

    public record ErrorDto(int estado, String error, String mensaje) {
    }

    public record InfoCargaDto(String fechaCarga, String archivo, long filasLeidas, long documentos,
                               String sha256Corto) {
    }
}
