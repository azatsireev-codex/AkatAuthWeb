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
