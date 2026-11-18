package pe.alertaperu.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import pe.alertaperu.domain.exception.FiltroInvalidoException;
import pe.alertaperu.domain.exception.RecursoNoEncontradoException;
import pe.alertaperu.domain.model.Emergencia;
import pe.alertaperu.domain.model.FiltroEmergencias;
import pe.alertaperu.domain.model.Indicadores;
import pe.alertaperu.domain.model.Paginacion;
import pe.alertaperu.domain.model.Peligro;
import pe.alertaperu.domain.model.Territorio;
import pe.alertaperu.domain.model.Ubicacion;

class CatalogoYConsultaServiceTest {

    private final Fakes.Catalogo catalogo = new Fakes.Catalogo()
            .conDepartamento("LIMA METROPOLITANA", "15")
            .conDepartamento("LIMA PROVINCIAS", "15")
            .conDepartamento("AMAZONAS", "01")
            .conProvincia("1501", "LIMA", "LIMA METROPOLITANA")
            .conProvincia("1510", "YAUYOS", "LIMA PROVINCIAS")
            .conProvincia("1502", "BARRANCA", "LIMA PROVINCIAS")
            .conDistrito("150101", "LIMA", "LIMA METROPOLITANA");

    private final CatalogoService catalogos = new CatalogoService(catalogo);

    @Test
    void departamentosOrdenadosPorNombre() {
        assertThat(catalogos.departamentos()).extracting(Territorio::nombre)
                .containsExactly("AMAZONAS", "LIMA METROPOLITANA", "LIMA PROVINCIAS");
    }

    @Test
    void provinciasSeparanLimaMetropolitanaDeLimaProvincias() {
        assertThat(catalogos.provincias("LIMA PROVINCIAS")).extracting(Territorio::codigo)
                .containsExactly("1502", "1510");
        assertThat(catalogos.provincias("LIMA METROPOLITANA")).extracting(Territorio::codigo)
                .containsExactly("1501");
    }

    @Test
    void provinciasDeDepartamentoInexistente() {
        assertThatThrownBy(() -> catalogos.provincias("ATLANTIDA")).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> catalogos.provincias(" ")).isInstanceOf(FiltroInvalidoException.class);
    }

    @Test
    void distritosValidanUbigeoDeProvincia() {
        assertThat(catalogos.distritos("1501")).hasSize(1);
        assertThatThrownBy(() -> catalogos.distritos("15")).isInstanceOf(FiltroInvalidoException.class);
        assertThatThrownBy(() -> catalogos.distritos("9999")).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void obtenerEmergenciaExistenteEInexistente() {
        var repo = new Fakes.Emergencias();
        var e = new Emergencia("351-2003-02-11-010101", "351", LocalDate.of(2003, 2, 11),
                new Ubicacion("010101", "01", "0101", "AMAZONAS", "CHACHAPOYAS", "CHACHAPOYAS", "SIERRA"),
                new Peligro("LLUVIA INTENSA", "ORIGEN NATURAL"), Indicadores.vacios());
        repo.datos.put(e.id(), e);
        var servicio = new ConsultarEmergenciasService(repo);

        assertThat(servicio.obtener(" 351-2003-02-11-010101 ")).isEqualTo(e);
        assertThatThrownBy(() -> servicio.obtener("no-existe")).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.obtener("")).isInstanceOf(FiltroInvalidoException.class);
    }

    @Test
    void buscarSinFiltroUsaFiltroVacio() {
        var repo = new Fakes.Emergencias();
        new ConsultarEmergenciasService(repo).buscar(null, new Paginacion(0, 20, null, true));
        assertThat(repo.ultimoFiltro).isEqualTo(FiltroEmergencias.sinFiltros());
    }
}
