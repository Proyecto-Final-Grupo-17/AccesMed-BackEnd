# AccesMed backend — build multi-stage.
# Etapa 1: compila el JAR con Maven. Etapa 2: imagen liviana solo con el runtime.
#
# Build (desde la raíz del repo):
#   docker build -f docker/Dockerfile -t accesmed-backend:local .
# Run:
#   docker run --rm -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev accesmed-backend:local

# --- Etapa de build ---
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Cache de dependencias: primero el pom, luego el código.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Etapa de runtime ---
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Usuario no-root por seguridad.
RUN addgroup -S accesmed && adduser -S accesmed -G accesmed
USER accesmed

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS=""

# Healthcheck contra actuator (requiere spring-boot-starter-actuator).
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
