package pe.alertaperu.infrastructure.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.alertaperu.application.port.in.ObtenerCatalogosUseCase;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.TerritorioDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.TipoPeligroDto;

/** Adaptador de entrada: opciones de los filtros (listas en cascada). */
@RestController
@RequestMapping("/api/catalogos")
public class CatalogoController {

    private final ObtenerCatalogosUseCase catalogos;

    public CatalogoController(ObtenerCatalogosUseCase catalogos) {
        this.catalogos = catalogos;
    }

    @GetMapping("/departamentos")
    public List<TerritorioDto> departamentos() {
        return catalogos.departamentos().stream().map(WebMapper::territorio).toList();
    }

    @GetMapping("/provincias")
    public List<TerritorioDto> provincias(@RequestParam String departamento) {
        return catalogos.provincias(departamento).stream().map(WebMapper::territorio).toList();
    }

    @GetMapping("/distritos")
    public List<TerritorioDto> distritos(@RequestParam String provincia) {
        return catalogos.distritos(provincia).stream().map(WebMapper::territorio).toList();
    }

    @GetMapping("/peligros")
    public List<TipoPeligroDto> peligros() {
        return catalogos.peligros().stream().map(WebMapper::tipoPeligro).toList();
    }

    @GetMapping("/regiones")
    public List<String> regiones() {
        return catalogos.regionesNaturales();
    }
}
