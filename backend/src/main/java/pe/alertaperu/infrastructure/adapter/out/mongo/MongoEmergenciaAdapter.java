package pe.alertaperu.infrastructure.adapter.out.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import pe.alertaperu.application.port.out.EmergenciaRepositoryPort;
import pe.alertaperu.domain.exception.FiltroInvalidoException;
import pe.alertaperu.domain.model.Emergencia;
import pe.alertaperu.domain.model.FiltroEmergencias;
import pe.alertaperu.domain.model.Pagina;
import pe.alertaperu.domain.model.Paginacion;
import pe.alertaperu.domain.model.Paginacion.CampoOrden;
import pe.alertaperu.infrastructure.adapter.out.mongo.document.EmergenciaDocument;

/** Adaptador de salida: listado y detalle de emergencias en MongoDB. */
@Component
public class MongoEmergenciaAdapter implements EmergenciaRepositoryPort {

    /**
     * Los ordenamientos sin índice (fallecidos, damnificados, afectados) se resuelven
     * en memoria, y Atlas Free limita esa memoria a 32 MB. Por eso se limita la
     * profundidad de página para esos campos.
     */
    static final long DESPLAZAMIENTO_MAXIMO_SIN_INDICE = 10_000;

    private final MongoTemplate mongo;

    public MongoEmergenciaAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Pagina<Emergencia> buscar(FiltroEmergencias filtro, Paginacion paginacion) {
        if (!usaIndice(paginacion.orden()) && paginacion.desplazamiento() > DESPLAZAMIENTO_MAXIMO_SIN_INDICE) {
            throw new FiltroInvalidoException("Al ordenar por " + paginacion.orden().name().toLowerCase()
                    + " solo se pueden consultar los primeros " + DESPLAZAMIENTO_MAXIMO_SIN_INDICE
                    + " registros. Aplica más filtros.");
        }

        Query consulta = new Query(CriteriosMongo.desde(filtro))
                .with(orden(paginacion))
                .skip(paginacion.desplazamiento())
                .limit(paginacion.tamano());
        // Proyección: el listado solo necesita estos campos
        consulta.fields().include("codigoSinpad", "fecha", "ubicacion", "peligro", "personas", "vivienda");

        List<Emergencia> contenido = mongo.find(consulta, EmergenciaDocument.class).stream()
                .map(MongoMapper::aDominio)
                .toList();

        long total = CriteriosMongo.estaVacio(filtro)
                ? mongo.estimatedCount(EmergenciaDocument.class)
                : mongo.count(new Query(CriteriosMongo.desde(filtro)), EmergenciaDocument.class);

        return new Pagina<>(contenido, paginacion.pagina(), paginacion.tamano(), total);
    }

    @Override
    public Optional<Emergencia> buscarPorId(String id) {
        return Optional.ofNullable(mongo.findById(id, EmergenciaDocument.class)).map(MongoMapper::aDominio);
    }

    private static boolean usaIndice(CampoOrden campo) {
        return campo == CampoOrden.FECHA || campo == CampoOrden.DEPARTAMENTO || campo == CampoOrden.PELIGRO;
    }

    private static Sort orden(Paginacion p) {
        Sort.Direction dir = p.descendente() ? Sort.Direction.DESC : Sort.Direction.ASC;
        String campo = switch (p.orden()) {
            case FECHA -> "fecha";
            case DEPARTAMENTO -> "ubicacion.departamento";
            case PELIGRO -> "peligro.nombre";
            case FALLECIDOS -> "personas.fallecidos";
            case DAMNIFICADOS -> "personas.damnificados";
            case AFECTADOS -> "personas.afectados";
            case VIVIENDAS_DESTRUIDAS -> "vivienda.destruidas";
        };
        Sort sort = Sort.by(dir, campo);
        if (p.orden() != CampoOrden.FECHA) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "fecha"));
        }
        // Desempate estable para que la paginación no repita ni salte registros
        return sort.and(Sort.by(Sort.Direction.ASC, "_id"));
    }
}
