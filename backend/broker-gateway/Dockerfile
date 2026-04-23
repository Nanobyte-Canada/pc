FROM gradle:8.10-jdk21-alpine AS build
WORKDIR /build

# Copy common module for composite build
COPY common ./common

# Copy broker-gateway service
COPY broker-gateway ./broker-gateway

WORKDIR /build/broker-gateway
RUN gradle dependencies --no-daemon || true
RUN gradle build -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates curl wget && \
    rm -rf /var/lib/apt/lists/*
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -s /bin/bash appuser
COPY --from=build /build/broker-gateway/build/libs/app.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8084
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl --fail --silent http://localhost:8084/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
