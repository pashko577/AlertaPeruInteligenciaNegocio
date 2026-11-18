package pe.alertaperu.application.service;

import java.util.Objects;

import pe.alertaperu.application.port.in.ConsultarEmergenciasUseCase;
import pe.alertaperu.application.port.out.EmergenciaRepositoryPort;
import pe.alertaperu.domain.exception.FiltroInvalidoException;
import pe.alertaperu.domain.exception.RecursoNoEncontradoException;
import pe.alertaperu.domain.model.Emergencia;
import pe.alertaperu.domain.model.FiltroEmergencias;
import pe.alertaperu.domain.model.Pagina;
import pe.alertaperu.domain.model.Paginacion;

public class ConsultarEmergenciasService implements ConsultarEmergenciasUseCase {

    private final EmergenciaRepositoryPort repositorio;

    public ConsultarEmergenciasService(EmergenciaRepositoryPort repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio);
    }

    @Override
    public Pagina<Emergencia> buscar(FiltroEmergencias filtro, Paginacion paginacion) {
        return repositorio.buscar(
                filtro != null ? filtro : FiltroEmergencias.sinFiltros(),
                Objects.requireNonNull(paginacion, "paginacion"));
    }

    @Override
    public Emergencia obtener(String id) {
        if (id == null || id.isBlank()) {
            throw new FiltroInvalidoException("el id de la emergencia es obligatorio");
        }
        return repositorio.buscarPorId(id.trim())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la emergencia " + id));
    }
}
