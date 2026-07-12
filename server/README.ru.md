# Skerry Sync Server

[English](README.md) · **Русский**

Self-hosted, zero-knowledge E2E-синхронизация для [Skerry](../README.ru.md) (модель
Vaultwarden). Сервер хранит **только шифротекст** — обёрнутый `dataKey` и зашифрованные
записи vault — плюс метаданные синхронизации. Мастер-пароль, `masterKey` и `dataKey` никогда
не покидают устройство и серверу недоступны.

> Лицензия: **AGPL-3.0** (см. `LICENSE`). Клиенты Skerry — GPL-3.0.

## Что внутри

- **Стек**: Kotlin + Ktor (Netty), Exposed, HikariCP. Аутентификация — SRP-6a (Nimbus) + JWT.
- **Хранилище**: SQLite по умолчанию (один файл, нулевая настройка); PostgreSQL — сменой
  `SKERRY_DB_URL`.
- **Крипто на сервере отсутствует** by design: сервер не умеет расшифровывать пользовательские
  данные. При регистрации загружаются SRP-соль/верификатор и обёрнутый `dataKey`; вход — обмен
  SRP-6a, в котором сам пароль никогда не передаётся.

## Быстрый старт

### Docker (готовый образ, рекомендуется)

Мультиархитектурные образы (amd64 + arm64) публикуются на Docker Hub как
[`secherkasov/skerry-sync`](https://hub.docker.com/r/secherkasov/skerry-sync) — теги:
точная `<версия>`, `<major.minor>`, `latest`. Сервер релизится в своём собственном темпе,
независимо от версий клиентов Skerry.

```bash
docker run -d --name skerry-sync \
  -p 8080:8080 \
  -e SKERRY_JWT_SECRET="$(openssl rand -base64 48)" \
  -e SKERRY_ADMIN_TOKEN="$(openssl rand -hex 16)" \
  -v skerry-data:/data \
  secherkasov/skerry-sync:latest
```

Держите `SKERRY_JWT_SECRET` неизменным при пересоздании контейнера (сохраните его в
env-файл) — смена секрета инвалидирует все выданные токены.

### Docker Compose (сборка из исходников)

```bash
# из корня репозитория
export SKERRY_JWT_SECRET="$(openssl rand -base64 48)"
export SKERRY_ADMIN_TOKEN="$(openssl rand -hex 16)"
docker compose up -d --build
```

В обоих вариантах сервер поднимется на `http://localhost:8080`. Данные — в томе
`skerry-data` (SQLite). Переключение на PostgreSQL — раскомментируйте сервис `db` и
postgres-переменные в `docker-compose.yml`.

Контейнер работает от непривилегированного пользователя, отдаёт healthcheck `/healthz`,
а образ собирается с `-PserverOnly` — Android SDK не нужен.

### Локально (Gradle)

```bash
SKERRY_JWT_SECRET=dev-secret SKERRY_ADMIN_TOKEN=admin ./gradlew :server:run -PserverOnly
```

## Конфигурация

Всё настраивается переменными окружения (модель «один `.env`»); шаблон с комментариями — в
[`.env.example`](.env.example). У всех значений разумные дефолты для локального запуска —
в проде *обязателен* только устойчивый `SKERRY_JWT_SECRET`.

| Переменная | Дефолт | Назначение |
|---|---|---|
| `SKERRY_HOST` | `0.0.0.0` | Интерфейс. За обратным прокси ставьте `127.0.0.1`. |
| `SKERRY_PORT` | `8080` | Порт. |
| `SKERRY_DB_URL` | `jdbc:sqlite:skerry-sync.db` | JDBC URL; `jdbc:postgresql://…` переключает драйвер на PostgreSQL. |
| `SKERRY_DB_USER` / `SKERRY_DB_PASSWORD` | *(пусто)* | Учётные данные БД (PostgreSQL). |
| `SKERRY_JWT_SECRET` | `dev-insecure-change-me` | Секрет подписи JWT. **С дефолтом сервер не стартует**, если не задан `SKERRY_DEV=1`. Смена секрета инвалидирует все выданные токены. |
| `SKERRY_JWT_ISSUER` | `skerry-sync` | Клейм `iss` в JWT. |
| `SKERRY_ADMIN_TOKEN` | *(пусто)* | Токен админ-консоли (`/console`, `/admin/*`). Пустой ⇒ админ-эндпоинты с данными закрыты. |
| `SKERRY_ACCESS_TTL` | `900` (15 мин) | Время жизни access-токена, секунды. |
| `SKERRY_REFRESH_TTL` | `2592000` (30 дней) | Время жизни refresh-токена, секунды. |
| `SKERRY_PAIRING_TTL` | `300` (5 мин) | Время жизни одноразовой сессии QR-паринга. |
| `SKERRY_TOMBSTONE_DAYS` | `90` | Срок хранения tombstone-записей до физической очистки. |
| `SKERRY_CORS_HOSTS` | *(пусто)* | Разрешённые CORS-источники через запятую. Пусто — CORS выключен (нативных клиентов он не касается). |
| `SKERRY_MAX_BODY_BYTES` | `4194304` (4 MiB) | Лимит тела запроса (защита от OOM/абьюза); сверх лимита — `413`. |
| `SKERRY_DEV` | *(не задана)* | `1` разрешает дефолтный JWT-секрет — только для локальной разработки. |

## Как работает синхронизация

1. **Регистрация** — клиент выводит ключи локально (Argon2id → `masterKey` →
   `authKey`/`dataKey`) и загружает SRP-соль/верификатор плюс `dataKey`, обёрнутый мастер-ключом.
   Ничего из загруженного не достаточно для расшифровки.
2. **Вход** — SRP-6a challenge/verify; сервер узнаёт лишь, что клиент знает пароль, но не сам
   пароль. При успехе выдаются короткоживущие access + refresh JWT.
3. **Push/pull** — клиенты загружают (`PUT`) батчи зашифрованных записей; конфликты решает
   last-writer-wins (по `version` записи, затем `deviceId` как тайбрейк). Скачивание — дельты
   по монотонному курсору (`?since=`).
4. **Live-обновления** — WebSocket `/sync` пушит сигнал «есть изменения», несущий только новый
   курсор и никогда — содержимое; клиенты затем забирают дельту.
5. **Удаления** — распространяются как tombstone-записи и физически вычищаются спустя
   `SKERRY_TOMBSTONE_DAYS`.
6. **Новое устройство** — либо логинится и забирает обёрнутый `dataKey` из `/vault/keys`,
   либо использует быстрый QR-паринг (`/pairing/*`, одноразовая сессия с коротким TTL).

Все шифроблобы (`blob`, `wrappedDataKey`, `encryptedDataKey`) передаются как base64.

## API

### Health и аутентификация

| Метод | Путь | Назначение |
|---|---|---|
| `GET` | `/healthz` | Liveness (открыт; используется healthcheck'ом контейнера). |
| `POST` | `/auth/register` | Регистрация: SRP-соль/верификатор + обёрнутый dataKey → токены. |
| `POST` | `/auth/srp/challenge` → `/auth/srp/verify` | Вход по SRP-6a без передачи пароля. |
| `POST` | `/auth/refresh` | Ротация access/refresh токенов. |

### Vault и устройства (JWT)

| Метод | Путь | Назначение |
|---|---|---|
| `GET` | `/vault/keys` | Обёрнутый `dataKey` для нового устройства. |
| `GET` | `/vault/records?since={cursor}` | Дельта зашифрованных записей. |
| `PUT` | `/vault/records` | Batch upsert с LWW (version, затем deviceId). |
| `WS` | `/sync` | Push «есть изменения» (только курсор, без содержимого). |
| `GET` / `DELETE` | `/devices`, `/devices/{id}` | Список устройств и отзыв. |
| `POST` | `/pairing/start` (auth) → `/pairing/claim` | Быстрый локальный QR-паринг. |

### Teams (JWT)

E2E-шифрованный шеринг: командные записи для сервера — шифротекст, членство выдаётся через
sealed-envelope приглашения на публичные ключи участников.

| Метод | Путь | Назначение |
|---|---|---|
| `PUT` | `/account/key` | Публикация публичного ключа аккаунта. |
| `GET` | `/account/keys/{accountId}` | Публичный ключ участника (для envelope). |
| `POST` / `GET` / `DELETE` | `/teams`, `/teams/{id}` | Создать, перечислить, удалить команду. |
| `GET` / `POST` | `/teams/{id}/members` | Список участников; приглашение (sealed envelope). |
| `PUT` | `/teams/{id}/members/{accountId}/role` | Смена роли (owner/member). |
| `DELETE` | `/teams/{id}/members/{accountId}` | Удаление участника / отзыв доступа. |
| `POST` | `/teams/{id}/accept` | Принятие приглашения. |
| `GET` / `PUT` | `/teams/{id}/records` | Pull/push зашифрованных общих записей. |
| `GET` | `/teams/{id}/activity` | Лента активности команды. |

### Admin (под `SKERRY_ADMIN_TOKEN`, заголовок `X-Admin-Token`)

| Метод | Путь | Назначение |
|---|---|---|
| `GET` | `/admin/health` | Liveness (открыт). |
| `GET` | `/admin/stats` | Агрегаты: аккаунты, устройства, записи, размеры блобов. |
| `GET` | `/admin/devices` | Все устройства: платформа, курсор, последняя синхронизация. |
| `GET` | `/admin/activity` | Аудит-лог (последние 2000 событий). |
| `GET` | `/admin/accounts`, `/admin/accounts/{id}/records` | Список аккаунтов, метаданные записей аккаунта. |
| `DELETE` | `/admin/devices/{id}?accountId=` | Отзыв устройства из консоли. |
| `DELETE` | `/admin/accounts/{id}/tombstones` | Досрочная очистка tombstone-записей аккаунта. |
| `DELETE` | `/admin/accounts/{id}` | Удаление аккаунта со всеми данными. |

## Админ-консоль

Статическая страница на `http://localhost:8080/console` (требует `SKERRY_ADMIN_TOKEN`) —
единый дашборд: **Overview** (аккаунты, устройства, записи, суммарный размер шифротекста),
**Devices** (платформа, последняя синхронизация, версия курсора, статус + отзыв),
**Privacy boundary** (что сервер видит и чего не видит) и **Recent activity** (аудит-лог).
Zero-knowledge сохраняется: консоль видит только метаданные — событие, устройство, метку
платформы, курсоры и размер-агрегаты — но не содержимое записей, мастер-пароль или `dataKey`.

> Шрифты (Space Grotesk, JetBrains Mono) зашиты в сервер (`resources/admin/fonts/*.woff2`),
> иконки — инлайн-SVG. Консоль полностью работает офлайн, без обращений к внешним CDN.

> ⚠️ Метаданные содержат `accountId` (это e-mail) и удерживаются в аудит-логе (последние 2000
> событий). Для single-user self-host оператор и есть субъект данных — приемлемо. Admin-токен
> ходит в заголовке `X-Admin-Token` открытым текстом: обязательно поставьте TLS-терминатор
> (ниже), иначе токен виден в сети.

## Безопасность в проде

- Задайте устойчивый `SKERRY_JWT_SECRET` (иначе рестарт инвалидирует все токены) и непустой
  `SKERRY_ADMIN_TOKEN`.
- Бэкап = файл SQLite (`/data`) или дамп PostgreSQL; данные зашифрованы, но это ваша
  единственная точка восстановления.
- Сам сервер слушает cleartext HTTP — TLS терминируется обратным прокси (ниже). Полезная
  нагрузка и так E2E-зашифрована (zero-knowledge), SRP безопасен поверх cleartext, но
  **админ-токен и метаданные (включая `accountId` = e-mail) идут открыто** — без TLS они
  видны в сети. Для публично доступного хоста TLS обязателен.

### TLS-терминатор

Клиент указывает `https://…` — WebSocket `/sync` автоматически переключается на `wss://`
(тот же хост).

**Caddy** (автоматический Let's Encrypt, проще всего):

```caddy
sync.example.com {
    reverse_proxy localhost:8080
}
```

**nginx** (сертификат свой/Certbot; важно пробросить апгрейд WebSocket для `/sync`):

```nginx
server {
    listen 443 ssl;
    server_name sync.example.com;
    ssl_certificate     /etc/letsencrypt/live/sync.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/sync.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        # WebSocket /sync (live pull): realtime notifications break without these two headers.
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 1h; # /sync is a long-lived connection; don't cut it on timeout
    }
}
```

Привяжите сервер к loopback (`SKERRY_HOST=127.0.0.1`), чтобы 8080 не торчал в сеть в обход
прокси.

> **Self-host в локальной сети без TLS** — допустимый осознанный выбор: трафик E2E-зашифрован,
> метаданные остаются в доверенной LAN. Android-клиент разрешает cleartext
> (`network_security_config.xml`). Как только хост доступен извне — ставьте TLS.

## Тесты

```bash
./gradlew :server:test
```

Покрывают LWW-конфликты, SRP-роундтрип, JWT, роли/ACL команд и полный HTTP-флоу
(register → вход → push/pull → devices → pairing → admin).
