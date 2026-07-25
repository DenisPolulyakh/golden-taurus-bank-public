# GoldenTaurusBank

Домашняя бухгалтерия: Spring Boot 3.5 (Java 21) + React 19 (Vite) + PostgreSQL.

## Деплой на VPS в Docker

Всё приложение поднимается тремя контейнерами в одной сети:

```
браузер → :80 nginx ──(статика React)
                     └─(/api/**)→ app:8081 (Spring Boot) → postgres:5432
```

nginx раздаёт собранный фронтенд и проксирует `/api` на бэкенд. Фронт и API на одном
origin, поэтому CORS не нужен, а httpOnly refresh-cookie работает без доработок.
Образы собираются multi-stage прямо в Docker — **на VPS нужен только Docker**, ни
Node, ни Maven, ни JDK ставить не требуется.

### Что нужно на VPS

- Docker Engine + плагин `docker compose`
- Открытый наружу порт (по умолчанию 80)
- Доступ в интернет для сборки (Maven Central, npm, Docker Hub)

Установка Docker на Ubuntu/Debian:

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # перелогиниться после этого
```

### Шаги

1. **Скопировать проект на VPS**

   ```bash
   git clone <repo-url> golden-taurus-bank
   cd golden-taurus-bank
   ```

2. **Создать `.env`** из шаблона и заполнить секреты:

   ```bash
   cp .env.example .env
   nano .env
   ```

   Обязательно задать:
   - `DB_PASSWORD` — пароль БД (любой свой).
   - `JWT_SECRET` — секрет для JWT, минимум 32 символа. Сгенерировать:
     ```bash
     openssl rand -base64 48
     ```
   - `APP_HTTP_PORT` — внешний порт (по умолчанию `80`).

3. **Собрать и запустить**

   ```bash
   docker compose up -d --build
   ```

   Первая сборка занимает несколько минут (Maven + npm тянут зависимости).

4. **Проверить**

   ```bash
   docker compose ps            # все три сервиса Up / postgres healthy
   docker compose logs -f app   # дождаться миграций Liquibase и старта Spring
   ```

   Открыть в браузере `http://<IP-сервера>` (или `:<APP_HTTP_PORT>`, если не 80).

5. **Открыть порт в фаерволе/облаке** (если закрыт):

   ```bash
   sudo ufw allow 80/tcp
   ```

### Управление

```bash
docker compose logs -f app        # логи бэкенда
docker compose restart app        # перезапуск бэкенда
docker compose down               # остановить (данные БД сохраняются в volume)
docker compose up -d --build      # пересобрать и поднять после обновления кода
docker compose down -v            # ВНИМАНИЕ: удаляет и данные БД (volume)
```

### Обновление после изменений в коде

```bash
git pull
docker compose up -d --build
```

### Переменные окружения (`.env`)

| Переменная       | Назначение                                | Обязательна |
|------------------|-------------------------------------------|-------------|
| `DB_PASSWORD`    | Пароль пользователя БД `taurus`           | да          |
| `JWT_SECRET`     | Секрет подписи JWT (≥32 симв.)            | да          |
| `APP_HTTP_PORT`  | Внешний порт nginx (по умолчанию 80)      | нет         |
| `MAIL_USERNAME`  | Логин SMTP (если нужна почта)             | нет         |
| `MAIL_PASSWORD`  | Пароль SMTP                               | нет         |

Прочие настройки (`DB_URL`, `SERVER_ADDRESS`, уровни логов и т.д.) заданы в
`docker-compose.yml`. При необходимости ограничить CORS можно задать
`APP_CORS_ALLOWED_ORIGINS` (список через запятую) — за единым nginx-origin это не
требуется.

### Переход на домен + HTTPS (на будущее)

1. Направить A-запись домена на IP VPS.
2. Добавить в `nginx.conf` `server`-блок на 443 и получить сертификат Let's Encrypt
   (certbot).
3. Включить `secure=true` у refresh-cookie в `AuthService` (сейчас `false` — корректно
   для HTTP по IP, но для HTTPS cookie должна быть secure).

## Локальная разработка

- Бэкенд: `./mvnw spring-boot:run` (нужен запущенный Postgres — можно
  `docker compose up -d postgres`).
- Фронтенд: в каталоге `src/main/java/ru/money/goldentaurusbank/www/frontend`
  выполнить `npm install && npm run dev` (Vite проксирует `/api` на `localhost:8081`).
