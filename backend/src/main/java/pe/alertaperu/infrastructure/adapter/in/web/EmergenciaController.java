package pe.alertaperu.infrastructure.adapter.in.web;

import java.util.Locale;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.alertaperu.application.port.in.ConsultarEmergenciasUseCase;
import pe.alertaperu.domain.exception.FiltroInvalidoException;
import pe.alertaperu.domain.model.Paginacion;
import pe.alertaperu.domain.model.Paginacion.CampoOrden;
import pe.alertaperu.infrastructure.adapter.in.web.dto.FiltroRequest;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.EmergenciaDetalleDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.EmergenciaFilaDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.PaginaDto;

/** Adaptador de entrada: explorador de registros. */
@RestController
@RequestMapping("/api/emergencias")
public class EmergenciaController {

    private final ConsultarEmergenciasUseCase consultar;

    public EmergenciaController(ConsultarEmergenciasUseCase consultar) {
        this.consultar = consultar;
    }

    /**
     * Ejemplo: /api/emergencias?departamento=CUSCO&anioDesde=2020&page=0&size=20&sort=damnificados&dir=desc
     */
    @GetMapping
    public PaginaDto<EmergenciaFilaDto> listar(
            @ModelAttribute FiltroRequest filtro,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Paginacion.TAMANO_POR_DEFECTO) int size,
            @RequestParam(defaultValue = "fecha") String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        Paginacion paginacion = new Paginacion(page, size, campoOrden(sort), descendente(dir));
        return WebMapper.pagina(consultar.buscar(filtro.aDominio(), paginacion), WebMapper::fila);
    }

    @GetMapping("/{id}")
    public EmergenciaDetalleDto detalle(@PathVariable String id) {
        return WebMapper.detalle(consultar.obtener(id));
    }

    /** Acepta "viviendasDestruidas", "viviendas_destruidas" o "VIVIENDAS_DESTRUIDAS". */
    private static CampoOrden campoOrden(String texto) {
        String normalizado = texto.trim().replace("_", "").toUpperCase(Locale.ROOT);
        for (CampoOrden c : CampoOrden.values()) {
            if (c.name().replace("_", "").equals(normalizado)) {
                return c;
            }
        }
        throw new FiltroInvalidoException("sort inválido: '" + texto + "'. Valores permitidos: fecha, "
                + "departamento, peligro, fallecidos, damnificados, afectados, viviendasDestruidas");
    }

    private static boolean descendente(String dir) {
        return switch (dir.trim().toLowerCase(Locale.ROOT)) {
            case "desc" -> true;
            case "asc" -> false;
            default -> throw new FiltroInvalidoException("dir debe ser 'asc' o 'desc'");
        };
    }
}
