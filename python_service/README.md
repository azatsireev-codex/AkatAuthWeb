# Python Auth Service (extension layer)

Этот сервис выносит почти всю бизнес-логику регистрации из Velocity-плагина.
Плагин остаётся тонким адаптером для Minecraft-событий и проксирования HTTP.

## Паттерны

- **Repository** — `app/repository.py` (доступ к SQLite)
- **Facade** — `app/service.py` (бизнес-правила регистрации/family + website webhook integration)
- **Adapter/API** — `app/main.py` (FastAPI endpoint'ы)

## Запуск

```bash
cd python_service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 9000
```

## Переменные окружения

### Базовые
- `PY_AUTH_DB_PATH` — путь к sqlite БД (по умолчанию `./python_service/data/auth.db`)
- `PY_AUTH_API_KEY` — bearer токен доступа

### Логика регистрации (перенесена из старого Java-конфига)
- `PY_REGISTRATION_TIMEOUT_SECONDS` (default `300`)
- `PY_STRICT_IP_CHECK` (default `true`)

### Параметры интеграции с сайтом (перенесены из старого Java-конфига)
- `PY_WEBSITE_URL` (default `http://127.0.0.1:8998`)
- `PY_WEBSITE_API_PATH` (default `/internal/players/account/approve`)
- `PY_WEBSITE_API_KEY` (default `change-me-website-api-key`)
- `PY_WEBSITE_TIMEOUT_SECONDS` (default `10`)
- `PY_WEBSITE_NEW_IP_PATH` (default `/internal/players/verify`)

## Endpoint'ы

### Для сайта (через плагин-прокси)
- `POST /internal/players/ip/check`
- `POST /internal/players/account/verify`
- `POST /internal/connection-requests/approve`

### Для Minecraft-части
- `POST /minecraft/login/check`

### Family management
- `POST /family/create`
- `POST /family/add`
- `POST /family/remove`
- `POST /family/delete`
- `POST /family/check`

Все endpoint'ы (кроме `/health`) требуют `Authorization: Bearer <PY_AUTH_API_KEY>`.

## Связка с плагином

В `config.yml` плагина:

```yaml
apiPort: 8668
apiKey: "token-for-website-to-plugin"
pythonServiceBaseUrl: "http://127.0.0.1:9000"
pythonServiceApiKey: "change-me-python-service-key"
```

- `apiKey` используется сайтом при обращении к плагину.
- `pythonServiceApiKey` используется плагином для вызовов Python service.

## Проверка регистрации через `curl`

Если сайт отправляет запросы в плагин на `8668`, проверять нужно именно URL плагина.

```bash
API_KEY="730222ffe0b86a26e0a6d0a6055fc99520b20143fc01c3b69e80b4030f09df54a072892381846863b8393c95bbbbfea52a8357b80eb228d4da8f729388fe6e85"
PLUGIN_URL="http://127.0.0.1:8668"
```

### 1) Precheck (до кода из почты)

```bash
curl -i -X POST "$PLUGIN_URL/internal/players/ip/check" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "TestPlayer",
    "email": "test@example.com",
    "ipAddress": "203.0.113.10",
    "ipTimeZone": "Europe/Moscow",
    "clientTimeZone": "Europe/Moscow"
  }'
```

### 2) Старт регистрации

Поля `ipTimeZone` и `clientTimeZone` **необязательные** — если их нет или они пустые, сервис их не перезаписывает в БД.

```bash
curl -i -X POST "$PLUGIN_URL/internal/players/account/verify" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "TestPlayer",
    "email": "test@example.com",
    "ipAddress": "203.0.113.10",
    "ipTimeZone": "Europe/Moscow",
    "clientTimeZone": "Europe/Moscow"
  }'
```

### 3) Подтверждение нового IP

```bash
curl -i -X POST "$PLUGIN_URL/internal/connection-requests/approve" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "TestPlayer",
    "ipAddress": "203.0.113.10"
  }'
```

> Примечание: `8998` — это обычно порт сайта для обратных webhook-вызовов из Python сервиса,
> а не порт, куда сайт шлёт эти три registration-запроса.
