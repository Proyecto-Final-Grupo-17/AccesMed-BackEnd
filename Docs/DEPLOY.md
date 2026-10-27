# Deploy — staging y prod

AccesMed (back + front) corre en un VPS Ubuntu con Docker. Cada repo tiene su workflow
`.github/workflows/deploy.yml` que construye la imagen, la publica en GHCR y la levanta
en el servidor por SSH.

## Flujo

| Rama | Entorno | Carpeta en el servidor | Back | Front |
|------|---------|------------------------|------|-------|
| `staging` | staging | `/root/docker/accesmed/staging/` | `:8081` | `:3001` |
| `main` | prod | `/root/docker/accesmed/prod/` | `:8080` | `:3000` |

Cada push a esas ramas (normalmente el merge de un PR) dispara:

1. **Test** — `./mvnw verify` (back) / `lint` + `build` (front).
2. **Build** — imagen con `docker/Dockerfile`, publicada en
   `ghcr.io/proyecto-final-grupo-17/accesmed-{backend,frontend}` con dos tags:
   `staging`/`prod` (el que usa el servidor) y `sha-<commit>` (inmutable, para rollback).
3. **Deploy** — copia `docker/deploy/docker-compose.yml` al servidor, hace `pull` + `up -d`
   y espera a que el healthcheck del contenedor quede `healthy`. Si no, imprime los logs
   y el job falla.

También se puede relanzar a mano desde la pestaña *Actions* → *Deploy* → *Run workflow*
(solo sobre `staging` o `main`).

## Estructura en el servidor

```
/root/docker/accesmed/
├── staging/
│   ├── back/
│   │   ├── docker-compose.yml   ← lo pisa el workflow (no editar a mano)
│   │   └── .env                 ← secretos del entorno (600, nunca se versiona)
│   ├── front/
│   │   ├── docker-compose.yml
│   │   └── .env
│   └── postgres-data/           ← datos de la Postgres (montado por el compose del back)
└── prod/                        ← misma estructura
```

- El compose del back levanta **`db`** (Postgres 16) y **`back`**. La base solo está en la
  red interna del compose: no publica puertos ni es alcanzable desde el front.
- Back y front del mismo entorno comparten la red Docker `accesmed-<entorno>`, que es
  donde se va a enganchar el nginx público cuando haya dominio.
- Plantillas de los `.env`: `docker/deploy/.env.example` en cada repo.

## Configuración en GitHub

**Secrets** (en los dos repos, o una sola vez a nivel organización):

| Secret | Valor |
|--------|-------|
| `SSH_HOST` | IP del servidor |
| `SSH_PORT` | Puerto SSH |
| `SSH_USER` | `root` |
| `SSH_PRIVATE_KEY` | Clave privada de deploy (`accesmed-github-actions`) |
| `SSH_KNOWN_HOSTS` | Salida de `ssh-keyscan -p <puerto> -t ed25519 <ip>` |

**Variables** (solo en el repo del front): `VITE_API_URL_STAGING` y `VITE_API_URL_PROD`,
la URL pública del back de cada entorno. Se hornean en el bundle al compilar: si cambian,
hay que volver a correr el workflow.

El registry no necesita credenciales extra: el workflow usa el `GITHUB_TOKEN` del job,
también para que el servidor haga `docker login` durante el deploy (y `logout` al final).

## Qué es público y qué pide credenciales

| Ruta | Acceso |
|------|--------|
| `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | HTTP Basic: `ACCESMED_DOCS_USERNAME` / `ACCESMED_DOCS_PASSWORD` del `.env` |
| `/actuator/health` | Público (solo `{"status":"UP"}`; lo usa el healthcheck del contenedor) |
| `/accesmed-api/Auth/{Login,Refresh,OlvideContrasena,RestablecerContrasena,ConfirmarCambioMail}` | Público |
| Resto de `/accesmed-api/**` | JWT (`Authorization: Bearer ...`) |

El usuario de la documentación no es un usuario del sistema: no existe en la base ni sirve
contra la API. Si falta `ACCESMED_DOCS_PASSWORD`, el back no arranca.

## Operación

```bash
cd /root/docker/accesmed/<entorno>/back   # o front
docker compose ps                         # estado y health
docker compose logs -f back               # logs en vivo
docker compose restart back               # reiniciar sin recrear
```

**Cambiar una variable** (por ejemplo `MAIL_PASSWORD`): editar el `.env` y
`docker compose up -d` (recrea el contenedor con el valor nuevo).

Excepciones: `POSTGRES_PASSWORD` no se cambia una vez creada la base (Postgres guarda la
de la primera inicialización), y `ACCESMED_SUPERADMIN_PASSWORD` solo se usa para crear el
SuperAdmin en el primer arranque: después su contraseña se cambia desde la app.

**Rollback**: en el `.env` poner `IMAGE_TAG=sha-<commit>` (los tags están en GHCR) y
`docker compose up -d`. El siguiente deploy por workflow no toca `IMAGE_TAG`: para volver
al flujo normal, restaurar `IMAGE_TAG=<entorno>`.

**Backup de la base**:

```bash
docker exec accesmed-<entorno>-db pg_dump -U accesmed accesmed | gzip > accesmed-$(date +%F).sql.gz
```

## Pendiente

- **Dominio + HTTPS**: hoy se accede por IP y puerto (HTTP). Cuando haya dominio, el
  nginx público del servidor termina TLS y rutea a los contenedores por la red
  `accesmed-<entorno>`; ahí se dejan de publicar los puertos y se actualizan
  `ACCESMED_CORS_ALLOWED_ORIGINS`, `ACCESMED_FRONTEND_BASE_URL` y `VITE_API_URL_*`.
