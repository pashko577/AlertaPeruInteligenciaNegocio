package pe.alertaperu.application.port.out;

import java.util.Optional;

import pe.alertaperu.domain.model.InfoCarga;

/** Puerto de salida: metadatos de la última carga del ETL. */
public interface InfoCargaRepositoryPort {

    Optional<InfoCarga> ultimaCarga();
}
