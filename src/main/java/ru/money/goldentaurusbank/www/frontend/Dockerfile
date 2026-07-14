# ===== Stage 1: сборка React (Vite) =====
FROM node:22-alpine AS build
WORKDIR /app

# Кэшируем npm-слой отдельно от исходников
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ===== Stage 2: nginx раздаёт статику и проксирует /api =====
FROM nginx:1.27-alpine AS run

# Наш конфиг вместо дефолтного
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Собранная статика
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
