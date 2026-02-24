from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

from .config import settings
from .repository import AuthRepository
from .service import AuthFacade


app = FastAPI(title="Akat Auth Python Service")
facade = AuthFacade(
    repository=AuthRepository(settings.db_path),
    registration_timeout_seconds=settings.registration_timeout_seconds,
    strict_ip_check=settings.strict_ip_check,
    website_url=settings.website_url,
    website_api_path=settings.website_api_path,
    website_api_key=settings.website_api_key,
    website_timeout_seconds=settings.website_timeout_seconds,
    website_new_ip_path=settings.website_new_ip_path,
)


def check_auth(authorization: str | None):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Unauthorized")
    token = authorization[7:]
    if token != settings.api_key:
        raise HTTPException(status_code=403, detail="Forbidden")


class RegistrationRequest(BaseModel):
    nickname: str | None = None
    email: str | None = None
    ipAddress: str | None = None


class ApproveRequest(BaseModel):
    nickname: str
    ipAddress: str


class LoginCheckRequest(BaseModel):
    nickname: str
    ipAddress: str


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


@app.post("/internal/players/ip/check")
def precheck(payload: RegistrationRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    data = facade.precheck_registration(payload.nickname, payload.email, payload.ipAddress)
    return {"success": True, "data": data, "message": data.get("message", "Поля регистрации прошли валидацию")}


@app.post("/internal/players/account/verify")
def register(payload: RegistrationRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    data = facade.start_registration(payload.nickname, payload.email, payload.ipAddress)
    if not data["success"]:
        return {"success": False, "error": data.get("error"), "message": data.get("message")}
    return {"success": True, "data": {"status": "pending", "timeout": data["timeout"]}, "message": data["message"]}


@app.post("/internal/connection-requests/approve")
def approve(payload: ApproveRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    data = facade.approve_new_ip(payload.nickname, payload.ipAddress)
    if not data["success"]:
        return {"success": False, "error": data.get("error"), "message": data.get("message")}
    return {"success": True, "data": {"nickname": payload.nickname, "ipAddress": data["ipAddress"]}, "message": data["message"]}


@app.post("/minecraft/login/check")
def login_check(payload: LoginCheckRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    return {"success": True, "data": facade.login_check(payload.nickname, payload.ipAddress)}


@app.post("/family/create")
def family_create(payload: CreateFamilyRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    group_id = facade.create_family_group(payload.nickname1, payload.nickname2)
    return {"success": True, "data": {"groupId": group_id}}


@app.post("/family/add")
def family_add(payload: AddFamilyRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    facade.add_family_member(payload.groupId, payload.nickname)
    return {"success": True, "data": {"groupId": payload.groupId, "nickname": payload.nickname}}


@app.post("/family/remove")
def family_remove(payload: RemoveFamilyRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    removed = facade.remove_family_member(payload.nickname)
    return {"success": True, "data": {"removed": removed}}


@app.post("/family/delete")
def family_delete(payload: DeleteFamilyRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    deleted = facade.delete_family_group(payload.groupId)
    return {"success": True, "data": {"deleted": deleted}}


@app.post("/family/check")
def family_check(payload: CheckFamilyRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    same_group = facade.same_family(payload.nickname1, payload.nickname2)
    return {"success": True, "data": {"sameGroup": same_group}}
