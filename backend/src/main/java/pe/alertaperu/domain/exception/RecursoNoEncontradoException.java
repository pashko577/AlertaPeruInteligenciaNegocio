package pe.alertaperu.domain.exception;

/** El recurso solicitado (emergencia, territorio) no existe. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
