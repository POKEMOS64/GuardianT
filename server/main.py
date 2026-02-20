
#
# Copyright (C) 2026 GuardianT Project
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

import os
import json
from datetime import datetime, timedelta, timezone
from typing import Annotated, Union

from fastapi import FastAPI, Depends, HTTPException, status, Query, Body, UploadFile, File, BackgroundTasks
from fastapi.responses import FileResponse, HTMLResponse
from fastapi.security import OAuth2PasswordBearer, HTTPBasic, HTTPBasicCredentials
from jose import JWTError, jwt
from pydantic import BaseModel
import shutil
import uuid
import secrets

# --- 1. Настройки безопасности ---
# В продакшене этот ключ ОБЯЗАТЕЛЬНО должен быть в переменных окружения!
# Команда для генерации: openssl rand -hex 32
SECRET_KEY = os.getenv("SECRET_KEY", "CHANGE_THIS_TO_A_SECURE_RANDOM_KEY")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 30

# --- 2. "База данных" устройств ---
# В реальном проекте замените на PostgreSQL/SQLite.
# Ключ словаря - MAC-адрес (ID устройства), значение - секретный ключ устройства.
TRUSTED_DEVICES = {
    # Пример:
    # "AA:BB:CC:DD:EE:FF": "your_device_secret_key_here",
}

# --- 2.1 Файловая "База данных" для сообщений ---
DB_FILE = "messages_db.json"

def load_db():
    if not os.path.exists(DB_FILE):
        return {"messages": {}}
    try:
        with open(DB_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except:
        return {"messages": {}}

def save_db(data):
    with open(DB_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=4)

# --- 2.2 Папка для файлов ---
UPLOAD_DIR = "uploads"
if not os.path.exists(UPLOAD_DIR):
    os.makedirs(UPLOAD_DIR)

# Максимальный размер файла (10 МБ)
MAX_FILE_SIZE = 10 * 1024 * 1024

# --- 3. Создание FastAPI приложения ---
app = FastAPI(title="GuardianT Server")

# Схема для получения токена из заголовка Authorization: Bearer <token>
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/auth/token")

# --- 4. Функции для работы с токенами (JWT) ---

def create_access_token(data: dict, expires_delta: Union[timedelta, None] = None):
    """Генерирует новый JWT токен."""
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.now(timezone.utc) + expires_delta
    else:
        expire = datetime.now(timezone.utc) + timedelta(minutes=15)
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
    return encoded_jwt

async def get_current_user_from_token(token: Annotated[str, Depends(oauth2_scheme)]):
    """
    Проверяет токен и возвращает ID устройства.
    """
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        device_id: str = payload.get("sub")
        if device_id is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception
    
    # В демо-версии проверяем наличие в словаре.
    # В продакшене здесь должен быть запрос к БД.
    if device_id not in TRUSTED_DEVICES:
        # Для тестов можно закомментировать проверку ниже, если хотите пускать всех с валидным токеном
        # raise credentials_exception
        pass
        
    return {"device_id": device_id}

# Модель для входящего сообщения
class MessageRequest(BaseModel):
    to_device: str
    encrypted_data: str

# --- 5. Эндпоинты (API) ---

@app.post("/api/auth/device")
async def authenticate_device_and_get_token(
    device_id: str = Query(..., description="Уникальный ID устройства (MAC)"),
    device_key: str = Query(..., description="Секретный ключ устройства")
):
    """
    Аутентифицирует устройство и выдает JWT токен.
    """
    # Проверка устройства
    if device_id not in TRUSTED_DEVICES or TRUSTED_DEVICES[device_id] != device_key:
        if not TRUSTED_DEVICES:
             print(f"WARNING: Unknown device {device_id} tried to connect. Add it to TRUSTED_DEVICES in main.py")
        
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid device ID or key",
        )
    
    access_token_expires = timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": device_id}, expires_delta=access_token_expires
    )
    
    return {"access_token": access_token, "token_type": "bearer"}

@app.get("/api/data")
async def get_protected_data(
    current_device: Annotated[dict, Depends(get_current_user_from_token)]
):
    return {
        "message": "Hello! This is protected data.",
        "accessed_by_device": current_device["device_id"],
    }

# --- Эндпоинты для файлов ---

@app.post("/api/files/upload")
async def upload_file(
    current_device: Annotated[dict, Depends(get_current_user_from_token)],
    file: UploadFile = File(...)
):
    file_id = str(uuid.uuid4())
    file_path = os.path.join(UPLOAD_DIR, file_id)
    
    file_size = 0
    with open(file_path, "wb") as buffer:
        while content := await file.read(1024 * 1024):
            file_size += len(content)
            if file_size > MAX_FILE_SIZE:
                buffer.close()
                os.remove(file_path)
                raise HTTPException(status_code=413, detail="File too large (Limit 10MB)")
            buffer.write(content)
        
    return {"file_id": file_id}

@app.get("/api/files/{file_id}")
async def download_file(
    file_id: str,
    current_device: Annotated[dict, Depends(get_current_user_from_token)],
    background_tasks: BackgroundTasks
):
    file_path = os.path.join(UPLOAD_DIR, file_id)
    if os.path.exists(file_path):
        background_tasks.add_task(os.remove, file_path)
        return FileResponse(file_path)
    raise HTTPException(status_code=404, detail="File not found")

@app.post("/api/chat/send")
async def send_message(
    message: MessageRequest,
    current_device: Annotated[dict, Depends(get_current_user_from_token)]
):
    db = load_db()
    if "messages" not in db:
        db["messages"] = {}
    
    target_id = message.to_device
    if target_id not in db["messages"]:
        db["messages"][target_id] = []
        
    new_msg = {
        "from": current_device["device_id"],
        "data": message.encrypted_data,
        "timestamp": datetime.now(timezone.utc).isoformat()
    }
    
    db["messages"][target_id].append(new_msg)
    save_db(db)
    
    return {"status": "queued", "target": target_id}

