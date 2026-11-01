# Stage 1: build the jar. This stage holds the JDK, Maven and source code,
# and none of it reaches the final image.
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copy the build files first. Docker reuses this cached layer until pom.xml
# changes, so dependencies are not downloaded again for every code change.
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN sh ./mvnw -B -ntp dependency:go-offline

COPY src src
# CI runs the tests, so they are not repeated during the image build.
RUN sh ./mvnw -B -ntp package -DskipTests

# Stage 2: the image that actually runs, with only a JRE and the jar.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as an unprivileged user, so a compromised application is not root.
RUN addgroup -S opstrack && adduser -S -G opstrack opstrack
COPY --from=build /app/target/opstrack-*.jar app.jar
USER opstrack

EXPOSE 8080

# Use most of the memory limit given to the container.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
