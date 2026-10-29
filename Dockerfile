# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- runtime stage (non-root user, JRE only) ----
FROM eclipse-temurin:17-jre
RUN useradd -r -u 1001 crm
WORKDIR /app
COPY --from=build /src/target/crm104-backend-1.0.0.jar app.jar
USER crm
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
