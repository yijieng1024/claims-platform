# reporting-service image. Build context: the repository root (docker-compose.yml sets it), because the
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
RUN ./mvnw -B -q -pl services/reporting-service -am dependency:go-offline -DexcludeGroupIds=com.example.claims

# 2) The sources. Tests are skipped here: they run locally / in CI, not on the reviewer's machine.
COPY libs libs
COPY services/reporting-service/src services/reporting-service/src
RUN ./mvnw -B -q -pl services/reporting-service -am package -DskipTests

# ---- runtime stage: slim JRE 25, non-root ----
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /src/services/reporting-service/target/reporting-service-*.jar app.jar
USER app
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
