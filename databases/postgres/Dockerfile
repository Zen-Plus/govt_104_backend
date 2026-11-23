# PostgreSQL for the 104 CRM (business / transactional data).
# The schema is NOT created here - the backend's Flyway migrations
# (104_backend/src/main/resources/db/migration) create and upgrade it on startup.
#
# Required at runtime:  POSTGRES_PASSWORD
# Optional at runtime:  POSTGRES_USER (default crm104), POSTGRES_DB (default crm104)
FROM postgres:16

ENV POSTGRES_DB=crm104 \
    POSTGRES_USER=crm104 \
    TZ=Asia/Kolkata \
    PGTZ=Asia/Kolkata

# Optional: drop one-time *.sql / *.sh scripts into /docker-entrypoint-initdb.d/
# (they run only when the data volume is empty). TZ above sets the server timezone at initdb.

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=10 \
  CMD pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" || exit 1

EXPOSE 5432
VOLUME ["/var/lib/postgresql/data"]
