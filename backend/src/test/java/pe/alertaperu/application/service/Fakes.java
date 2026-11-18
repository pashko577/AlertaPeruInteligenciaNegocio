package pe.alertaperu.application.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.alertaperu.application.port.out.CatalogoRepositoryPort;
import pe.alertaperu.application.port.out.EmergenciaRepositoryPort;
import pe.alertaperu.application.port.out.EstadisticasRepositoryPort;
import pe.alertaperu.domain.model.ConteoAgrupado;
import pe.alertaperu.domain.model.Emergencia;
import pe.alertaperu.domain.model.FiltroEmergencias;
import pe.alertaperu.domain.model.NivelTerritorial;
import pe.alertaperu.domain.model.Pagina;
import pe.alertaperu.domain.model.Paginacion;
import pe.alertaperu.domain.model.Territorio;
import pe.alertaperu.domain.model.TipoPeligro;
import pe.alertaperu.domain.model.TotalesIndicadores;

/**
 * Implementaciones en memoria de los puertos de salida.
 * Permiten probar los casos de uso sin MongoDB: es la ventaja principal
 * de la arquitectura hexagonal.
 */
public final class Fakes {

    private Fakes() {
    }

    public static ConteoAgrupado conteo(String clave, long emergencias) {
        return new ConteoAgrupado(clave, clave, null, emergencias, 0, 0, 0, 0);
    }

    public static class Emergencias implements EmergenciaRepositoryPort {
        public final Map<String, Emergencia> datos = new HashMap<>();
        FiltroEmergencias ultimoFiltro;

        @Override
        public Pagina<Emergencia> buscar(FiltroEmergencias filtro, Paginacion paginacion) {
            ultimoFiltro = filtro;
            return new Pagina<>(new ArrayList<>(datos.values()), paginacion.pagina(), paginacion.tamano(),
                    datos.size());
        }

        @Override
        public Optional<Emergencia> buscarPorId(String id) {
            return Optional.ofNullable(datos.get(id));
        }
    }

    public static class Estadisticas implements EstadisticasRepositoryPort {
        public TotalesIndicadores totales = TotalesIndicadores.ceros();
        public List<ConteoAgrupado> anios = List.of();
        public List<ConteoAgrupado> peligros = List.of();
        public Map<NivelTerritorial, List<ConteoAgrupado>> territorios = new HashMap<>();

        @Override
        public TotalesIndicadores totales(FiltroEmergencias filtro) {
            return totales;
        }

        @Override
        public List<ConteoAgrupado> porAnio(FiltroEmergencias filtro) {
            return anios;
        }

        @Override
        public List<ConteoAgrupado> porPeligro(FiltroEmergencias filtro) {
            return peligros;
        }

        @Override
        public List<ConteoAgrupado> porTerritorio(FiltroEmergencias filtro, NivelTerritorial nivel) {
            return territorios.getOrDefault(nivel, List.of());
        }
    }

    public static class Catalogo implements CatalogoRepositoryPort {
        final List<Territorio> territorios = new ArrayList<>();

        public Catalogo conDepartamento(String nombre, String cod) {
            territorios.add(new Territorio(NivelTerritorial.DEPARTAMENTO, nombre, nombre, nombre, cod, null, true));
            return this;
        }

        public Catalogo conProvincia(String ubigeo, String nombre, String departamento) {
            territorios.add(new Territorio(NivelTerritorial.PROVINCIA, ubigeo, nombre, departamento,
                    ubigeo.substring(0, 2), ubigeo, true));
            return this;
        }

        public Catalogo conDistrito(String ubigeo, String nombre, String departamento) {
            territorios.add(new Territorio(NivelTerritorial.DISTRITO, ubigeo, nombre, departamento,
                    ubigeo.substring(0, 2), ubigeo.substring(0, 4), true));
            return this;
        }

        @Override
        public List<Territorio> territorios(NivelTerritorial nivel) {
            return territorios.stream().filter(t -> t.nivel() == nivel).toList();
        }

        @Override
        public List<TipoPeligro> peligros() {
            return List.of(new TipoPeligro("ORIGEN NATURAL", List.of("SISMO")));
        }

        @Override
        public List<String> regionesNaturales() {
            return List.of("COSTA", "SIERRA");
        }
    }
}