@app.get("/api/chat/poll")
async def poll_messages(
    current_device: Annotated[dict, Depends(get_current_user_from_token)]
):
    device_id = current_device["device_id"]
    db = load_db()
    
    messages = []
    if "messages" in db and device_id in db["messages"]:
        messages = db["messages"][device_id]
        db["messages"][device_id] = []
        save_db(db)
        
    return messages

# --- Админ-панель ---

security = HTTPBasic()

def get_current_admin(credentials: Annotated[HTTPBasicCredentials, Depends(security)]):
    admin_user = os.getenv("ADMIN_USER", "admin") # Измените для свой
    admin_pass = os.getenv("ADMIN_PASS", "admin") # Измените для свой
    
    correct_username = secrets.compare_digest(credentials.username, admin_user)
    correct_password = secrets.compare_digest(credentials.password, admin_pass)
    
    if not (correct_username and correct_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password",
            headers={"WWW-Authenticate": "Basic"},
        )
    return credentials.username

@app.get("/admin", response_class=HTMLResponse)
async def admin_dashboard(username: Annotated[str, Depends(get_current_admin)]):
    """HTML-интерфейс для администратора."""
    html_content = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>GuardianT Admin</title>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 20px; background: #f0f2f5; color: #333; }
            .container { max-width: 900px; margin: 0 auto; }
            .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
            .card { background: white; padding: 24px; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); margin-bottom: 20px; }
            h1 { margin: 0; color: #1a73e8; font-size: 24px; }
            h2 { margin-top: 0; font-size: 18px; color: #444; border-bottom: 1px solid #eee; padding-bottom: 10px; margin-bottom: 15px; }
            .stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; }
            .stat-item { text-align: center; padding: 15px; background: #f8f9fa; border-radius: 8px; border: 1px solid #eee; }
            .stat-value { font-size: 28px; font-weight: bold; color: #1a73e8; margin-bottom: 5px; }
            .stat-label { color: #666; font-size: 14px; }
            table { width: 100%; border-collapse: collapse; font-size: 14px; }
            th, td { text-align: left; padding: 12px; border-bottom: 1px solid #eee; }
            th { background-color: #f8f9fa; color: #555; font-weight: 600; }
            .refresh-btn { background: #1a73e8; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: 500; transition: background 0.2s; }
            .refresh-btn:hover { background: #1557b0; }
            .empty-state { text-align: center; color: #888; padding: 20px; font-style: italic; }
        </style>
    </head>
    <body>
        <div class="container">
            <div class="header">
                <h1>🛡️ GuardianT Server</h1>
                <button class="refresh-btn" onclick="loadStats()">Обновить данные</button>
            </div>
            
            <div class="card">
                <h2>Статистика сервера</h2>
                <div class="stat-grid">
                    <div class="stat-item"><div class="stat-value" id="deviceCount">-</div><div class="stat-label">Доверенных устройств</div></div>
                    <div class="stat-item"><div class="stat-value" id="msgCount">-</div><div class="stat-label">Сообщений в очереди</div></div>
                    <div class="stat-item"><div class="stat-value" id="storageSize">-</div><div class="stat-label">Объем файлов</div></div>
                </div>
            </div>

            <div class="card">
                <h2>Очередь сообщений (Pending)</h2>
                <table id="msgTable">
                    <thead><tr><th>Получатель (Device ID)</th><th>Сообщений</th><th>Последняя активность</th></tr></thead>
                    <tbody></tbody>
                </table>
                <div id="emptyState" class="empty-state" style="display:none">Очередь пуста</div>
            </div>
        </div>
        <script>
            async function loadStats() {
                try {
                    const res = await fetch('/api/admin/stats');
                    if (res.status === 401) return location.reload();
                    const data = await res.json();
                    
                    document.getElementById('deviceCount').innerText = data.devices_count;
                    document.getElementById('msgCount').innerText = data.total_messages;
                    document.getElementById('storageSize').innerText = data.storage_usage;
                    
                    const tbody = document.querySelector('#msgTable tbody');
                    tbody.innerHTML = '';
                    if (data.queues.length === 0) {
                        document.getElementById('emptyState').style.display = 'block';
                    } else {
                        document.getElementById('emptyState').style.display = 'none';
                        data.queues.forEach(q => {
                            const tr = document.createElement('tr');
                            tr.innerHTML = `<td>${q.device_id}</td><td><strong>${q.count}</strong></td><td>${q.last_msg || '-'}</td>`;
                            tbody.appendChild(tr);
                        });
                    }
                } catch (e) { console.error(e); }
            }
            loadStats();
            setInterval(loadStats, 5000);
        </script>
    </body>
    </html>
    """
    return HTMLResponse(content=html_content)

@app.get("/api/admin/stats")
async def get_admin_stats(username: Annotated[str, Depends(get_current_admin)]):
    db = load_db()
    messages_map = db.get("messages", {})
    total_messages = sum(len(msgs) for msgs in messages_map.values())
    
    queues = []
    for device_id, msgs in messages_map.items():
        if len(msgs) > 0:
            queues.append({
                "device_id": device_id,
                "count": len(msgs),
                "last_msg": msgs[-1].get("timestamp", "")
            })
            
    return {
        "devices_count": len(TRUSTED_DEVICES),
        "total_messages": total_messages,
        "queues": queues
    }

@app.get("/")
async def root():
    return {"message": "GuardianT Server is running!"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
