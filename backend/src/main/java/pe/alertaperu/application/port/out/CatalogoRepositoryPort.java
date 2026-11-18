package pe.alertaperu.application.port.out;

import java.util.List;

import pe.alertaperu.domain.model.NivelTerritorial;
import pe.alertaperu.domain.model.Territorio;
import pe.alertaperu.domain.model.TipoPeligro;

/** Puerto de salida: catálogos para filtros y mapa. */
public interface CatalogoRepositoryPort {

    List<Territorio> territorios(NivelTerritorial nivel);

    List<TipoPeligro> peligros();

    List<String> regionesNaturales();
}
