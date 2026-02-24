# Python Auth Service (extension layer)

Этот сервис выносит бизнес-логику из Velocity-плагина в Python и подключается к плагину через HTTP (Adapter/Facade pattern).

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
- `PY_AUTH_API_KEY` — bearer токен для доступа плагина

## Endpoint'ы

- `GET /health`
- `POST /family/create`
- `POST /family/add`
- `POST /family/remove`
- `POST /family/delete`
- `POST /family/check`

Все `/family/*` endpoint'ы требуют `Authorization: Bearer <PY_AUTH_API_KEY>`.

## Связка с плагином

В `config.yml` плагина:

```yaml
pythonServiceEnabled: true
pythonServiceBaseUrl: "http://127.0.0.1:9000"
pythonServiceApiKey: "change-me-python-service-key"
```

При `pythonServiceEnabled=true` команды `webauthfamily` и проверка family-доступа в регистрации идут через Python service.
