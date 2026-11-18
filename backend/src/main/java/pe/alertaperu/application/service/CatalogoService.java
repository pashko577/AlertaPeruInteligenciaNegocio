package pe.alertaperu.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import pe.alertaperu.application.port.in.ObtenerCatalogosUseCase;
import pe.alertaperu.application.port.out.CatalogoRepositoryPort;
import pe.alertaperu.domain.exception.FiltroInvalidoException;
import pe.alertaperu.domain.exception.RecursoNoEncontradoException;
import pe.alertaperu.domain.model.NivelTerritorial;
import pe.alertaperu.domain.model.Territorio;
import pe.alertaperu.domain.model.TipoPeligro;

public class CatalogoService implements ObtenerCatalogosUseCase {

    private static final Comparator<Territorio> POR_NOMBRE = Comparator.comparing(Territorio::nombre);

    private final CatalogoRepositoryPort catalogo;

    public CatalogoService(CatalogoRepositoryPort catalogo) {
        this.catalogo = Objects.requireNonNull(catalogo);
    }

    @Override
    public List<Territorio> departamentos() {
        return catalogo.territorios(NivelTerritorial.DEPARTAMENTO).stream().sorted(POR_NOMBRE).toList();
    }

    @Override
    public List<Territorio> provincias(String departamento) {
        if (departamento == null || departamento.isBlank()) {
            throw new FiltroInvalidoException("el parámetro departamento es obligatorio");
        }
        String dep = departamento.trim();
        boolean existe = catalogo.territorios(NivelTerritorial.DEPARTAMENTO).stream()
                .anyMatch(t -> t.codigo().equals(dep));
        if (!existe) {
            throw new RecursoNoEncontradoException("No existe el departamento " + dep);
        }
        return catalogo.territorios(NivelTerritorial.PROVINCIA).stream()
                .filter(t -> dep.equals(t.departamento()))
                .sorted(POR_NOMBRE)
                .toList();
    }

    @Override
    public List<Territorio> distritos(String provincia) {
        if (provincia == null || !provincia.trim().matches("\\d{4}")) {
            throw new FiltroInvalidoException("provincia debe ser un ubigeo de 4 dígitos");
        }
        String prov = provincia.trim();
        List<Territorio> distritos = catalogo.territorios(NivelTerritorial.DISTRITO).stream()
                .filter(t -> prov.equals(t.codProvincia()))
                .sorted(POR_NOMBRE)
                .toList();
        if (distritos.isEmpty()) {
            throw new RecursoNoEncontradoException("No existe la provincia " + prov);
        }
        return distritos;
    }

    @Override
    public List<TipoPeligro> peligros() {
        return catalogo.peligros();
    }

    @Override
    public List<String> regionesNaturales() {
        return catalogo.regionesNaturales();
    }
}
