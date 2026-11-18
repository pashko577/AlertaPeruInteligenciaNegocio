package pe.alertaperu.infrastructure.adapter.out.mongo.document;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento de la colección "territorios" (catálogo generado por el ETL).
 * _id: "departamento:NOMBRE", "provincia:UBIGEO4" o "distrito:UBIGEO6".
 */
@Document(collection = "territorios")
public record TerritorioDocument(
        @Id String id,
        String nivel,
        String nombre,
        String ubigeo,
        String departamento,
        String codDepartamento,
        String codProvincia,
        Boolean tienePoligono,
        List<String> provincias) {
}
