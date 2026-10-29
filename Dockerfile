# Etapa 1: compilar Spring Boot
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY src src

RUN mvn -B -DskipTests package

RUN find target -maxdepth 1 -type f \
    -name "*.jar" ! -name "*.original" \
    -exec cp {} /app.jar \; -quit

# Etapa 2: ejecutar con Java 21
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system vetcare \
    && useradd --system --gid vetcare vetcare \
    && mkdir -p /app/uploads \
    && chown -R vetcare:vetcare /app

COPY --from=build --chown=vetcare:vetcare /app.jar /app/app.jar

USER vetcare

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
