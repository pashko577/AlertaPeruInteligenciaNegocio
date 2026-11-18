package pe.alertaperu.infrastructure.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.alertaperu.application.port.in.ObtenerEstadisticasUseCase;
import pe.alertaperu.domain.model.NivelTerritorial;
import pe.alertaperu.infrastructure.adapter.in.web.dto.FiltroRequest;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.ConteoDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.ResumenDto;

/** Adaptador de entrada: KPIs, gráficos, ranking y datos del mapa. */
@RestController
@RequestMapping("/api/estadisticas")
public class EstadisticasController {

    private final ObtenerEstadisticasUseCase estadisticas;

    public EstadisticasController(ObtenerEstadisticasUseCase estadisticas) {
        this.estadisticas = estadisticas;
    }

    @GetMapping("/resumen")
    public ResumenDto resumen(@ModelAttribute FiltroRequest filtro) {
        return WebMapper.resumen(estadisticas.resumen(filtro.aDominio()));
    }

    @GetMapping("/por-anio")
    public List<ConteoDto> porAnio(@ModelAttribute FiltroRequest filtro) {
        return estadisticas.porAnio(filtro.aDominio()).stream().map(WebMapper::conteo).toList();
    }

    @GetMapping("/por-peligro")
    public List<ConteoDto> porPeligro(@ModelAttribute FiltroRequest filtro) {
        return estadisticas.porPeligro(filtro.aDominio()).stream().map(WebMapper::conteo).toList();
    }

    /** nivel = departamento | provincia | distrito. Lo usan el ranking y el mapa coroplético. */
    @GetMapping("/por-territorio")
    public List<ConteoDto> porTerritorio(@ModelAttribute FiltroRequest filtro,
                                         @RequestParam(required = false) String nivel) {
        return estadisticas.porTerritorio(filtro.aDominio(), NivelTerritorial.desde(nivel)).stream()
                .map(WebMapper::conteo).toList();
    }
}
