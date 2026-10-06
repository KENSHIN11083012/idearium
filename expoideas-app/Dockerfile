# syntax=docker/dockerfile:1
# App de Expoideas: se compila con Node y se sirve con Nginx sin privilegios.
# Nginx también reenvía /expoideas/api/ a la API, así que la app y la API
# comparten origen y no hace falta CORS.

# La misma versión de Node que .nvmrc y la integración continua: lo que se prueba es lo que se publica.
FROM node:24-slim AS build
WORKDIR /src

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# Qué versión es esta imagen: la etiqueta con la que la publica la integración
# continua (el commit). Sale en el pie de la app.
ARG APP_VERSION=local
# La ruta base está fija en nginx.conf; la API se llama por la misma ruta.
ENV VITE_BASE_PATH=/expoideas/ \
    VITE_API_URL=/expoideas/api/v1 \
    VITE_APP_VERSION=$APP_VERSION
RUN npm run build


FROM nginxinc/nginx-unprivileged:stable-alpine

ARG APP_VERSION=local
LABEL org.opencontainers.image.title="Idearium app" \
      org.opencontainers.image.revision=$APP_VERSION

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html/expoideas

EXPOSE 8080
