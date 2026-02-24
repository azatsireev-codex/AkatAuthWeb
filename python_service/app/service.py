from dataclasses import dataclass
import json
import re
import time
import urllib.error
import urllib.request

from .repository import AuthRepository


@dataclass
class AuthFacade:
    repository: AuthRepository
    registration_timeout_seconds: int = 300
    strict_ip_check: bool = True
    website_url: str = "http://127.0.0.1:8998"
    website_api_path: str = "/internal/players/account/approve"
    website_api_key: str = "change-me-website-api-key"
    website_timeout_seconds: int = 10
    website_new_ip_path: str = "/internal/players/verify"

    def precheck_registration(self, nickname: str, email: str, ip_address: str):
        err = self._validate(nickname, email, ip_address)
        if err:
            return {"canRegister": False, **err}
        return {"canRegister": True}

    def start_registration(self, nickname: str, email: str, ip_address: str):
        err = self._validate(nickname, email, ip_address)
        if err:
            return {"success": False, "status": 409, **err}
        self.repository.save_pending(nickname, email, ip_address)
        return {
            "success": True,
            "status": 200,
            "message": "У вас есть 5 минут чтобы зайти на сервер",
            "timeout": self.registration_timeout_seconds,
        }

    def approve_new_ip(self, nickname: str, _ip_address: str):
        conf = self.repository.get_confirmation(nickname)
        if not conf:
            return {
                "success": False,
                "status": 404,
                "error": "CONFIRMATION_EXPIRED",
                "message": "Время подтверждения запроса истекло",
            }
        new_ip = conf[0]
        self.repository.update_original_ip(nickname, new_ip)
        self.repository.remove_confirmation(nickname)
        return {"success": True, "status": 200, "message": "IP успешно подтверждён", "ipAddress": new_ip}

    def login_check(self, nickname: str, ip_address: str):
        pending = self.repository.get_pending(nickname)
        now_ms = int(time.time() * 1000)
        if pending:
            _, email, pending_ip, created_at = pending
            passed_s = int((now_ms - created_at) / 1000)
            if now_ms - created_at > self.registration_timeout_seconds * 1000:
                self.repository.delete_pending(nickname)
                return {"decision": "PENDING_EXPIRED", "timePassedSeconds": passed_s}
            if self.strict_ip_check and pending_ip != ip_address:
                return {"decision": "PENDING_IP_MISMATCH"}

            self.repository.save_player(nickname, email, pending_ip, ip_address)
            self.repository.delete_pending(nickname)
            self._notify_website_approval(nickname)
            return {"decision": "PENDING_COMPLETE_SUCCESS"}

        player = self.repository.find_player(nickname)
        if not player:
            return {"decision": "NOT_REGISTERED"}

        original_ip = player[2]
        if original_ip == ip_address:
            return {"decision": "ALLOW"}

        conf = self.repository.get_confirmation(nickname)
        if conf and conf[0] == ip_address:
            return {"decision": "NEW_IP_CONFIRMATION_REQUIRED"}

        self.repository.upsert_confirmation(nickname, ip_address, original_ip)
        self._notify_website_new_ip(nickname, ip_address)
        return {"decision": "NEW_IP_CONFIRMATION_REQUIRED"}

    # family methods
    def create_family_group(self, nickname1: str, nickname2: str) -> str:
        return self.repository.create_group(nickname1, nickname2)

    def add_family_member(self, group_id: str, nickname: str):
        self.repository.add_family_member(group_id, nickname)

    def remove_family_member(self, nickname: str) -> bool:
        return self.repository.remove_family_member(nickname)

    def delete_family_group(self, group_id: str) -> int:
        return self.repository.delete_family_group(group_id)

    def same_family(self, nickname1: str, nickname2: str) -> bool:
        return self.repository.same_family(nickname1, nickname2)

    def _validate(self, nickname: str, email: str, ip_address: str):
        if not nickname:
            return {"error": "MISSING_NICKNAME", "message": "Никнейм обязателен"}
        if len(nickname) < 3 or len(nickname) > 16:
            return {"error": "INVALID_NICKNAME_LENGTH", "message": "Никнейм должен быть от 3 до 16 символов"}
        if not re.match(r"^[a-zA-Z0-9_]+$", nickname):
            return {"error": "INVALID_NICKNAME_FORMAT", "message": "Никнейм может содержать только буквы, цифры и подчёркивания"}
        if self.repository.exists_nickname(nickname):
            return {"error": "NICKNAME_TAKEN", "message": "Этот никнейм уже занят"}

        if not email:
            return {"error": "MISSING_EMAIL", "message": "Email обязателен"}
        if not re.match(r"^[A-Za-z0-9+_.-]+@(.+)$", email):
            return {"error": "INVALID_EMAIL", "message": "Некорректный формат email"}
        conflict_email = self.repository.find_by_email(email)
        if conflict_email:
            return {"error": "EMAIL_IN_USE", "message": "Этот email уже зарегистрирован", "conflict": conflict_email}

        if not ip_address:
            return {"error": "MISSING_IP", "message": "IP адрес обязателен"}
        conflict_ip = self.repository.find_by_ip(ip_address)
        if conflict_ip and not self.repository.same_family(nickname, conflict_ip):
            return {
                "error": "IP_IN_USE",
                "message": "Этот IP-адрес уже используется другим аккаунтом",
                "conflict": conflict_ip,
            }

        return None

    def _notify_website_approval(self, nickname: str):
        self._post_to_website(
            self.website_api_path,
            {"nickname": nickname},
        )

    def _notify_website_new_ip(self, nickname: str, ip_address: str):
        self._post_to_website(
            self.website_new_ip_path,
            {"nickname": nickname, "ipAddress": ip_address},
        )

    def _post_to_website(self, path: str, payload: dict):
        url = self.website_url.rstrip("/") + path
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=data,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.website_api_key}",
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=self.website_timeout_seconds):
                return
        except urllib.error.URLError:
            return
