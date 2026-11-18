package pe.alertaperu.infrastructure.adapter.in.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import pe.alertaperu.domain.model.ConteoAgrupado;
import pe.alertaperu.domain.model.Emergencia;
import pe.alertaperu.domain.model.FiltroEmergencias;
import pe.alertaperu.domain.model.Pagina;
import pe.alertaperu.domain.model.Peligro;
import pe.alertaperu.domain.model.ResumenEstadistico;
import pe.alertaperu.domain.model.Territorio;
import pe.alertaperu.domain.model.TipoPeligro;
import pe.alertaperu.domain.model.TotalesIndicadores;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.ConteoDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.EmergenciaDetalleDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.EmergenciaFilaDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.OpcionDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.PaginaDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.PeligroDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.ResumenDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.TerritorioDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.TipoPeligroDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.TotalesDto;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.UbicacionDto;

/** Traduce modelos del dominio a DTOs de la API. */
final class WebMapper {

    static final String NOTA_AYUDA = "Peso y costo de la ayuda: unidades no verificadas. El diccionario "
            + "indica toneladas y miles de soles, pero las magnitudes no son coherentes con esas unidades.";
    static final String NOTA_EDUCACION = "Educación: hasta 2018 se registran centros educativos; "
            + "desde 2019, aulas. No son comparables entre sí.";

    private WebMapper() {
    }

    static <T, R> PaginaDto<R> pagina(Pagina<T> p, Function<T, R> mapeo) {
        return new PaginaDto<>(p.contenido().stream().map(mapeo).toList(),
                p.pagina(), p.tamano(), p.totalElementos(), p.totalPaginas());
    }

    static EmergenciaFilaDto fila(Emergencia e) {
        var u = e.ubicacion();
        var i = e.indicadores();
        return new EmergenciaFilaDto(
                e.id(), e.codigoSinpad(), texto(e.fecha()),
                u.departamento(), u.provincia(), u.distrito(), u.ubigeo(), u.regionNatural(),
                e.peligro().etiqueta(), e.peligro().tipo(),
                i.valor("personas", "fallecidos").orElse(null),
                i.valor("personas", "damnificados").orElse(null),
                i.valor("personas", "afectados").orElse(null),
                i.valor("vivienda", "destruidas").orElse(null));
    }

    static EmergenciaDetalleDto detalle(Emergencia e) {
        var u = e.ubicacion();
        List<String> notas = new ArrayList<>();
        if (e.indicadores().grupos().containsKey("ayuda")) {
            notas.add(NOTA_AYUDA);
        }
        notas.add(NOTA_EDUCACION);
        return new EmergenciaDetalleDto(
                e.id(), e.codigoSinpad(), texto(e.fecha()),
                new UbicacionDto(u.ubigeo(), u.departamento(), u.provincia(), u.distrito(), u.regionNatural()),
                peligro(e.peligro()),
                e.indicadores().grupos(),
                notas);
    }

    static ResumenDto resumen(ResumenEstadistico r) {
        TotalesIndicadores t = r.totales();
        return new ResumenDto(
                filtro(r.filtro()),
                new TotalesDto(t.emergencias(), t.fallecidos(), t.desaparecidos(), t.lesionados(),
                        t.damnificados(), t.afectados(), t.viviendasDestruidas(), t.viviendasAfectadas(),
                        t.hectareasCultivoAfectadas(), t.hectareasCultivoDestruidas()),
                r.damnificadosPorEmergencia(),
                r.peligroMasFrecuente(),
                r.ultimoAnio(),
                r.variacionInteranual());
    }

    static ConteoDto conteo(ConteoAgrupado c) {
        return new ConteoDto(c.clave(), c.nombre(), c.codigo(), c.emergencias(), c.fallecidos(),
                c.damnificados(), c.afectados(), c.viviendasDestruidas());
    }

    static TerritorioDto territorio(Territorio t) {
        return new TerritorioDto(t.codigo(), t.nombre(), t.departamento(), t.codDepartamento(),
                t.codProvincia(), t.tienePoligono());
    }

    static TipoPeligroDto tipoPeligro(TipoPeligro t) {
        return new TipoPeligroDto(t.tipo(),
                t.peligros().stream().map(p -> new OpcionDto(p, Peligro.etiquetaDe(p))).toList());
    }

    /** Filtros efectivamente aplicados (solo los no nulos), para mostrarlos junto a los KPIs. */
    static Map<String, Object> filtro(FiltroEmergencias f) {
        Map<String, Object> m = new LinkedHashMap<>();
        poner(m, "anioDesde", f.anioDesde());
        poner(m, "anioHasta", f.anioHasta());
        poner(m, "departamento", f.departamento());
        poner(m, "provincia", f.provincia());
        poner(m, "distrito", f.distrito());
        poner(m, "regionNatural", f.regionNatural());
        poner(m, "tipoPeligro", f.tipoPeligro());
        poner(m, "peligro", f.peligro());
        return m;
    }

    private static PeligroDto peligro(Peligro p) {
        return new PeligroDto(p.nombre(), p.etiqueta(), p.tipo());
    }

    private static void poner(Map<String, Object> m, String clave, Object valor) {
        if (valor != null) {
            m.put(clave, valor);
        }
    }

    private static String texto(Object o) {
        return o == null ? null : o.toString();
    }
}
