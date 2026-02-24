import sqlite3
from dataclasses import dataclass
from pathlib import Path
from threading import Lock


@dataclass
class FamilyGroupResult:
    group_id: str


class FamilyAccessRepository:
    def __init__(self, db_path: str):
        self._db_path = db_path
        self._lock = Lock()
        Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        self._init_schema()

    def _conn(self):
        return sqlite3.connect(self._db_path)

    def _init_schema(self):
        with self._conn() as conn:
            conn.execute(
                "CREATE TABLE IF NOT EXISTS family_access (group_id TEXT NOT NULL, nickname TEXT UNIQUE NOT NULL)"
            )
            conn.execute("CREATE INDEX IF NOT EXISTS idx_family_group_id ON family_access(group_id)")

    def create_group(self, nickname1: str, nickname2: str) -> FamilyGroupResult:
        with self._lock:
            group_id = self._next_group_id()
            self.add_member(group_id, nickname1)
            self.add_member(group_id, nickname2)
            return FamilyGroupResult(group_id=group_id)

    def add_member(self, group_id: str, nickname: str) -> None:
        with self._conn() as conn:
            conn.execute(
                "INSERT OR REPLACE INTO family_access (group_id, nickname) VALUES (?, ?)",
                (group_id, nickname),
            )

    def remove_member(self, nickname: str) -> bool:
        with self._conn() as conn:
            cur = conn.execute("DELETE FROM family_access WHERE nickname = ?", (nickname,))
            return cur.rowcount > 0

    def delete_group(self, group_id: str) -> int:
        with self._conn() as conn:
            cur = conn.execute("DELETE FROM family_access WHERE group_id = ?", (group_id,))
            return cur.rowcount

    def group_by_nickname(self, nickname: str) -> str | None:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT group_id FROM family_access WHERE nickname = ? LIMIT 1", (nickname,)
            ).fetchone()
            return row[0] if row else None

    def same_group(self, nickname1: str, nickname2: str) -> bool:
        g1 = self.group_by_nickname(nickname1)
        g2 = self.group_by_nickname(nickname2)
        return g1 is not None and g1 == g2

    def _next_group_id(self) -> str:
        with self._conn() as conn:
            rows = conn.execute("SELECT group_id FROM family_access WHERE group_id LIKE 'f%'").fetchall()
        max_id = 0
        for (group_id,) in rows:
            if len(group_id) < 2:
                continue
            try:
                max_id = max(max_id, int(group_id[1:]))
            except ValueError:
                pass
        return f"f{max_id + 1}"
