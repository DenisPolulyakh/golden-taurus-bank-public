# ===== Stage 1: сборка React (Vite) =====
FROM node:22-alpine AS build
WORKDIR /app

# Кэшируем npm-слой отдельно от исходников
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ===== Stage 2: Caddy раздаёт статику, проксирует /api и держит TLS =====
FROM caddy:2-alpine AS run

COPY Caddyfile /etc/caddy/Caddyfile

# Собранная статика
COPY --from=build /app/dist /usr/share/caddy

EXPOSE 80 443
