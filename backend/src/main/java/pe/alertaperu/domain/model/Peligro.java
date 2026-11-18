package pe.alertaperu.domain.model;

/**
 * Peligro que originó la emergencia.
 *
 * @param nombre valor original de la fuente (por ejemplo "LLUVIA INTENSA" o "zOTROS")
 * @param tipo   ORIGEN NATURAL o INDUCIDOS POR LA ACCIÓN HUMANA
 */
public record Peligro(String nombre, String tipo) {

    /**
     * Nombre para mostrar. La fuente escribe "zOTROS" (con una "z" para que
     * aparezca al final al ordenar); se presenta como "OTROS".
     */
    public String etiqueta() {
        return etiquetaDe(nombre);
    }

    public static String etiquetaDe(String nombre) {
        if (nombre != null && nombre.startsWith("z") && nombre.length() > 1) {
            return nombre.substring(1);
        }
        return nombre;
    }
}
