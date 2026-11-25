# auth-service image. Build context: the repository root (docker-compose.yml sets it), because the
# service is built together with the parent POM and libs/*.

# ---- build stage: Maven wrapper on JDK 25 ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# 1) Only the wrapper and the POMs. Every module's POM is needed because the parent lists them all.
#    The dependency download below is cached as its own layer and only re-runs when a pom.xml changes,
#    so editing Java code doesn't download the internet again. Our own libs aren't downloadable yet,
#    hence -DexcludeGroupIds.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY libs/claims-events/pom.xml libs/claims-events/
COPY libs/web-common/pom.xml libs/web-common/
COPY services/auth-service/pom.xml services/auth-service/
COPY services/claims-service/pom.xml services/claims-service/
COPY services/reporting-service/pom.xml services/reporting-service/
RUN ./mvnw -B -q -pl services/auth-service -am dependency:go-offline -DexcludeGroupIds=com.example.claims

# 2) The sources. Tests are skipped here: they run locally / in CI, not on the reviewer's machine.
COPY libs libs
COPY services/auth-service/src services/auth-service/src
RUN ./mvnw -B -q -pl services/auth-service -am package -DskipTests

# ---- runtime stage: slim JRE 25, non-root ----
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app app
# the RSA signing key lives on a volume (compose) so tokens stay valid across restarts
RUN mkdir -p /data/keys && chown app:app /data/keys
WORKDIR /app
COPY --from=build /src/services/auth-service/target/auth-service-*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
