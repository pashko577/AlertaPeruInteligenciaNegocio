package pe.alertaperu.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import pe.alertaperu.domain.exception.FiltroInvalidoException;

class FiltroEmergenciasTest {

    private static FiltroEmergencias filtro(Integer desde, Integer hasta, String prov, String dist) {
        return new FiltroEmergencias(desde, hasta, null, prov, dist, null, null, null);
    }

    @Test
    void textosVaciosSeTratanComoSinFiltro() {
        var f = new FiltroEmergencias(null, null, "  ", "", null, " ", null, null);
        assertThat(f).isEqualTo(FiltroEmergencias.sinFiltros());
    }

    @Test
    void recortaEspacios() {
        var f = new FiltroEmergencias(null, null, " CUSCO ", null, null, null, null, null);
        assertThat(f.departamento()).isEqualTo("CUSCO");
    }

    @Test
    void rangoDeAniosInvertidoEsInvalido() {
        assertThatThrownBy(() -> filtro(2020, 2010, null, null))
                .isInstanceOf(FiltroInvalidoException.class);
    }

    @Test
    void anioFueraDeRangoEsInvalido() {
        assertThatThrownBy(() -> filtro(1500, null, null, null))
                .isInstanceOf(FiltroInvalidoException.class);
    }

    @Test
    void provinciaDebeTenerCuatroDigitos() {
        assertThatThrownBy(() -> filtro(null, null, "080", null)).isInstanceOf(FiltroInvalidoException.class);
        assertThatThrownBy(() -> filtro(null, null, "08a1", null)).isInstanceOf(FiltroInvalidoException.class);
        assertThat(filtro(null, null, "0801", null).provincia()).isEqualTo("0801");
    }

    @Test
    void distritoDebeTenerSeisDigitosYConservarCeroInicial() {
        assertThatThrownBy(() -> filtro(null, null, null, "10101")).isInstanceOf(FiltroInvalidoException.class);
        assertThat(filtro(null, null, null, "010101").distrito()).isEqualTo("010101");
    }

    @Test
    void distritoDebePertenecerALaProvincia() {
        assertThatThrownBy(() -> filtro(null, null, "0801", "030101"))
                .isInstanceOf(FiltroInvalidoException.class)
                .hasMessageContaining("no pertenece");
        assertThat(filtro(null, null, "0801", "080101").distrito()).isEqualTo("080101");
    }

    @Test
    void paginacionValidaLimites() {
        assertThatThrownBy(() -> new Paginacion(-1, 20, null, true)).isInstanceOf(FiltroInvalidoException.class);
        assertThatThrownBy(() -> new Paginacion(0, 101, null, true)).isInstanceOf(FiltroInvalidoException.class);
        var p = new Paginacion(3, 20, null, true);
        assertThat(p.orden()).isEqualTo(Paginacion.CampoOrden.FECHA);
        assertThat(p.desplazamiento()).isEqualTo(60);
    }

    @Test
    void etiquetaDeOtros() {
        assertThat(new Peligro("zOTROS", "INDUCIDOS POR LA ACCIÓN HUMANA").etiqueta()).isEqualTo("OTROS");
        assertThat(new Peligro("SISMO", "ORIGEN NATURAL").etiqueta()).isEqualTo("SISMO");
    }

    @Test
    void indicadorAusenteEsSinDatoNoCero() {
        var i = new Indicadores(java.util.Map.of("educacion", java.util.Map.of("centrosDestruidos", 0)));
        assertThat(i.valor("educacion", "centrosDestruidos")).contains(0);
        assertThat(i.valor("educacion", "aulasAfectadas")).isEmpty();
        assertThat(i.valor("ayuda", "peso")).isEmpty();
    }
}
