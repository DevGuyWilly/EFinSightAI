# Render (and most hosts) have no native Java runtime, so the backend ships as a container.

# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
# src/main/resources/application.properties is gitignored because it holds secrets, so the image is built from
# the committed template. Everything secret or environment-specific (database, JWT, TrueLayer, Gemini) is
# supplied at runtime as environment variables, which override the file (see render.yaml).
RUN chmod +x mvnw \
 && cp src/main/resources/application.properties.example src/main/resources/application.properties \
 && ./mvnw -B clean package -DskipTests

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /app/target/e-finsight-*.jar app.jar
USER appuser
# Keep the heap inside small instances (Render's free plan has 512 MB).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
# Render injects PORT (default 10000) and requires the service to listen on it.
CMD ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
