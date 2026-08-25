# Frontend/edge image: builds the React app, then serves the static build and reverse-proxies
# /api/* to the backend from the same Caddy instance - see ../docker-compose.yml. Caddy also
# handles TLS (automatic Let's Encrypt for a real domain, a locally-trusted cert for "localhost").

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Defaults to the primary instance's path (see vite.config.ts) - override to build a second
# instance of this same app for a different path prefix (e.g. /energytracker2/), and set the
# matching $APP_PATH/$APP_UPSTREAM on that instance's caddy service (see frontend/Caddyfile and
# docker-compose.yml).
ARG APP_BASE_PATH=/energytracker/
ENV APP_BASE_PATH=$APP_BASE_PATH
RUN npm run build

FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
