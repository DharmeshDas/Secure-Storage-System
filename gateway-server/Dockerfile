# ── Stage 1: Build ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Run ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd -r dfs && useradd -r -g dfs dfs
COPY --from=build /app/target/gateway-server-1.0.0.jar app.jar
RUN chown dfs:dfs app.jar
USER dfs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
