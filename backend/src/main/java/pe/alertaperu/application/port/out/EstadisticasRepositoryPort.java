package pe.alertaperu.application.port.out;

import java.util.List;

import pe.alertaperu.domain.model.ConteoAgrupado;
import pe.alertaperu.domain.model.FiltroEmergencias;
import pe.alertaperu.domain.model.NivelTerritorial;
import pe.alertaperu.domain.model.TotalesIndicadores;

/** Puerto de salida: agregaciones sobre las emergencias. */
public interface EstadisticasRepositoryPort {

    TotalesIndicadores totales(FiltroEmergencias filtro);

    /** Un elemento por año con datos; clave = año. */
    List<ConteoAgrupado> porAnio(FiltroEmergencias filtro);

    /** Un elemento por peligro; clave = nombre original, codigo = tipo de peligro. */
    List<ConteoAgrupado> porPeligro(FiltroEmergencias filtro);

    /**
     * Un elemento por territorio del nivel indicado.
     * Departamento: clave = nombre INDECI, codigo = código de 2 dígitos.
     * Provincia y distrito: clave = ubigeo.
     */
    List<ConteoAgrupado> porTerritorio(FiltroEmergencias filtro, NivelTerritorial nivel);
}
