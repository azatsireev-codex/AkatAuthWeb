from dataclasses import dataclass
from .repository import FamilyAccessRepository


@dataclass
class FamilyAccessFacade:
    repository: FamilyAccessRepository

    def create_group(self, nickname1: str, nickname2: str) -> str:
        return self.repository.create_group(nickname1, nickname2).group_id

    def add_member(self, group_id: str, nickname: str) -> None:
        self.repository.add_member(group_id, nickname)

    def remove_member(self, nickname: str) -> bool:
        return self.repository.remove_member(nickname)

    def delete_group(self, group_id: str) -> int:
        return self.repository.delete_group(group_id)

    def same_group(self, nickname1: str, nickname2: str) -> bool:
        return self.repository.same_group(nickname1, nickname2)
