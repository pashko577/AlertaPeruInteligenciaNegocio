package pe.alertaperu.infrastructure.adapter.out.mongo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.mongodb.core.query.Criteria;

import pe.alertaperu.domain.model.FiltroEmergencias;

/**
 * Traduce el filtro del dominio a criterios de MongoDB.
 * Cada criterio se apoya en los índices aprobados (sección 9 del documento).
 */
final class CriteriosMongo {

    private CriteriosMongo() {
    }

    static Criteria desde(FiltroEmergencias filtro) {
        List<Criteria> partes = new ArrayList<>();

        // Años -> rango sobre "fecha" (índice idx_fecha)
        if (filtro.anioDesde() != null || filtro.anioHasta() != null) {
            Criteria fecha = Criteria.where("fecha");
            if (filtro.anioDesde() != null) {
                fecha = fecha.gte(inicioDeAnio(filtro.anioDesde()));
            }
            if (filtro.anioHasta() != null) {
                fecha = fecha.lt(inicioDeAnio(filtro.anioHasta() + 1));
            }
            partes.add(fecha);
        }

        if (filtro.departamento() != null) {
            partes.add(Criteria.where("ubicacion.departamento").is(filtro.departamento()));
        }

        // Distrito (igualdad) o provincia (prefijo anclado, que también usa idx_ubigeo_fecha).
        // El dominio ya validó que el ubigeo solo contiene dígitos.
        if (filtro.distrito() != null) {
            partes.add(Criteria.where("ubicacion.ubigeo").is(filtro.distrito()));
        } else if (filtro.provincia() != null) {
            partes.add(Criteria.where("ubicacion.ubigeo").regex("^" + filtro.provincia()));
        }

        if (filtro.regionNatural() != null) {
            partes.add(Criteria.where("ubicacion.regionNatural").is(filtro.regionNatural()));
        }
        if (filtro.tipoPeligro() != null) {
            partes.add(Criteria.where("peligro.tipo").is(filtro.tipoPeligro()));
        }
        if (filtro.peligro() != null) {
            partes.add(Criteria.where("peligro.nombre").is(filtro.peligro()));
        }

        return partes.isEmpty() ? new Criteria() : new Criteria().andOperator(partes);
    }

    static boolean estaVacio(FiltroEmergencias filtro) {
        return filtro.equals(FiltroEmergencias.sinFiltros());
    }

    private static Instant inicioDeAnio(int anio) {
        return LocalDate.of(anio, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
