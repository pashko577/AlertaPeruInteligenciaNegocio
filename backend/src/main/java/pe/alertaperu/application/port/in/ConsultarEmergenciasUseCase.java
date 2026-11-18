package pe.alertaperu.application.port.in;

import pe.alertaperu.domain.model.Emergencia;
import pe.alertaperu.domain.model.FiltroEmergencias;
import pe.alertaperu.domain.model.Pagina;
import pe.alertaperu.domain.model.Paginacion;

/** Caso de uso: explorar registros de emergencias. */
public interface ConsultarEmergenciasUseCase {

    Pagina<Emergencia> buscar(FiltroEmergencias filtro, Paginacion paginacion);

    /** @throws pe.alertaperu.domain.exception.RecursoNoEncontradoException si no existe */
    Emergencia obtener(String id);
}
