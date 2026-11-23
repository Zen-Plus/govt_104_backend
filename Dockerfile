# 104 CRM backend - Spring Boot 3 / Java 17.
# Build:  docker build -t crm104-backend ./104_backend
#
# Required runtime env:
#   DB_URL, DB_USER, DB_PASSWORD     PostgreSQL (jdbc:postgresql://<host>:5432/crm104)
#   MONGO_URI                        mongodb://<user>:<pw>@<host>:27017/crm104_logs?authSource=crm104_logs
#   JWT_SECRET (>= 32 chars), CRYPTO_KEY (Base64 32 bytes)
# Optional: CORS_ORIGINS, SEED_USERS, SEED_PASSWORD, JWT_EXPIRY_MINUTES, ESCALATION_CRON, JAVA_OPTS

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# Download dependencies first so they are cached until pom.xml changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- runtime stage (JRE only, non-root) ----
FROM eclipse-temurin:17-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 1001 crm
WORKDIR /app
COPY --from=build /src/target/crm104-backend-1.0.0.jar app.jar
USER crm

ENV TZ=Asia/Kolkata \
    SERVER_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fs http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
