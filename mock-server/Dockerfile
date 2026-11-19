# syntax=docker/dockerfile:1
# The petkit-local mock server as a small image. Build from the REPO ROOT, because the server
# serves (and its build copies) the vendored contract api/openapi.yaml:
#
#   docker build -f mock-server/Dockerfile -t petkit-local-mock .
#   docker run --rm -p 8097:8097 petkit-local-mock
#
# Base images are pinned by digest; Dependabot (docker, /mock-server) raises them.

FROM eclipse-temurin:25-jdk-noble@sha256:80f67fb3d6f76e2d817d98a4cc2a9cf05f054d4e6adc5e2144bde671d722e63f AS build
WORKDIR /src/mock-server
# The wrapper and the build scripts first, so the Gradle distribution and the dependencies are a
# cached layer that only a version change invalidates.
COPY mock-server/gradlew mock-server/settings.gradle.kts mock-server/build.gradle.kts mock-server/gradle.properties ./
COPY mock-server/gradle ./gradle
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY api /src/api
COPY mock-server/src ./src
RUN ./gradlew --no-daemon --quiet installDist

FROM eclipse-temurin:25-jre-alpine@sha256:e1898fd6a11fc965a994d8ace186fd3585aeb636cf8f05f47a723fcfee0b0918
RUN addgroup -S mock && adduser -S -G mock -H -s /sbin/nologin mock
COPY --from=build /src/mock-server/build/install/petkit-local-mock /opt/petkit-local-mock
USER mock
# Every option is also a PETKIT_MOCK_* variable (README.md); the defaults are the real app API's.
ENV JAVA_OPTS="-Djava.awt.headless=true -XX:MaxRAMPercentage=75"
# 8443: the HTTPS port, when PETKIT_MOCK_TLS_PORT sets it (e2e/docker-compose.yml).
EXPOSE 8097 8443
ENTRYPOINT ["/opt/petkit-local-mock/bin/petkit-local-mock"]
