package pe.alertaperu.infrastructure.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import pe.alertaperu.domain.exception.FiltroInvalidoException;
import pe.alertaperu.domain.exception.RecursoNoEncontradoException;
import pe.alertaperu.infrastructure.adapter.in.web.dto.Respuestas.ErrorDto;

/** Convierte excepciones en respuestas JSON claras, sin exponer detalles internos. */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(FiltroInvalidoException.class)
    ResponseEntity<ErrorDto> filtroInvalido(FiltroInvalidoException e) {
        return respuesta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<ErrorDto> noEncontrado(RecursoNoEncontradoException e) {
        return respuesta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ErrorDto> enlace(BindException e) {
        String campo = e.getFieldError() != null ? e.getFieldError().getField() : "parámetro";
        return respuesta(HttpStatus.BAD_REQUEST, "Valor inválido para '" + campo + "'");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorDto> tipo(MethodArgumentTypeMismatchException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "Valor inválido para '" + e.getName() + "'");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorDto> faltante(MissingServletRequestParameterException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "Falta el parámetro '" + e.getParameterName() + "'");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorDto> recurso(NoResourceFoundException e) {
        return respuesta(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorDto> inesperado(Exception e) {
        log.error("Error inesperado", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno. Revisa el log del servidor.");
    }

    private static ResponseEntity<ErrorDto> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(new ErrorDto(estado.value(), estado.getReasonPhrase(), mensaje));
    }
}
