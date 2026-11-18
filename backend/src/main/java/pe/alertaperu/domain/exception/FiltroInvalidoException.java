package pe.alertaperu.domain.exception;

/** Un filtro o parámetro de consulta no cumple las reglas del dominio. */
public class FiltroInvalidoException extends RuntimeException {

    public FiltroInvalidoException(String mensaje) {
        super(mensaje);
    }
}
