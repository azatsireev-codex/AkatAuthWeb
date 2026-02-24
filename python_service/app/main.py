from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

from .config import settings
from .repository import FamilyAccessRepository
from .service import FamilyAccessFacade


app = FastAPI(title="Akat Auth Python Service")
facade = FamilyAccessFacade(FamilyAccessRepository(settings.db_path))


def check_auth(authorization: str | None):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Unauthorized")
    token = authorization[7:]
    if token != settings.api_key:
        raise HTTPException(status_code=403, detail="Forbidden")


class CreateFamilyRequest(BaseModel):
    nickname1: str
    nickname2: str


class AddFamilyRequest(BaseModel):
    groupId: str
    nickname: str


class RemoveFamilyRequest(BaseModel):
    nickname: str


class DeleteFamilyRequest(BaseModel):
    groupId: str


class CheckFamilyRequest(BaseModel):
    nickname1: str
    nickname2: str


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/family/create")
def family_create(payload: CreateFamilyRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    group_id = facade.create_group(payload.nickname1, payload.nickname2)
    return {"success": True, "data": {"groupId": group_id}}


@app.post("/family/add")
def family_add(payload: AddFamilyRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    facade.add_member(payload.groupId, payload.nickname)
    return {"success": True, "data": {"groupId": payload.groupId, "nickname": payload.nickname}}


@app.post("/family/remove")
def family_remove(payload: RemoveFamilyRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    removed = facade.remove_member(payload.nickname)
    return {"success": True, "data": {"removed": removed}}


@app.post("/family/delete")
def family_delete(payload: DeleteFamilyRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    deleted = facade.delete_group(payload.groupId)
    return {"success": True, "data": {"deleted": deleted}}


@app.post("/family/check")
def family_check(payload: CheckFamilyRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    same_group = facade.same_group(payload.nickname1, payload.nickname2)
    return {"success": True, "data": {"sameGroup": same_group}}
