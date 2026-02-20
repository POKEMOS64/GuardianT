#!/bin/bash

# --- GuardianT Server Installer (Open Source Edition) ---

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}GuardianT Server Auto-Installer${NC}"

if [ "$EUID" -ne 0 ]; then
  echo -e "${RED}Please run as root (sudo bash easy_install.sh)${NC}"
  exit
fi

echo -e "${YELLOW}Step 1: Configuration${NC}"
read -p "Enter your domain (e.g., server.example.com): " DOMAIN_NAME
if [ -z "$DOMAIN_NAME" ]; then
    echo -e "${RED}Domain cannot be empty!${NC}"
    exit 1
fi

read -p "Enter admin email for SSL (Let's Encrypt): " ADMIN_EMAIL
if [ -z "$ADMIN_EMAIL" ]; then
    echo -e "${RED}Email cannot be empty!${NC}"
    exit 1
fi

GENERATED_KEY=$(openssl rand -hex 32)
echo "Generated SECRET_KEY: $GENERATED_KEY"

echo -e "${YELLOW}Step 2: Installing packages...${NC}"
apt update
apt install -y nginx python3-pip python3-venv certbot python3-certbot-nginx ufw

echo -e "${YELLOW}Step 3: Firewall setup...${NC}"
ufw allow 'OpenSSH'
ufw allow 'Nginx Full'
ufw --force enable

echo -e "${YELLOW}Step 4: Deploying application...${NC}"
APP_DIR="/var/www/gardiant_server"
mkdir -p $APP_DIR

# Copy files from current directory
cp main.py requirements.txt $APP_DIR/

cd $APP_DIR
python3 -m venv venv
source venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
deactivate

chown -R www-data:www-data $APP_DIR
chmod -R 775 $APP_DIR

echo -e "${YELLOW}Step 5: Creating Systemd service...${NC}"
SERVICE_FILE="/etc/systemd/system/gardiant.service"

cat > $SERVICE_FILE <<EOF
[Unit]
Description=GuardianT FastAPI Server
After=network.target

[Service]
User=www-data
Group=www-data
WorkingDirectory=$APP_DIR
Environment="PATH=$APP_DIR/venv/bin"
Environment="SECRET_KEY=$GENERATED_KEY"
ExecStart=$APP_DIR/venv/bin/uvicorn main:app --workers 1 --uds $APP_DIR/gardiant.sock --forwarded-allow-ips='*'

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl start gardiant
systemctl enable gardiant

echo -e "${YELLOW}Step 6: Nginx setup...${NC}"
NGINX_CONF="/etc/nginx/sites-available/gardiant"

cat > $NGINX_CONF <<EOF
server {
    listen 80;
    server_name $DOMAIN_NAME;
    client_max_body_size 20M;

    location / {
        include proxy_params;
        proxy_pass http://unix:$APP_DIR/gardiant.sock;
    }
}
EOF

ln -sf $NGINX_CONF /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl restart nginx

echo -e "${YELLOW}Step 7: SSL Certificate...${NC}"
certbot --nginx -d $DOMAIN_NAME --non-interactive --agree-tos -m $ADMIN_EMAIL --redirect

echo -e "${GREEN}Installation Complete!${NC}"
echo -e "Server: https://$DOMAIN_NAME"
