# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

# Cache dependency resolution separately from source compilation
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B -q

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -B -q -DskipTests=true

# ── Stage 2: extract Spring Boot layers ───────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /extract
# Globbed rather than named: `package` leaves exactly one jar here (the repackaged fat jar - its
# pre-repackage copy is `.jar.original`, which `*.jar` does not match), so a change of artifactId
# or version cannot silently break the build again. The previous name was a leftover from another
# project and matched nothing, which is why `app.jar` arrived as an empty directory.
COPY --from=build /build/target/*.jar application.jar
# `tools`, not `layertools`: the layertools jarmode was deprecated in Spring Boot 3.3 and removed
# in 4.x, so the old invocation no longer extracts anything.
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ── Stage 3: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

#USER root
# Patch OS packages to pick up security fixes published in the Alpine repos at build time
# (e.g. OpenSSL/libcrypto3, libssl3). Upgrade the OpenSSL libraries explicitly so the layer
# clearly targets them, then upgrade everything else. Build with `--pull --no-cache` so a
# refreshed base image and package index are used instead of stale cached layers.
# Upgrade the explicitly-targeted libraries first so the layer clearly names them
# (OpenSSL: libcrypto3/libssl3; expat: libexpat -> CVE-2026-50219 / CVE-2026-56132 fixed in
# 2.8.2-r0; p11-kit: CVE-2026-2100 -> 0.26.2-r0; sqlite: sqlite-libs -> CVE-2026-11822 memory
# corruption / CVE-2026-11824 heap buffer overflow, fixed in 3.53.4-r0), then upgrade everything
# else to pick up any remaining security fixes.
#
# APK_PATCH_BUST busts the Docker build cache for this layer so it always re-runs against a fresh
# package index instead of reusing a stale cached layer (which would keep already-fixed CVEs in the
# image). The build scripts pass the current date; pass a changing value from CI too, e.g.
# `--build-arg APK_PATCH_BUST=$(date +%Y%m%d)`.
ARG APK_PATCH_BUST=0
RUN echo "apk security-patch layer, bust=${APK_PATCH_BUST}" && \
    apk update && \
    apk upgrade --no-cache libcrypto3 libssl3 libexpat p11-kit p11-kit-trust sqlite-libs && \
    apk upgrade --no-cache && \
    rm -rf /var/cache/apk/*
#USER 101

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app
RUN chown appuser:appgroup /app

COPY --from=extract --chown=appuser:appgroup /extract/extracted/dependencies/ ./
COPY --from=extract --chown=appuser:appgroup /extract/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=appuser:appgroup /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=appuser:appgroup /extract/extracted/application/ ./

USER appuser

EXPOSE 8080

# Probes the actuator health endpoint, so the container is reported healthy only once Flyway has
# migrated, the context has refreshed and the datasource answers - not merely when the JVM is up.
# wget is BusyBox's, already in the Alpine base; curl is not installed and `apk add wget` would
# replace the builtin with GNU wget, whose flags differ. Shell form on purpose: it resolves
# SERVER_PORT at runtime, so overriding the port does not leave the check pointing at 8080.
# start-period covers a cold start and the migrations, and is not counted as a failure.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health" || exit 1

# The application layer holds a thin application.jar that references the extracted
# libraries, so it is launched directly. JarLauncher belonged to the layertools layout
# and has no BOOT-INF to launch here.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "application.jar"]