package pe.alertaperu.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import pe.alertaperu.application.port.in.ObtenerEstadisticasUseCase;
import pe.alertaperu.application.port.out.CatalogoRepositoryPort;
import pe.alertaperu.application.port.out.EstadisticasRepositoryPort;
import pe.alertaperu.domain.model.ConteoAgrupado;
import pe.alertaperu.domain.model.FiltroEmergencias;
import pe.alertaperu.domain.model.NivelTerritorial;
import pe.alertaperu.domain.model.Peligro;
import pe.alertaperu.domain.model.ResumenEstadistico;
import pe.alertaperu.domain.model.Territorio;
import pe.alertaperu.domain.model.TotalesIndicadores;

public class EstadisticasService implements ObtenerEstadisticasUseCase {

    /** Ranking: más emergencias primero; a igualdad, orden alfabético. */
    static final Comparator<ConteoAgrupado> POR_EMERGENCIAS_DESC =
            Comparator.comparingLong(ConteoAgrupado::emergencias).reversed()
                    .thenComparing(ConteoAgrupado::nombre, Comparator.nullsLast(Comparator.naturalOrder()));

    private final EstadisticasRepositoryPort estadisticas;
    private final CatalogoRepositoryPort catalogo;

    public EstadisticasService(EstadisticasRepositoryPort estadisticas, CatalogoRepositoryPort catalogo) {
        this.estadisticas = Objects.requireNonNull(estadisticas);
        this.catalogo = Objects.requireNonNull(catalogo);
    }

    @Override
    public ResumenEstadistico resumen(FiltroEmergencias filtro) {
        FiltroEmergencias f = filtroOVacio(filtro);
        TotalesIndicadores totales = estadisticas.totales(f);

        double damnificadosPorEmergencia = totales.emergencias() == 0
                ? 0
                : (double) totales.damnificados() / totales.emergencias();

        String peligroMasFrecuente = porPeligro(f).stream()
                .findFirst()
                .map(ConteoAgrupado::nombre)
                .orElse(null);

        List<ConteoAgrupado> anios = porAnio(f);
        Integer ultimoAnio = null;
        Double variacion = null;
        if (!anios.isEmpty()) {
            ConteoAgrupado ultimo = anios.getLast();
            ultimoAnio = Integer.valueOf(ultimo.clave());
            if (anios.size() >= 2) {
                ConteoAgrupado anterior = anios.get(anios.size() - 2);
                boolean consecutivos = Integer.parseInt(anterior.clave()) == ultimoAnio - 1;
                if (consecutivos && anterior.emergencias() > 0) {
                    variacion = (double) (ultimo.emergencias() - anterior.emergencias()) / anterior.emergencias();
                }
            }
        }
        return new ResumenEstadistico(f, totales, damnificadosPorEmergencia,
                peligroMasFrecuente, ultimoAnio, variacion);
    }

    @Override
    public List<ConteoAgrupado> porAnio(FiltroEmergencias filtro) {
        return estadisticas.porAnio(filtroOVacio(filtro)).stream()
                .sorted(Comparator.comparingInt(c -> Integer.parseInt(c.clave())))
                .toList();
    }

    @Override
    public List<ConteoAgrupado> porPeligro(FiltroEmergencias filtro) {
        return estadisticas.porPeligro(filtroOVacio(filtro)).stream()
                .map(c -> c.conNombre(Peligro.etiquetaDe(c.clave())))
                .sorted(POR_EMERGENCIAS_DESC)
                .toList();
    }

    @Override
    public List<ConteoAgrupado> porTerritorio(FiltroEmergencias filtro, NivelTerritorial nivel) {
        NivelTerritorial n = nivel != null ? nivel : NivelTerritorial.DEPARTAMENTO;
        List<ConteoAgrupado> conteos = estadisticas.porTerritorio(filtroOVacio(filtro), n);
        if (n == NivelTerritorial.DEPARTAMENTO) {
            // La clave ya es el nombre INDECI del departamento
            return conteos.stream().map(c -> c.conNombre(c.clave())).sorted(POR_EMERGENCIAS_DESC).toList();
        }
        // Provincias y distritos: nombre oficial desde el catálogo (manda el ubigeo)
        Map<String, String> nombres = catalogo.territorios(n).stream()
                .collect(Collectors.toMap(Territorio::codigo, Territorio::nombre, (a, b) -> a));
        Function<ConteoAgrupado, String> nombreDe = c -> nombres.getOrDefault(c.clave(), c.clave());
        return conteos.stream().map(c -> c.conNombre(nombreDe.apply(c))).sorted(POR_EMERGENCIAS_DESC).toList();
    }

    private static FiltroEmergencias filtroOVacio(FiltroEmergencias filtro) {
        return filtro != null ? filtro : FiltroEmergencias.sinFiltros();
    }
}
