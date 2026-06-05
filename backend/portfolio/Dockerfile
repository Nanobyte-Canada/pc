# Build stage
FROM gradle:8.10-jdk21-alpine AS build
WORKDIR /app

# Copy gradle files first for better caching
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application
RUN gradle build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Install CA certificates for HTTPS connections
RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates curl wget && \
    rm -rf /var/lib/apt/lists/*

# Import custom CA certificates (e.g. corporate proxy CA) into JVM truststore
COPY certs/*.crt /usr/local/share/ca-certificates/
RUN update-ca-certificates && \
    JAVA_CACERTS=$(find /opt -name cacerts -type f 2>/dev/null | head -1) && \
    if [ -n "$JAVA_CACERTS" ]; then \
      for cert in /usr/local/share/ca-certificates/*.crt; do \
        [ -f "$cert" ] && keytool -importcert -noprompt -trustcacerts \
          -keystore "$JAVA_CACERTS" -storepass changeit \
          -alias "$(basename $cert .crt)" -file "$cert" 2>/dev/null || true; \
      done; \
    fi

# Create non-root user for security
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -s /bin/bash appuser

# Copy the built artifact
COPY --from=build /app/build/libs/app.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl --fail --silent http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
