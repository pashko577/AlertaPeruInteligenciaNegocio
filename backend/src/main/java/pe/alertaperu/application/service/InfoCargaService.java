package pe.alertaperu.application.service;

import java.util.Objects;

import pe.alertaperu.application.port.in.ObtenerInfoCargaUseCase;
import pe.alertaperu.application.port.out.InfoCargaRepositoryPort;
import pe.alertaperu.domain.exception.RecursoNoEncontradoException;
import pe.alertaperu.domain.model.InfoCarga;

public class InfoCargaService implements ObtenerInfoCargaUseCase {

    private final InfoCargaRepositoryPort repositorio;

    public InfoCargaService(InfoCargaRepositoryPort repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio);
    }

    @Override
    public InfoCarga ultimaCarga() {
        return repositorio.ultimaCarga()
                .orElseThrow(() -> new RecursoNoEncontradoException("Todavía no se ha cargado el dataset"));
    }
}
