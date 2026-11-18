package pe.alertaperu.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.alertaperu.application.port.in.ConsultarEmergenciasUseCase;
import pe.alertaperu.application.port.in.ObtenerCatalogosUseCase;
import pe.alertaperu.application.port.in.ObtenerEstadisticasUseCase;
import pe.alertaperu.application.port.in.ObtenerInfoCargaUseCase;
import pe.alertaperu.application.port.out.CatalogoRepositoryPort;
import pe.alertaperu.application.port.out.EmergenciaRepositoryPort;
import pe.alertaperu.application.port.out.EstadisticasRepositoryPort;
import pe.alertaperu.application.port.out.InfoCargaRepositoryPort;
import pe.alertaperu.application.service.CatalogoService;
import pe.alertaperu.application.service.ConsultarEmergenciasService;
import pe.alertaperu.application.service.EstadisticasService;
import pe.alertaperu.application.service.InfoCargaService;

/**
 * Ensambla el hexágono: conecta los casos de uso (application) con los
 * adaptadores de salida (MongoDB). Las clases de application no tienen
 * anotaciones de Spring; solo esta configuración las conoce.
 */
@Configuration
public class CasosDeUsoConfig {

    @Bean
    ConsultarEmergenciasUseCase consultarEmergencias(EmergenciaRepositoryPort repositorio) {
        return new ConsultarEmergenciasService(repositorio);
    }

    @Bean
    ObtenerEstadisticasUseCase obtenerEstadisticas(EstadisticasRepositoryPort estadisticas,
                                                   CatalogoRepositoryPort catalogo) {
        return new EstadisticasService(estadisticas, catalogo);
    }

    @Bean
    ObtenerCatalogosUseCase obtenerCatalogos(CatalogoRepositoryPort catalogo) {
        return new CatalogoService(catalogo);
    }

    @Bean
    ObtenerInfoCargaUseCase obtenerInfoCarga(InfoCargaRepositoryPort repositorio) {
        return new InfoCargaService(repositorio);
    }
}
