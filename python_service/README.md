# Python Auth Service (extension layer)

Этот сервис выносит почти всю бизнес-логику регистрации из Velocity-плагина.
Плагин остаётся тонким адаптером для Minecraft-событий и проксирования HTTP.

## Паттерны

- **Repository** — `app/repository.py` (доступ к SQLite)
- **Facade** — `app/service.py` (бизнес-правила регистрации/family)
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

- `PY_AUTH_DB_PATH` — путь к sqlite БД (по умолчанию `./python_service/data/auth.db`)
- `PY_AUTH_API_KEY` — bearer токен доступа

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
pythonServiceEnabled: true
pythonServiceBaseUrl: "http://127.0.0.1:9000"
pythonServiceApiKey: "change-me-python-service-key"
apiKey: "token-for-website-to-plugin"
```

- `apiKey` используется сайтом при обращении к плагину.
- `pythonServiceApiKey` используется плагином для вызовов Python service.
