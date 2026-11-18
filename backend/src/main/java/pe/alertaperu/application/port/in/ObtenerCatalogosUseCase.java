package pe.alertaperu.application.port.in;

import java.util.List;

import pe.alertaperu.domain.model.Territorio;
import pe.alertaperu.domain.model.TipoPeligro;

/** Caso de uso: opciones de los filtros (listas en cascada). */
public interface ObtenerCatalogosUseCase {

    List<Territorio> departamentos();

    List<Territorio> provincias(String departamento);

    List<Territorio> distritos(String provincia);

    List<TipoPeligro> peligros();

    List<String> regionesNaturales();
}
