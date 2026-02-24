from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    db_path: str = os.getenv("PY_AUTH_DB_PATH", "./python_service/data/auth.db")
    api_key: str = os.getenv("PY_AUTH_API_KEY", "change-me-python-service-key")

    registration_timeout_seconds: int = int(os.getenv("PY_REGISTRATION_TIMEOUT_SECONDS", "300"))
    strict_ip_check: bool = os.getenv("PY_STRICT_IP_CHECK", "true").lower() == "true"

    website_url: str = os.getenv("PY_WEBSITE_URL", "http://127.0.0.1:8998")
    website_api_path: str = os.getenv("PY_WEBSITE_API_PATH", "/internal/players/account/approve")
    website_api_key: str = os.getenv("PY_WEBSITE_API_KEY", "change-me-website-api-key")
    website_timeout_seconds: int = int(os.getenv("PY_WEBSITE_TIMEOUT_SECONDS", "10"))
    website_new_ip_path: str = os.getenv("PY_WEBSITE_NEW_IP_PATH", "/internal/players/verify")


settings = Settings()
