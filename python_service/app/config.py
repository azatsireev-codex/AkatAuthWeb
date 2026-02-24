from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    db_path: str = os.getenv("PY_AUTH_DB_PATH", "./python_service/data/auth.db")
    api_key: str = os.getenv("PY_AUTH_API_KEY", "change-me-python-service-key")


settings = Settings()
