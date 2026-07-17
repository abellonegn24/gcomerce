# -----------------------------------------------------------------------------
# Gadaa Istar Backend — Multi-stage Docker build
#
# Module-agnostic: pass --build-arg MODULE=api-storefront (default) or
# --build-arg MODULE=api-admin to select which Quarkus runtime to package.
# The exposed port is set via --build-arg PORT=8080 / 8081 accordingly.
# -----------------------------------------------------------------------------

ARG MODULE=api-storefront
ARG PORT=8080

FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /workspace

# Copy Gradle wrapper first for better layer caching
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle gradle.properties ./

# Copy every module's build script — cheap layer, big cache win
COPY common/build.gradle common/
COPY persistence/build.gradle persistence/
COPY web-common/build.gradle web-common/
COPY api-storefront/build.gradle api-storefront/
COPY api-admin/build.gradle api-admin/

# Warm up dependency cache
RUN chmod +x ./gradlew && ./gradlew --no-daemon help >/dev/null 2>&1 || true

# Copy source for every module — Quarkus reads dependent-project sources
# transitively so we can't cherry-pick just one API module's sources here.
COPY common/src common/src
COPY persistence/src persistence/src
COPY web-common/src web-common/src
COPY api-storefront/src api-storefront/src
COPY api-admin/src api-admin/src

# Build only the selected module's runnable JAR
RUN ./gradlew --no-daemon :${MODULE}:build -x test

# Move the module's quarkus-app to a stable path so the runtime stage
# doesn't need to know about MODULE anymore.
RUN mkdir -p /out && cp -r ${MODULE}/build/quarkus-app/. /out/

# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
ARG PORT
WORKDIR /app

# Non-root user
RUN groupadd --system --gid 1001 gadaa \
 && useradd  --system --uid 1001 --gid gadaa --shell /bin/false gadaa

COPY --from=build /out/ /app/

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENV LANG=en_US.UTF-8
ENV PORT=${PORT}

USER gadaa
EXPOSE ${PORT}

HEALTHCHECK --interval=15s --timeout=3s --retries=5 --start-period=30s \
  CMD wget -qO- http://localhost:${PORT}/q/health/live | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/quarkus-run.jar"]
