# syntax=docker/dockerfile:1
# The petkit-local mock server as a small image. Build from the REPO ROOT, because the server
# serves (and its build copies) the vendored contract api/openapi.yaml:
#
#   docker build -f mock-server/Dockerfile -t petkit-local-mock .
#   docker run --rm -p 8097:8097 petkit-local-mock
#
# Base images are pinned by digest; Dependabot (docker, /mock-server) raises them.

FROM eclipse-temurin:17-jdk-noble@sha256:1bedb04434bd275ef072355e9caec2a22be5240fbe82ad51a59323f2bbcab4a6 AS build
WORKDIR /src/mock-server
# The wrapper and the build scripts first, so the Gradle distribution and the dependencies are a
# cached layer that only a version change invalidates.
COPY mock-server/gradlew mock-server/settings.gradle.kts mock-server/build.gradle.kts mock-server/gradle.properties ./
COPY mock-server/gradle ./gradle
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY api /src/api
COPY mock-server/src ./src
RUN ./gradlew --no-daemon --quiet installDist

FROM eclipse-temurin:17-jre-alpine@sha256:e2ba4e947bd5e236a9866fbdda03e4936036cb9e9d16ea0d84d103d6634a5c21
RUN addgroup -S mock && adduser -S -G mock -H -s /sbin/nologin mock
COPY --from=build /src/mock-server/build/install/petkit-local-mock /opt/petkit-local-mock
USER mock
# Every option is also a PETKIT_MOCK_* variable (README.md); the defaults are the real app API's.
ENV JAVA_OPTS="-Djava.awt.headless=true -XX:MaxRAMPercentage=75"
EXPOSE 8097
ENTRYPOINT ["/opt/petkit-local-mock/bin/petkit-local-mock"]
