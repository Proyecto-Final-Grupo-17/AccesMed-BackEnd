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

El VPS es **Ubuntu 24.04** (kernel 6.8) y aloja, además de AccesMed, proyectos ajenos al
proyecto (`/root/system-softworks/`, contenedores detenidos) — no tocarlos.

```
/root/
├── docker/
│   ├── accesmed/
│   │   ├── staging/
│   │   │   ├── back/
│   │   │   │   ├── docker-compose.yml   ← lo pisa el workflow (no editar a mano)
│   │   │   │   └── .env                 ← secretos del entorno (nunca se versiona)
│   │   │   ├── front/
│   │   │   │   ├── docker-compose.yml
│   │   │   │   └── .env
│   │   │   └── postgres-data/           ← datos de la Postgres (lo monta el compose del back)
│   │   └── prod/                        ← misma estructura (hoy sin contenedores levantados)
│   ├── chatwoot/
│   │   ├── docker-compose.yaml          ← se administra a mano, no hay workflow
│   │   ├── .env
│   │   └── referencia-oficial/          ← compose de referencia de Chatwoot
│   └── flowise/                         ← agente del chatbot (repo AccesMed-Flowise, un solo ambiente)
│       ├── docker-compose.yml           ← lo pisa el workflow de AccesMed-Flowise
│       └── .env                         ← se crea a mano (plantilla: .env.example de ese repo)
└── system-softworks/                    ← otros proyectos del VPS, ajenos a AccesMed
```

- El compose del back levanta **`db`** (Postgres 16) y **`back`**. La base solo está en la
  red interna del compose: no publica puertos ni es alcanzable desde el front.
- Back y front del mismo entorno comparten la red Docker `accesmed-<entorno>`.
- Plantillas de los `.env`: `docker/deploy/.env.example` en cada repo.

### Contenedores

Estado actual: **solo staging está levantado**. Las carpetas de `prod/` existen con su
`.env`, pero no hay contenedores corriendo.

| Contenedor | Imagen | Puerto host | Red |
|------------|--------|-------------|-----|
| `accesmed-staging-back` | `accesmed-backend:staging` | `8081` → 8080 | `accesmed-staging` + `internal` |
| `accesmed-staging-front` | `accesmed-frontend:staging` | `3001` → 8080 | `accesmed-staging` |
| `accesmed-staging-db` | `postgres:16-alpine` | — (solo interno) | `internal` |
| `chatwoot-rails-1` | `chatwoot/chatwoot:v4.18.0-ce` | `127.0.0.1:3100` → 3000 | `chatwoot_default` |
| `chatwoot-sidekiq-1` | `chatwoot/chatwoot:v4.18.0-ce` | — | `chatwoot_default` |
| `chatwoot-postgres-1` | `pgvector/pgvector:pg16` | `127.0.0.1:5433` → 5432 | `chatwoot_default` |
| `chatwoot-redis-1` | `redis:alpine` | — | `chatwoot_default` |
| `accesmed-flowise` | `ghcr.io/proyecto-final-grupo-17/accesmed-flowise:main` | `127.0.0.1:3200` → 3000 | `accesmed-staging` |

> **Chatwoot y el back no comparten red Docker.** Chatwoot vive en `chatwoot_default` y el
> back en `accesmed-staging` + `internal`. Para que Chatwoot llamara a un webhook del back
> hay que elegir una de dos: sumar el back a `chatwoot_default` (y entonces la URL interna
> es `http://accesmed-staging-back:8080/...`, sin salir del host), o que Chatwoot lo llame
> por `http://accesmed.site/...`, que hoy es HTTP sin TLS.

## Dominios y nginx

El reverse proxy es **nginx instalado en el host** (systemd, no un contenedor), con los
sitios en `/etc/nginx/sites-enabled/`. El contenedor llamado `nginx` que aparece detenido
es de otro proyecto del VPS.

