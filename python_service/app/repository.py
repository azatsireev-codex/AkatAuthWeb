import sqlite3
from pathlib import Path
from threading import Lock
from time import time


class AuthRepository:
    def __init__(self, db_path: str):
        self._db_path = db_path
        self._lock = Lock()
        Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        self._init_schema()

    def _conn(self):
        return sqlite3.connect(self._db_path)

    def _init_schema(self):
        with self._conn() as conn:
            conn.execute("CREATE TABLE IF NOT EXISTS players (nickname TEXT PRIMARY KEY, email TEXT NOT NULL, original_ip TEXT NOT NULL, last_ip TEXT)")
            conn.execute("CREATE TABLE IF NOT EXISTS pending_registrations (nickname TEXT PRIMARY KEY, email TEXT NOT NULL, ip_address TEXT NOT NULL, created_at_ms INTEGER NOT NULL)")
            conn.execute("CREATE TABLE IF NOT EXISTS ip_confirmations (nickname TEXT PRIMARY KEY, new_ip TEXT NOT NULL, original_ip TEXT NOT NULL, created_at_ms INTEGER NOT NULL)")
            conn.execute("CREATE TABLE IF NOT EXISTS family_access (group_id TEXT NOT NULL, nickname TEXT UNIQUE NOT NULL)")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_family_group_id ON family_access(group_id)")

    # --- players
    def exists_nickname(self, nickname: str) -> bool:
        with self._conn() as conn:
            row = conn.execute("SELECT 1 FROM players WHERE nickname = ?", (nickname,)).fetchone()
            return row is not None

    def find_by_email(self, email: str) -> str | None:
        with self._conn() as conn:
            row = conn.execute("SELECT nickname FROM players WHERE email = ? LIMIT 1", (email,)).fetchone()
            return row[0] if row else None

    def find_by_ip(self, ip: str) -> str | None:
        with self._conn() as conn:
            row = conn.execute("SELECT nickname FROM players WHERE original_ip = ? LIMIT 1", (ip,)).fetchone()
            return row[0] if row else None

    def find_player(self, nickname: str):
        with self._conn() as conn:
            row = conn.execute("SELECT nickname, email, original_ip, last_ip FROM players WHERE nickname = ?", (nickname,)).fetchone()
            return row

    def save_player(self, nickname: str, email: str, original_ip: str, last_ip: str):
        with self._conn() as conn:
            conn.execute(
                "INSERT OR REPLACE INTO players (nickname, email, original_ip, last_ip) VALUES (?, ?, ?, ?)",
                (nickname, email, original_ip, last_ip),
            )

    def update_original_ip(self, nickname: str, ip: str):
        with self._conn() as conn:
            conn.execute("UPDATE players SET original_ip = ?, last_ip = ? WHERE nickname = ?", (ip, ip, nickname))

    # --- pending
    def save_pending(self, nickname: str, email: str, ip_address: str):
        with self._conn() as conn:
            conn.execute(
                "INSERT OR REPLACE INTO pending_registrations (nickname, email, ip_address, created_at_ms) VALUES (?, ?, ?, ?)",
                (nickname, email, ip_address, int(time() * 1000)),
            )

    def get_pending(self, nickname: str):
        with self._conn() as conn:
            return conn.execute(
                "SELECT nickname, email, ip_address, created_at_ms FROM pending_registrations WHERE nickname = ?",
                (nickname,),
            ).fetchone()

    def delete_pending(self, nickname: str):
        with self._conn() as conn:
            conn.execute("DELETE FROM pending_registrations WHERE nickname = ?", (nickname,))

    # --- ip confirmations
    def get_confirmation(self, nickname: str):
        with self._conn() as conn:
            return conn.execute(
                "SELECT new_ip, original_ip, created_at_ms FROM ip_confirmations WHERE nickname = ?",
                (nickname,),
            ).fetchone()

    def upsert_confirmation(self, nickname: str, new_ip: str, original_ip: str):
        with self._conn() as conn:
            conn.execute(
                "INSERT OR REPLACE INTO ip_confirmations (nickname, new_ip, original_ip, created_at_ms) VALUES (?, ?, ?, ?)",
                (nickname, new_ip, original_ip, int(time() * 1000)),
            )

    def remove_confirmation(self, nickname: str):
        with self._conn() as conn:
            conn.execute("DELETE FROM ip_confirmations WHERE nickname = ?", (nickname,))

    # --- family
    def create_group(self, nickname1: str, nickname2: str) -> str:
        with self._lock:
            group_id = self._next_group_id()
            self.add_family_member(group_id, nickname1)
            self.add_family_member(group_id, nickname2)
            return group_id

    def add_family_member(self, group_id: str, nickname: str):
        with self._conn() as conn:
            conn.execute("INSERT OR REPLACE INTO family_access (group_id, nickname) VALUES (?, ?)", (group_id, nickname))

    def remove_family_member(self, nickname: str) -> bool:
        with self._conn() as conn:
            cur = conn.execute("DELETE FROM family_access WHERE nickname = ?", (nickname,))
            return cur.rowcount > 0

    def delete_family_group(self, group_id: str) -> int:
        with self._conn() as conn:
            cur = conn.execute("DELETE FROM family_access WHERE group_id = ?", (group_id,))
            return cur.rowcount

    def same_family(self, nickname1: str, nickname2: str) -> bool:
        with self._conn() as conn:
            g1 = conn.execute("SELECT group_id FROM family_access WHERE nickname = ?", (nickname1,)).fetchone()
            g2 = conn.execute("SELECT group_id FROM family_access WHERE nickname = ?", (nickname2,)).fetchone()
            return g1 is not None and g2 is not None and g1[0] == g2[0]

    def _next_group_id(self) -> str:
        with self._conn() as conn:
            rows = conn.execute("SELECT group_id FROM family_access WHERE group_id LIKE 'f%'").fetchall()
        max_id = 0
        for (group_id,) in rows:
            try:
                max_id = max(max_id, int(group_id[1:]))
            except Exception:
                pass
        return f"f{max_id + 1}"
