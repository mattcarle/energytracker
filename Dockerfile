# Backend image: builds the Spring Boot jar, then runs it on a slim JRE. Runs on plain HTTP -
# see docker-compose.yml, TLS is terminated by the caddy service in front of this one.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Cache dependencies in their own layer, so a source-only change doesn't re-download the world.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN useradd --system --create-home --home-dir /app appuser \
    && mkdir -p /data \
    && chown -R appuser:appuser /app /data

COPY --from=build /app/target/energytracker-0.0.1-SNAPSHOT.jar app.jar

USER appuser
EXPOSE 8080
VOLUME /data
ENTRYPOINT ["java", "-jar", "app.jar"]
