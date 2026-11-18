package pe.alertaperu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifica la regla central de la arquitectura hexagonal:
 * domain/ y application/ no dependen de Spring, MongoDB ni de la infraestructura.
 */
class ArquitecturaHexagonalTest {

    private static final Path RAIZ = Path.of("src/main/java/pe/alertaperu");
    private static final List<String> PROHIBIDOS = List.of(
            "import org.springframework",
            "import com.mongodb",
            "import org.bson",
            "import jakarta.",
            "import pe.alertaperu.infrastructure");

    @Test
    @DisplayName("El dominio no depende de frameworks ni de la infraestructura")
    void dominioIndependiente() throws IOException {
        assertThat(violaciones(RAIZ.resolve("domain"))).isEmpty();
    }

    @Test
    @DisplayName("La capa de aplicación no depende de frameworks ni de la infraestructura")
    void aplicacionIndependiente() throws IOException {
        assertThat(violaciones(RAIZ.resolve("application"))).isEmpty();
    }

    @Test
    @DisplayName("El dominio no depende de la capa de aplicación")
    void dominioNoConoceAplicacion() throws IOException {
        try (Stream<Path> archivos = Files.walk(RAIZ.resolve("domain"))) {
            List<String> v = archivos.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> contenido(p).contains("import pe.alertaperu.application"))
                    .map(Path::toString).toList();
            assertThat(v).isEmpty();
        }
    }

    private static List<String> violaciones(Path carpeta) throws IOException {
        try (Stream<Path> archivos = Files.walk(carpeta)) {
            return archivos.filter(p -> p.toString().endsWith(".java"))
                    .flatMap(p -> contenido(p).lines()
                            .filter(l -> PROHIBIDOS.stream().anyMatch(l.trim()::startsWith))
                            .map(l -> p.getFileName() + ": " + l.trim()))
                    .toList();
        }
    }

    private static String contenido(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
