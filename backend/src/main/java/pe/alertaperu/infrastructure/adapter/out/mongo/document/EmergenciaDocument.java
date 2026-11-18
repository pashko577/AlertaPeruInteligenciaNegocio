package pe.alertaperu.infrastructure.adapter.out.mongo.document;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento de la colección "emergencias" tal como lo carga el ETL.
 *
 * <p>La fecha se lee como {@link Instant} (UTC) para evitar que se convierta
 * con la zona horaria local y se desplace un día.</p>
 *
 * <p>Los grupos de indicadores son mapas: un campo ausente significa "sin dato".</p>
 */
@Document(collection = "emergencias")
public record EmergenciaDocument(
        @Id String id,
        String codigoSinpad,
        Instant fecha,
        Integer anio,
        Integer mes,
        UbicacionDocument ubicacion,
        PeligroDocument peligro,
        Map<String, Object> personas,
        Map<String, Object> vivienda,
        Map<String, Object> educacion,
        Map<String, Object> salud,
        Map<String, Object> agricultura,
        Map<String, Object> infraestructura,
        Map<String, Object> ganaderia,
        Map<String, Object> ayuda) {

    public record UbicacionDocument(
            String ubigeo,
            String codDepartamento,
            String codProvincia,
            String departamento,
            String provincia,
            String distrito,
            String regionNatural) {
    }

    public record PeligroDocument(String nombre, String tipo) {
    }
}
