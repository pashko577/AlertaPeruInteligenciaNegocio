package pe.alertaperu.infrastructure.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.alertaperu.application.port.in.ObtenerInfoCargaUseCase;
import pe.alertaperu.domain.model.InfoCarga;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.InfoCargaDto;

/** Adaptador de entrada: vigencia del dataset para "Sobre los datos". */
@RestController
public class InfoCargaController {

    private final ObtenerInfoCargaUseCase infoCarga;

    public InfoCargaController(ObtenerInfoCargaUseCase infoCarga) {
        this.infoCarga = infoCarga;
    }

    @GetMapping("/api/metadatos/ultima-carga")
    public InfoCargaDto ultimaCarga() {
        InfoCarga i = infoCarga.ultimaCarga();
        return new InfoCargaDto(
                i.fechaCarga() == null ? null : i.fechaCarga().toString(),
                i.archivo(), i.filasLeidas(), i.documentos(),
                i.sha256() == null ? null : i.sha256().substring(0, Math.min(12, i.sha256().length())));
    }
}
