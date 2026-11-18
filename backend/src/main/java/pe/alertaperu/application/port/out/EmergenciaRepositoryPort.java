package pe.alertaperu.application.port.out;

import java.util.Optional;

import pe.alertaperu.domain.model.Emergencia;
import pe.alertaperu.domain.model.FiltroEmergencias;
import pe.alertaperu.domain.model.Pagina;
import pe.alertaperu.domain.model.Paginacion;

/** Puerto de salida: acceso a los registros de emergencias. */
public interface EmergenciaRepositoryPort {

    Pagina<Emergencia> buscar(FiltroEmergencias filtro, Paginacion paginacion);

    Optional<Emergencia> buscarPorId(String id);
}
