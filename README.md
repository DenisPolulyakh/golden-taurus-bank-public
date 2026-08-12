# GoldenTaurusBank

Домашняя бухгалтерия: Spring Boot 3.5 (Java 21) + React 19 (Vite) + PostgreSQL.

## Деплой на VPS в Docker

Всё приложение поднимается тремя контейнерами в одной сети:

```
браузер → :443 Caddy ──(статика React)
                      └─(/api/**)→ app:8081 (Spring Boot) → postgres:5432
```

Caddy раздаёт собранный фронтенд, проксирует `/api` на бэкенд и сам выпускает и
продлевает сертификат Let's Encrypt. Фронт и API на одном
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
   - `APP_DOMAIN` — домен, на который Caddy выпустит сертификат.
   - `FRONTEND_URL` — внешний адрес приложения, уходит в ссылки писем.
   - `COOKIE_SECURE=true` — при работе по HTTPS.

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

   Открыть в браузере `https://<домен>`. Выпуск сертификата видно в
   `docker compose logs -f web` — обычно 10–30 секунд после старта.

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
| `APP_DOMAIN`     | Домен для сертификата Let's Encrypt       | да          |
| `FRONTEND_URL`   | Внешний адрес в ссылках писем             | да          |
| `COOKIE_SECURE`  | `true` при HTTPS (по умолчанию `false`)   | нет         |
| `MAIL_USERNAME`  | Логин SMTP (если нужна почта)             | нет         |
| `MAIL_PASSWORD`  | Пароль SMTP                               | нет         |

Прочие настройки (`DB_URL`, `SERVER_ADDRESS`, уровни логов и т.д.) заданы в
`docker-compose.yml`. При необходимости ограничить CORS можно задать
`APP_CORS_ALLOWED_ORIGINS` (список через запятую) — за единым Caddy-origin это не
требуется.

### HTTPS

Сертификат Let's Encrypt Caddy получает и продлевает автоматически, certbot и cron не
нужны. Условия: домен из `APP_DOMAIN` резолвится в IP VPS **до** первого запуска, порты
80 и 443 открыты (80 нужен для ACME-проверки).

Том `caddy_data` удалять нельзя — в нём лежат сертификаты. Без него они перевыпускаются
при каждом старте, а у Let's Encrypt лимит 5 одинаковых сертификатов в неделю.

## Локальная разработка

- Бэкенд: `./mvnw spring-boot:run` (нужен запущенный Postgres — можно
  `docker compose up -d postgres`).
- Фронтенд: в каталоге `src/main/java/ru/money/goldentaurusbank/www/frontend`
  выполнить `npm install && npm run dev` (Vite проксирует `/api` на `localhost:8081`).
