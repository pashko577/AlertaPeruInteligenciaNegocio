# Imagen para desplegar ALERTA PERÚ (API + página) en Render u otro servicio de contenedores.
# La base de datos NO va aquí: está en MongoDB Atlas. La URI llega por la variable MONGODB_URI.

# --- 1. Compilación -------------------------------------------------------
FROM eclipse-temurin:25-jdk AS compilacion
WORKDIR /app
# Primero solo lo necesario para bajar dependencias (se cachea entre despliegues)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sh ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Las pruebas se ejecutan en local (mvnw test); aquí solo se empaqueta
RUN sh ./mvnw -B -q package -DskipTests && cp target/alerta-peru-*.jar app.jar

# --- 2. Ejecución (solo JRE, imagen más liviana) -----------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 alerta
COPY --from=compilacion /app/app.jar app.jar
USER alerta
# Ajustado al plan gratuito de Render (512 MB de RAM)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
# Render define PORT; application.yml lo usa (8080 si no existe)
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
