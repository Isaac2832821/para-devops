# ──────────────────────────────────────────────────────────────────
# Stage 1: Build — Compilación con Maven
# Imagen base: maven:3.9.6-eclipse-temurin-17-alpine
# ──────────────────────────────────────────────────────────────────
FROM public.ecr.aws/docker/library/maven:3.9.6-eclipse-temurin-17-alpine AS builder

LABEL stage="builder"
LABEL maintainer="Colegio Bernardo O'Higgins"

WORKDIR /app

# Descargar dependencias primero (capa cacheada si pom.xml no cambia)
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Compilar el proyecto
COPY src ./src
RUN mvn package -DskipTests -B -q

# ──────────────────────────────────────────────────────────────────
# Stage 2: Runtime — JRE mínimo con usuario no-root
# Imagen base: eclipse-temurin:17-jre-alpine (sin JDK ni Maven)
# ──────────────────────────────────────────────────────────────────
FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre-alpine AS production

LABEL maintainer="Colegio Bernardo O'Higgins"
LABEL description="Eureka Server — Service Discovery"

# Crear usuario no-root (principio de mínimo privilegio)
RUN addgroup -g 1001 -S appgroup && \
    adduser  -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copiar SOLO el JAR compilado desde el builder
COPY --from=builder /app/target/*.jar app.jar

# Ajustar propietario del JAR
RUN chown appuser:appgroup app.jar

# Cambiar al usuario no-root
USER appuser

# Puerto del servidor Eureka
EXPOSE 8761

# Optimización JVM para contenedores:
# -Djava.security.egd evita bloqueos de entropía en /dev/random
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
