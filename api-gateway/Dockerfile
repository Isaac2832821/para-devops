# ──────────────────────────────────────────────────────────────────
# Stage 1: Build
# ──────────────────────────────────────────────────────────────────
FROM public.ecr.aws/docker/library/maven:3.9.6-eclipse-temurin-17-alpine AS builder

LABEL stage="builder"
LABEL maintainer="Colegio Bernardo O'Higgins"

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B -q

COPY src ./src
RUN mvn package -DskipTests -B -q

# ──────────────────────────────────────────────────────────────────
# Stage 2: Runtime — JRE mínimo con usuario no-root
# ──────────────────────────────────────────────────────────────────
FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre-alpine AS production

LABEL maintainer="Colegio Bernardo O'Higgins"
LABEL description="API Gateway — Punto de entrada único del sistema"

RUN addgroup -g 1001 -S appgroup && \
    adduser  -u 1001 -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

# Puerto del API Gateway
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
