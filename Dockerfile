# ===== Stage 1: сборка jar (Maven + JDK 21) =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Сначала только pom.xml — чтобы слой с зависимостями кэшировался,
# пока не поменялся сам pom.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Теперь исходники и сборка (тесты пропускаем — они поднимают Testcontainers)
COPY src ./src
RUN mvn -B -DskipTests clean package

# ===== Stage 2: рантайм (только JRE + готовый jar) =====
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
