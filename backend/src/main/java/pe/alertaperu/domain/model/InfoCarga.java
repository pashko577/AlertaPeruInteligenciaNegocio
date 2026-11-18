package pe.alertaperu.domain.model;

import java.time.Instant;

/**
 * Datos de la última ejecución del ETL (colección metadatos_carga).
 * Se muestran en "Sobre los datos" para indicar la vigencia del dataset.
 */
public record InfoCarga(
        Instant fechaCarga,
        String archivo,
        long filasLeidas,
        long documentos,
        String sha256) {
}
