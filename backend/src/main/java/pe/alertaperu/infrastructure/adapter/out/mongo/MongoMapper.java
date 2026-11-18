package pe.alertaperu.infrastructure.adapter.out.mongo;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import pe.alertaperu.domain.model.Emergencia;
import pe.alertaperu.domain.model.Indicadores;
import pe.alertaperu.domain.model.NivelTerritorial;
import pe.alertaperu.domain.model.Peligro;
import pe.alertaperu.domain.model.Territorio;
import pe.alertaperu.domain.model.Ubicacion;
import pe.alertaperu.infrastructure.adapter.out.mongo.document.EmergenciaDocument;
import pe.alertaperu.infrastructure.adapter.out.mongo.document.TerritorioDocument;

/** Traduce documentos de MongoDB a modelos del dominio (y nada más). */
final class MongoMapper {

    private MongoMapper() {
    }

    static Emergencia aDominio(EmergenciaDocument d) {
        var u = d.ubicacion();
        var p = d.peligro();
        Map<String, Map<String, Number>> grupos = new LinkedHashMap<>();
        agregarGrupo(grupos, "personas", d.personas());
        agregarGrupo(grupos, "vivienda", d.vivienda());
        agregarGrupo(grupos, "educacion", d.educacion());
        agregarGrupo(grupos, "salud", d.salud());
        agregarGrupo(grupos, "agricultura", d.agricultura());
        agregarGrupo(grupos, "infraestructura", d.infraestructura());
        agregarGrupo(grupos, "ganaderia", d.ganaderia());
        agregarGrupo(grupos, "ayuda", d.ayuda());

        return new Emergencia(
                d.id(),
                d.codigoSinpad(),
                d.fecha() == null ? null : d.fecha().atZone(ZoneOffset.UTC).toLocalDate(),
                u == null ? null : new Ubicacion(u.ubigeo(), u.codDepartamento(), u.codProvincia(),
                        u.departamento(), u.provincia(), u.distrito(), u.regionNatural()),
                p == null ? null : new Peligro(p.nombre(), p.tipo()),
                new Indicadores(grupos));
    }

    static Territorio aDominio(TerritorioDocument d) {
        NivelTerritorial nivel = NivelTerritorial.desde(d.nivel());
        String codigo = nivel == NivelTerritorial.DEPARTAMENTO ? d.nombre() : d.ubigeo();
        return new Territorio(
                nivel,
                codigo,
                d.nombre(),
                nivel == NivelTerritorial.DEPARTAMENTO ? d.nombre() : d.departamento(),
                d.codDepartamento(),
                d.codProvincia() != null ? d.codProvincia()
                        : (nivel == NivelTerritorial.PROVINCIA ? d.ubigeo() : null),
                Boolean.TRUE.equals(d.tienePoligono()) || nivel == NivelTerritorial.DEPARTAMENTO);
    }

    private static void agregarGrupo(Map<String, Map<String, Number>> destino, String nombre,
                                     Map<String, Object> origen) {
        if (origen == null) {
            return;
        }
        Map<String, Number> valores = new LinkedHashMap<>();
        origen.forEach((campo, valor) -> {
            if (valor instanceof Number n) {
                valores.put(campo, n);
            }
        });
        destino.put(nombre, valores);
    }
}
