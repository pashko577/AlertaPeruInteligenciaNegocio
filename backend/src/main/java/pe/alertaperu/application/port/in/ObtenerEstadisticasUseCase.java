package pe.alertaperu.application.port.in;

import java.util.List;

import pe.alertaperu.domain.model.ConteoAgrupado;
import pe.alertaperu.domain.model.FiltroEmergencias;
import pe.alertaperu.domain.model.NivelTerritorial;
import pe.alertaperu.domain.model.ResumenEstadistico;

/** Caso de uso: indicadores y agregaciones para el dashboard y el mapa. */
public interface ObtenerEstadisticasUseCase {

    ResumenEstadistico resumen(FiltroEmergencias filtro);

    List<ConteoAgrupado> porAnio(FiltroEmergencias filtro);

    List<ConteoAgrupado> porPeligro(FiltroEmergencias filtro);

    List<ConteoAgrupado> porTerritorio(FiltroEmergencias filtro, NivelTerritorial nivel);
}
