package pe.alertaperu.infrastructure.adapter.out.mongo;

import java.util.Date;
import java.util.Optional;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import pe.alertaperu.application.port.out.InfoCargaRepositoryPort;
import pe.alertaperu.domain.model.InfoCarga;

/** Adaptador de salida: lee la ejecución más reciente de metadatos_carga. */
@Component
public class MongoInfoCargaAdapter implements InfoCargaRepositoryPort {

    private final MongoTemplate mongo;

    public MongoInfoCargaAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<InfoCarga> ultimaCarga() {
        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "registradoEn")).limit(1);
        Document d = mongo.findOne(q, Document.class, "metadatos_carga");
        if (d == null) {
            return Optional.empty();
        }
        Date fecha = d.getDate("registradoEn");
        return Optional.of(new InfoCarga(
                fecha == null ? null : fecha.toInstant(),
                d.getString("archivo"),
                numero(d, "filasLeidas"),
                numero(d, "documentosEnAtlas"),
                d.getString("sha256")));
    }

    private static long numero(Document d, String campo) {
        return d.get(campo) instanceof Number n ? n.longValue() : 0L;
    }
}
