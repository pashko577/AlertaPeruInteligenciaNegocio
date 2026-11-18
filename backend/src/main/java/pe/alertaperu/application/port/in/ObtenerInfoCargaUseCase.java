package pe.alertaperu.application.port.in;

import pe.alertaperu.domain.model.InfoCarga;

/** Caso de uso: vigencia del dataset cargado. */
public interface ObtenerInfoCargaUseCase {

    /** @throws pe.alertaperu.domain.exception.RecursoNoEncontradoException si no hubo cargas */
    InfoCarga ultimaCarga();
}
