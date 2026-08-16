# Frontend/edge image: builds the React app, then serves the static build and reverse-proxies
# /api/* to the backend from the same Caddy instance - see ../docker-compose.yml. Caddy also
# handles TLS (automatic Let's Encrypt for a real domain, a locally-trusted cert for "localhost").

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
