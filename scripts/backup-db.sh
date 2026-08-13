#!/usr/bin/env bash
#
# Дамп базы taurus_db из контейнера postgres в BACKUP_DIR.
# Формат custom (-Fc) — сжатый, восстанавливается через pg_restore.
#
# Запуск вручную:   ./scripts/backup-db.sh
# Восстановление:   gunzip -c нет; для .dump:
#   docker exec -i golden_taurus_db pg_restore -U "$DB_USERNAME" -d taurus_db \
#       --clean --if-exists < /path/taurus_db_2026-08-13_03-15.dump
#
set -euo pipefail

CONTAINER="${CONTAINER:-golden_taurus_db}"
BACKUP_DIR="${BACKUP_DIR:-/home/deploy/backups/golden-taurus}"
KEEP_DAYS="${KEEP_DAYS:-14}"

mkdir -p "$BACKUP_DIR"

stamp="$(date +%Y-%m-%d_%H-%M)"
file="$BACKUP_DIR/taurus_db_$stamp.dump"

# Пользователь и имя БД берём из окружения самого контейнера,
# чтобы скрипт не зависел от .env и текущего каталога.
docker exec -i "$CONTAINER" sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-privileges' \
  > "$file.part"

mv "$file.part" "$file"

# Чистка старых дампов
find "$BACKUP_DIR" -maxdepth 1 -name 'taurus_db_*.dump' -mtime "+$KEEP_DAYS" -delete

echo "$(date -Is) OK $file ($(du -h "$file" | cut -f1))"
