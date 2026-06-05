FROM gradle:8.10-jdk21-alpine AS build
WORKDIR /build

# Import Invesdwin Maven repo full certificate chain into JVM truststore (for TWS API dependency)
RUN apk add --no-cache openssl && \
    JAVA_CACERTS=$(find /opt -name cacerts -type f 2>/dev/null | head -1) && \
    echo | openssl s_client -showcerts -connect invesdwin.de:443 -servername invesdwin.de 2>/dev/null | \
    awk 'BEGIN{n=0} /BEGIN CERTIFICATE/{n++; fn="/tmp/invesdwin-cert-"n".pem"} {if(fn) print > fn} /END CERTIFICATE/{fn=""}' && \
    for cert in /tmp/invesdwin-cert-*.pem; do \
      [ -f "$cert" ] && keytool -import -trustcacerts -keystore "$JAVA_CACERTS" -storepass changeit \
        -noprompt -alias "$(basename $cert .pem)" -file "$cert" 2>/dev/null || true; \
    done && \
    rm -f /tmp/invesdwin-cert-*.pem

# Copy common module for composite build
COPY common ./common

# Copy market-data service
COPY market-data ./market-data

WORKDIR /build/market-data
RUN gradle dependencies --no-daemon || true
RUN gradle build -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates curl wget && \
    rm -rf /var/lib/apt/lists/*
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -s /bin/bash appuser
COPY --from=build /build/market-data/build/libs/app.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl --fail --silent http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
