# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies first
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/target/tax-platform-*.jar app.jar

EXPOSE 8080

# Container-aware JVM. MaxRAMPercentage keeps the heap within the (small)
# memory limit set on the container in docker-compose.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
