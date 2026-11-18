package pe.alertaperu.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static pe.alertaperu.application.service.Fakes.conteo;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.alertaperu.domain.model.ConteoAgrupado;
import pe.alertaperu.domain.model.FiltroEmergencias;
import pe.alertaperu.domain.model.NivelTerritorial;
import pe.alertaperu.domain.model.TotalesIndicadores;

class EstadisticasServiceTest {

    private Fakes.Estadisticas repo;
    private Fakes.Catalogo catalogo;
    private EstadisticasService servicio;

    @BeforeEach
    void preparar() {
        repo = new Fakes.Estadisticas();
        catalogo = new Fakes.Catalogo()
                .conProvincia("1606", "UCAYALI", "LORETO")
                .conDistrito("160601", "CONTAMANA", "LORETO");
        servicio = new EstadisticasService(repo, catalogo);
    }

    @Test
    void resumenCalculaDamnificadosPorEmergenciaYPeligroMasFrecuente() {
        repo.totales = new TotalesIndicadores(200, 1, 0, 0, 500, 0, 0, 0, 0, 0);
        repo.peligros = List.of(conteo("SISMO", 20), conteo("LLUVIA INTENSA", 180));

        var r = servicio.resumen(FiltroEmergencias.sinFiltros());

        assertThat(r.damnificadosPorEmergencia()).isEqualTo(2.5);
        assertThat(r.peligroMasFrecuente()).isEqualTo("LLUVIA INTENSA");
    }

    @Test
    void resumenSinDatosNoDivideEntreCero() {
        var r = servicio.resumen(null);
        assertThat(r.damnificadosPorEmergencia()).isZero();
        assertThat(r.peligroMasFrecuente()).isNull();
        assertThat(r.variacionInteranual()).isNull();
        assertThat(r.filtro()).isEqualTo(FiltroEmergencias.sinFiltros());
    }

    @Test
    void variacionInteranualConAniosConsecutivos() {
        repo.anios = List.of(conteo("2025", 150), conteo("2024", 100)); // desordenados a propósito
        var r = servicio.resumen(null);
        assertThat(r.ultimoAnio()).isEqualTo(2025);
        assertThat(r.variacionInteranual()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void sinVariacionSiLosAniosNoSonConsecutivos() {
        repo.anios = List.of(conteo("2020", 100), conteo("2025", 150));
        assertThat(servicio.resumen(null).variacionInteranual()).isNull();
    }

    @Test
    void porAnioOrdenaCronologicamente() {
        repo.anios = List.of(conteo("2010", 1), conteo("2003", 1), conteo("2025", 1));
        assertThat(servicio.porAnio(null)).extracting(ConteoAgrupado::clave)
                .containsExactly("2003", "2010", "2025");
    }

    @Test
    void porPeligroMuestraOtrosSinLaZ() {
        repo.peligros = List.of(conteo("zOTROS", 5));
        assertThat(servicio.porPeligro(null).getFirst().nombre()).isEqualTo("OTROS");
        assertThat(servicio.porPeligro(null).getFirst().clave()).isEqualTo("zOTROS");
    }

    @Test
    void porTerritorioUsaNombreOficialDelCatalogoYOrdenaDescendente() {
        // En el CSV el ubigeo 160601 aparece mal escrito como "CONTUMAZA"
        repo.territorios.put(NivelTerritorial.DISTRITO, List.of(
                new ConteoAgrupado("160601", "CONTUMAZA", "1606", 189, 0, 0, 0, 0),
                conteo("130112", 11)));

        var r = servicio.porTerritorio(null, NivelTerritorial.DISTRITO);

        assertThat(r).extracting(ConteoAgrupado::nombre).containsExactly("CONTAMANA", "130112");
    }
}
