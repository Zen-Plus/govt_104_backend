# 104 CRM – Docker deployment

| Service    | Dockerfile                        | Image             | Port  | Data volume |
|------------|-----------------------------------|-------------------|-------|-------------|
| PostgreSQL | `databases/postgres/Dockerfile`   | `crm104-postgres` | 5432  | `pgdata`    |
| MongoDB    | `databases/mongo/Dockerfile`      | `crm104-mongo`    | 27017 | `mongodata` |
| Backend    | `Dockerfile`                      | `crm104-backend`  | 8080  | –           |
| Frontend   | `../104_frontend/Dockerfile`      | `crm104-frontend` | 80    | –           |

Startup order: **postgres + mongo → backend → frontend**. The browser talks only to the
frontend (nginx). nginx serves the React app and proxies `/api/*` to the backend.
The backend's Flyway migrations create the PostgreSQL schema on first start. The MongoDB init
script creates a `readWrite` app user on `crm104_logs` the first time the data volume is empty.

Prerequisites: Docker 24+ with Docker Compose v2, and both repos cloned side by side:

```bash
mkdir crm104 && cd crm104
git clone https://github.com/Zen-Plus/104_backend.git
git clone https://github.com/Zen-Plus/104_frontend.git
```

```
crm104/
├── 104_backend/    <- this repo: backend + docker-compose.yml, .env, databases/
└── 104_frontend/
```

---

## Option A – Docker Compose (one server)

```bash
cd 104_backend              # folder containing docker-compose.yml
cp .env.example .env
# Fill in .env. Generate secrets with:
#   openssl rand -hex 24      -> DB_PASSWORD, MONGO_ROOT_PASSWORD, MONGO_APP_PASSWORD
#   openssl rand -hex 32      -> JWT_SECRET
#   openssl rand -base64 32   -> CRYPTO_KEY
# Set CORS_ORIGINS to the public URL, e.g. https://crm.example.gov.in

docker compose up -d --build --wait   # build images, start, wait until all are healthy
docker compose ps                      # all 4 should show (healthy)
```

Open `http://<server>:80` (or `FRONTEND_PORT`).

Common operations:

```bash
docker compose logs -f backend          # follow logs
docker compose up -d --build backend    # redeploy one service after a code change
docker compose down                     # stop (data is kept in volumes)
docker compose down -v                  # stop AND delete all data – irreversible
```

First test run with demo users: set `SEED_USERS=true` and a `SEED_PASSWORD` in `.env`, then log in
as `admin`. **Set `SEED_USERS=false` and change those passwords before go-live.**

---

## Option B – Run each container separately (`docker run`)

Use this when you deploy the services on different hosts or orchestrators. The steps below run on
one host over a user-defined network. On separate hosts, replace the hostnames with real addresses.

Run these from the `104_backend` folder.

```bash
# 0. Secrets (or export them from your secret manager)
export DB_PASSWORD=$(openssl rand -hex 24)
export MONGO_ROOT_PASSWORD=$(openssl rand -hex 24)
export MONGO_APP_PASSWORD=$(openssl rand -hex 24)
export JWT_SECRET=$(openssl rand -hex 32)
export CRYPTO_KEY=$(openssl rand -base64 32)

# 1. Build images
docker build -t crm104-postgres ./databases/postgres
docker build -t crm104-mongo    ./databases/mongo
docker build -t crm104-backend  .
docker build -t crm104-frontend ../104_frontend

# 2. Network + volumes
docker network create crm104-net
docker volume create crm104-pgdata
docker volume create crm104-mongodata

# 3. PostgreSQL
docker run -d --name postgres --network crm104-net --restart unless-stopped \
  -e POSTGRES_USER=crm104 -e POSTGRES_PASSWORD="$DB_PASSWORD" -e POSTGRES_DB=crm104 \
  -v crm104-pgdata:/var/lib/postgresql/data \
  crm104-postgres

# 4. MongoDB
docker run -d --name mongo --network crm104-net --restart unless-stopped \
  -e MONGO_INITDB_ROOT_USERNAME=admin -e MONGO_INITDB_ROOT_PASSWORD="$MONGO_ROOT_PASSWORD" \
  -e MONGO_APP_USER=crm104 -e MONGO_APP_PASSWORD="$MONGO_APP_PASSWORD" \
  -v crm104-mongodata:/data/db \
  crm104-mongo

# Wait until both report "healthy":
docker ps --format '{{.Names}}: {{.Status}}'

# 5. Backend
docker run -d --name backend --network crm104-net --restart unless-stopped \
  -e DB_URL=jdbc:postgresql://postgres:5432/crm104 \
  -e DB_USER=crm104 -e DB_PASSWORD="$DB_PASSWORD" \
  -e MONGO_URI="mongodb://crm104:${MONGO_APP_PASSWORD}@mongo:27017/crm104_logs?authSource=crm104_logs" \
  -e JWT_SECRET="$JWT_SECRET" -e CRYPTO_KEY="$CRYPTO_KEY" \
  -e CORS_ORIGINS=http://localhost \
  -e SEED_USERS=false \
  crm104-backend

# Wait until healthy (about 30-60s):  docker logs -f backend

# 6. Frontend (BACKEND_URL = where nginx forwards /api)
docker run -d --name frontend --network crm104-net --restart unless-stopped \
  -e BACKEND_URL=http://backend:8080 \
  -p 80:80 \
  crm104-frontend
```

---

## Configuration reference

**Backend** (runtime env)

| Variable | Required | Description |
|---|---|---|
| `DB_URL` | yes | `jdbc:postgresql://<host>:5432/crm104` |
| `DB_USER`, `DB_PASSWORD` | yes | PostgreSQL credentials |
| `MONGO_URI` | yes | `mongodb://<user>:<pw>@<host>:27017/crm104_logs?authSource=crm104_logs` |
| `JWT_SECRET` | yes | at least 32 characters |
| `CRYPTO_KEY` | yes | 32 random bytes, Base64 |
| `CORS_ORIGINS` | no | public URL(s) of the frontend, comma-separated |
| `SEED_USERS`, `SEED_PASSWORD` | no | create demo users (use only for testing) |
| `JWT_EXPIRY_MINUTES`, `ESCALATION_CRON`, `JAVA_OPTS` | no | tuning |

**Frontend**

| Variable | When | Description |
|---|---|---|
| `BACKEND_URL` | runtime | target for the `/api` proxy (default `http://backend:8080`) |
| `VITE_API_BASE` | build arg | leave empty. Set it only if the browser must call the API on a different origin |

**PostgreSQL:** `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`.
**MongoDB:** `MONGO_INITDB_ROOT_USERNAME/PASSWORD`, `MONGO_APP_USER/PASSWORD`, `MONGO_APP_DB`.

Database credentials are read **only the first time** a volume is created. Changing them later in
`.env` does not change the existing users. Change them inside the database instead.

---

## Production checklist

- Put TLS (HTTPS) in front of the frontend, either on a load balancer or by adding a 443 server
  block to `nginx.conf` in the 104_frontend repo.
- Keep ports 5432, 27017 and 8080 private. Compose binds them to `127.0.0.1` only, and you can
  remove those `ports:` lines entirely if you don't need host access.
- Use letters and digits only in passwords. They are embedded in the MongoDB URI.
- `SEED_USERS=false`
- Back up the data volumes:
  ```bash
  docker compose exec -T postgres pg_dump -U crm104 crm104 > backup.sql
  docker compose exec -T mongo mongodump -u admin -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --archive > mongo.archive
  ```