| Dominio | TLS | Ruta | Destino |
|---------|-----|------|---------|
| `accesmed.site` | **no** (solo `:80`) | `/accesmed-api/`, `/swagger-ui`, `/v3/api-docs` | `127.0.0.1:8081` (back staging) |
| `accesmed.site` | **no** | `/` | `127.0.0.1:3001` (front staging) |
| `www.accesmed.site` | no | todo | 301 → `accesmed.site` |
| `chat.accesmed.site` | **sí** (cert en `/etc/ssl/chat.accesmed.site/`) | `/privacidad/` | estático en `/var/www/chat.accesmed.site` |
| `chat.accesmed.site` | sí | `/` | `127.0.0.1:3100` (Chatwoot) |
| `flowise.accesmed.site` | **sí** (Let's Encrypt, `/etc/letsencrypt/live/flowise.accesmed.site/`) | `/` | `127.0.0.1:3200` (Flowise); `:80` redirige a HTTPS |

El certificado de `chat.` está puesto a mano en `/etc/ssl/`, no lo gestiona Certbot
(`/etc/letsencrypt/live/` tiene el de `flowise.` y los de los otros proyectos del VPS).

> **La config de nginx vive solo en el servidor**, no está versionada en ningún repo. Si se
> reinstala el VPS hay que reescribirla.

## Chatwoot

Es el canal de WhatsApp y la bandeja de los operadores humanos. Se administra a mano
(no tiene workflow de deploy).

- **Versión** `v4.18.0-ce`. Una cuenta, dos usuarios.
- **Dos inboxes de WhatsApp** (`Channel::Whatsapp`), ambos con provider `whatsapp_cloud`
  (API oficial de Meta): *WhatsApp (prueba)* y *AccesMed WhatsApp*. **La conexión con
  WhatsApp ya está hecha** — es el trámite más lento de toda la integración.
- **No hay ningún Agent Bot creado** (la tabla `agent_bots` está vacía): es el paso que
  falta para que los mensajes entrantes lleguen al backend.
- Operación: `cd /root/docker/chatwoot && docker compose ps|logs -f rails`.

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
| `/accesmed-api/Agente/**` | API key del agente: header `X-Agente-Api-Key` (`ACCESMED_AGENTE_API_KEY`); la usa Flowise |
| `/accesmed-api/Chatwoot/Webhook` | Sin JWT; el back verifica la firma HMAC del webhook (`ACCESMED_CHATWOOT_WEBHOOK_SECRET`) |
| Resto de `/accesmed-api/**` | JWT (`Authorization: Bearer ...`) |

El usuario de la documentación no es un usuario del sistema: no existe en la base ni sirve
contra la API. Si falta `ACCESMED_DOCS_PASSWORD`, el back no arranca.

## Variables del canal chatbot (contenedor del back)

El back habla con Chatwoot y Flowise y expone endpoints a Flowise. Variables nuevas, en el `.env` del
servidor (plantilla `docker/deploy/.env.example`) y mapeadas en `docker/deploy/docker-compose.yml`:

| Variable | Obligatoria | Default | Para qué |
|---|---|---|---|
| `ACCESMED_AGENTE_API_KEY` | Sí | — | Clave que Flowise manda en `X-Agente-Api-Key` |
| `ACCESMED_CHATWOOT_API_TOKEN` | Sí | — | Token de la API de Chatwoot para responder mensajes |
| `ACCESMED_FLOWISE_CHATFLOW_ID` | Sí | — | Id del chatflow del agente |
| `ACCESMED_CHATWOOT_BASE_URL` | No | `https://chat.accesmed.site` | URL de Chatwoot |
| `ACCESMED_CHATWOOT_ACCOUNT_ID` | No | `1` | Cuenta de Chatwoot |
| `ACCESMED_CHATWOOT_WEBHOOK_SECRET` | No | vacío | Secret del Agent Bot; vacío = no se verifica la firma |
| `ACCESMED_FLOWISE_BASE_URL` | No | `http://accesmed-flowise:3000` | URL de Flowise (red `accesmed-staging`) |
| `ACCESMED_FLOWISE_API_KEY` | No | vacío | API key del chatflow, si la exige |

Las tres obligatorias **no tienen default: si faltan en el `.env` del servidor, el back no arranca**.
Cargarlas antes de desplegar una imagen con este cambio.

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

- **TLS en `accesmed.site`**: hoy el front y la API se sirven por HTTP plano; el único
  certificado del proyecto es el de `chat.accesmed.site`. Hasta que esté, cualquier webhook
  entrante al back viajaría sin cifrar (motivo extra para resolver la llamada de Chatwoot
  al back por red Docker interna).
- **Levantar `prod`**: las carpetas y los `.env` existen, pero solo corre staging.
- **Agent Bot de Chatwoot**: falta crearlo y apuntarlo al webhook del back (ver
  `Docs/Features/Chatbot.md`).
- **Versionar la config de nginx**: hoy solo existe en el servidor.
