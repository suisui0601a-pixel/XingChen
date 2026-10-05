FROM node:24.19.0-bookworm-slim@sha256:a9f5f7c91a432850b2a8a7797adf5eadb6c733ceed61167806cee7ea7fbc29df AS frontend-tools
RUN npm install --global npm@11.17.0 \
    && test "$(node --version)" = "v24.19.0" \
    && test "$(npm --version)" = "11.17.0"

FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /app
COPY --from=frontend-tools /usr/local/bin/node /usr/local/bin/node
COPY --from=frontend-tools /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
RUN ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && test "$(node --version)" = "v24.19.0" \
    && test "$(npm --version)" = "11.17.0"
COPY . .
ARG XINGCHEN_GIT_COMMIT=unavailable
ARG XINGCHEN_VERSION=0.1.0-SNAPSHOT
RUN ./gradlew --no-daemon bootJar -PXINGCHEN_GIT_COMMIT=${XINGCHEN_GIT_COMMIT} -PXINGCHEN_VERSION=${XINGCHEN_VERSION}
RUN java container/ExtractSqliteNative.java /app/build/libs/xingchen-core-*.jar /tmp/sqlite-native
RUN mkdir -p /tmp/healthcheck \
    && javac --release 21 -d /tmp/healthcheck container/Healthcheck.java \
    && jar --create --file /tmp/healthcheck.jar --main-class Healthcheck -C /tmp/healthcheck .

FROM eclipse-temurin:21.0.12.1_1-jre-noble AS runtime
ARG XINGCHEN_VERSION=0.1.0-SNAPSHOT
ARG XINGCHEN_GIT_COMMIT=unavailable
LABEL org.opencontainers.image.title="XingChen Core" \
      org.opencontainers.image.version="${XINGCHEN_VERSION}" \
      org.opencontainers.image.revision="${XINGCHEN_GIT_COMMIT}"
RUN groupadd --gid 10001 xingchen \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin xingchen \
    && install -d -o 10001 -g 10001 /data /data/db /data/config /data/prompts /data/logs /data/assets /data/assets/stickers /tmp
WORKDIR /app
COPY --from=build --chown=10001:10001 /app/build/libs/xingchen-core-*.jar /app/xingchen.jar
COPY --from=build --chown=10001:10001 /tmp/healthcheck.jar /app/healthcheck.jar
COPY --from=build /tmp/sqlite-native/ /app/native/
ENV SPRING_PROFILES_ACTIVE=container
ENV HOME=/tmp
USER 10001:10001
EXPOSE 3200
HEALTHCHECK --interval=30s --timeout=4s --start-period=60s --retries=3 CMD ["java", "-cp", "/app/healthcheck.jar", "Healthcheck"]
ENTRYPOINT ["java", "-Djava.io.tmpdir=/tmp", "-Duser.home=/tmp", "-Dorg.sqlite.lib.path=/app/native", "-jar", "/app/xingchen.jar"]
