package pe.alertaperu.infrastructure.adapter.out.mongo;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.stereotype.Component;

import pe.alertaperu.application.port.out.EstadisticasRepositoryPort;
import pe.alertaperu.domain.model.ConteoAgrupado;
import pe.alertaperu.domain.model.FiltroEmergencias;
import pe.alertaperu.domain.model.NivelTerritorial;
import pe.alertaperu.domain.model.TotalesIndicadores;

/** Adaptador de salida: agregaciones de MongoDB para KPIs, gráficos y mapa. */
@Component
public class MongoEstadisticasAdapter implements EstadisticasRepositoryPort {

    private static final String COLECCION = "emergencias";

    private final MongoTemplate mongo;

    public MongoEstadisticasAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public TotalesIndicadores totales(FiltroEmergencias filtro) {
        GroupOperation grupo = group()
                .count().as("emergencias")
                .sum("personas.fallecidos").as("fallecidos")
                .sum("personas.desaparecidos").as("desaparecidos")
                .sum("personas.lesionados").as("lesionados")
                .sum("personas.damnificados").as("damnificados")
                .sum("personas.afectados").as("afectados")
                .sum("vivienda.destruidas").as("viviendasDestruidas")
                .sum("vivienda.afectadas").as("viviendasAfectadas")
                .sum("agricultura.hasAfectadas").as("hasAfectadas")
                .sum("agricultura.hasDestruidas").as("hasDestruidas");
        Document r = mongo.aggregate(newAggregation(match(CriteriosMongo.desde(filtro)), grupo),
                COLECCION, Document.class).getUniqueMappedResult();
        if (r == null) {
            return TotalesIndicadores.ceros();
        }
        return new TotalesIndicadores(
                entero(r, "emergencias"), entero(r, "fallecidos"), entero(r, "desaparecidos"),
                entero(r, "lesionados"), entero(r, "damnificados"), entero(r, "afectados"),
                entero(r, "viviendasDestruidas"), entero(r, "viviendasAfectadas"),
                decimal(r, "hasAfectadas"), decimal(r, "hasDestruidas"));
    }

    @Override
    public List<ConteoAgrupado> porAnio(FiltroEmergencias filtro) {
        return agrupar(filtro, conMedidas(group("anio")), null);
    }

    @Override
    public List<ConteoAgrupado> porPeligro(FiltroEmergencias filtro) {
        return agrupar(filtro, conMedidas(group("peligro.nombre")).first("peligro.tipo").as("codigo"), "codigo");
    }

    @Override
    public List<ConteoAgrupado> porTerritorio(FiltroEmergencias filtro, NivelTerritorial nivel) {
        return switch (nivel) {
            case DEPARTAMENTO -> agrupar(filtro,
                    conMedidas(group("ubicacion.departamento")).first("ubicacion.codDepartamento").as("codigo"),
                    "codigo");
            case PROVINCIA -> agrupar(filtro,
                    conMedidas(group("ubicacion.codProvincia")).first("ubicacion.codDepartamento").as("codigo"),
                    "codigo");
            case DISTRITO -> agrupar(filtro,
                    conMedidas(group("ubicacion.ubigeo")).first("ubicacion.codProvincia").as("codigo"),
                    "codigo");
        };
    }

    private static GroupOperation conMedidas(GroupOperation grupo) {
        return grupo
                .count().as("emergencias")
                .sum("personas.fallecidos").as("fallecidos")
                .sum("personas.damnificados").as("damnificados")
                .sum("personas.afectados").as("afectados")
                .sum("vivienda.destruidas").as("viviendasDestruidas");
    }

    private List<ConteoAgrupado> agrupar(FiltroEmergencias filtro, GroupOperation grupo, String campoCodigo) {
        Aggregation agregacion = newAggregation(match(CriteriosMongo.desde(filtro)), grupo);
        return mongo.aggregate(agregacion, COLECCION, Document.class).getMappedResults().stream()
                .map(d -> new ConteoAgrupado(
                        String.valueOf(d.get("_id")),
                        String.valueOf(d.get("_id")),
                        campoCodigo == null ? null : d.getString(campoCodigo),
                        entero(d, "emergencias"),
                        entero(d, "fallecidos"),
                        entero(d, "damnificados"),
                        entero(d, "afectados"),
                        entero(d, "viviendasDestruidas")))
                .toList();
    }

    private static long entero(Document d, String campo) {
        Object v = d.get(campo);
        return v instanceof Number n ? Math.round(n.doubleValue()) : 0L;
    }

    private static double decimal(Document d, String campo) {
        Object v = d.get(campo);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
