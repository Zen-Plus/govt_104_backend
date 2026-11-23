# MongoDB for the 104 CRM (audit / API / notification logs).
# Authentication is enabled: a root user is created from MONGO_INITDB_ROOT_*,
# and init/01-create-app-user.js creates a least-privilege app user for the backend.
#
# Required at runtime:
#   MONGO_INITDB_ROOT_USERNAME, MONGO_INITDB_ROOT_PASSWORD   (admin account)
#   MONGO_APP_USER, MONGO_APP_PASSWORD                       (backend account, readWrite on MONGO_APP_DB)
# Optional:  MONGO_APP_DB (default crm104_logs)
FROM mongo:7

ENV MONGO_APP_DB=crm104_logs \
    TZ=Asia/Kolkata

# Runs only when /data/db is empty (first start).
COPY init/ /docker-entrypoint-initdb.d/

# 'ping' is allowed without authentication, so no credentials are needed here.
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=10 \
  CMD mongosh --quiet --eval "db.adminCommand('ping').ok" | grep -q 1 || exit 1

EXPOSE 27017
VOLUME ["/data/db"]
