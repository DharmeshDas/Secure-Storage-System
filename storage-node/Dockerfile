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

# Create storage directories for all 3 nodes
RUN mkdir -p /data/dfs/node-1 /data/dfs/node-2 /data/dfs/node-3

RUN groupadd -r dfs && useradd -r -g dfs dfs
RUN chown -R dfs:dfs /data/dfs

COPY --from=build /app/target/storage-node-1.0.0.jar app.jar
RUN chown dfs:dfs app.jar
USER dfs
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]
