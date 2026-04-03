# ── Build stage ────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# ── Runtime stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S dfs && adduser -S dfs -G dfs
RUN mkdir -p /data/chunks && chown dfs:dfs /data/chunks
COPY --from=build /app/target/*.jar app.jar
RUN chown dfs:dfs app.jar
USER dfs
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
