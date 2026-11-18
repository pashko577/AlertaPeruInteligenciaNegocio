package pe.alertaperu.infrastructure.adapter.out.mongo;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import pe.alertaperu.application.port.out.CatalogoRepositoryPort;
import pe.alertaperu.domain.model.NivelTerritorial;
import pe.alertaperu.domain.model.Territorio;
import pe.alertaperu.domain.model.TipoPeligro;
import pe.alertaperu.infrastructure.adapter.out.mongo.document.EmergenciaDocument;
import pe.alertaperu.infrastructure.adapter.out.mongo.document.TerritorioDocument;

/**
 * Adaptador de salida: catálogos. Son datos estáticos entre cargas del ETL,
 * así que se guardan en memoria después de la primera consulta para no
 * gastar operaciones de Atlas Free en cada cambio de filtro.
 */
@Component
public class MongoCatalogoAdapter implements CatalogoRepositoryPort {

    private final MongoTemplate mongo;
    private final Map<NivelTerritorial, List<Territorio>> territorios = new EnumMap<>(NivelTerritorial.class);
    private volatile List<TipoPeligro> peligros;
    private volatile List<String> regiones;

    public MongoCatalogoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public synchronized List<Territorio> territorios(NivelTerritorial nivel) {
        return territorios.computeIfAbsent(nivel, n -> mongo.find(
                        Query.query(Criteria.where("nivel").is(n.name().toLowerCase())), TerritorioDocument.class)
                .stream()
                .map(MongoMapper::aDominio)
                .toList());
    }

    @Override
    public List<TipoPeligro> peligros() {
        if (peligros == null) {
            peligros = mongo.aggregate(newAggregation(
                                    group("peligro.tipo").addToSet("peligro.nombre").as("peligros"),
                                    sort(Sort.Direction.ASC, "_id")),
                            "emergencias", Document.class)
                    .getMappedResults().stream()
                    .map(d -> new TipoPeligro(d.getString("_id"),
                            d.getList("peligros", String.class).stream()
                                    .sorted(Comparator.comparing((String p) -> p.startsWith("z")).thenComparing(p -> p))
                                    .toList()))
                    .toList();
        }
        return peligros;
    }

    @Override
    public List<String> regionesNaturales() {
        if (regiones == null) {
            regiones = mongo.findDistinct(new Query(), "ubicacion.regionNatural",
                            EmergenciaDocument.class, String.class)
                    .stream().sorted().toList();
        }
        return regiones;
    }
}
