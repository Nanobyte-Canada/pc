#!/bin/bash
# One-time VPS setup script for Nginx reverse proxy and Let's Encrypt SSL
#
# Usage:
#   1. Copy to VPS: scp scripts/setup-nginx-ssl.sh user@vps:/tmp/
#   2. SSH to VPS: ssh user@vps
#   3. Run: sudo bash /tmp/setup-nginx-ssl.sh your-email@example.com
#
# Prerequisites:
#   - Domain (devpc.nanobyte.ca) must point to VPS IP
#   - Docker containers must be running on ports 3000 and 8080

set -e

DOMAIN="devpc.nanobyte.ca"
EMAIL="${1:-}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Nginx + SSL Setup for ${DOMAIN}${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}ERROR: Please run as root (sudo)${NC}"
    exit 1
fi

# Check email argument
if [ -z "$EMAIL" ]; then
    echo -e "${RED}ERROR: Email required for Let's Encrypt${NC}"
    echo "Usage: sudo bash $0 your-email@example.com"
    exit 1
fi

# Step 1: Install packages
echo -e "${YELLOW}Step 1: Installing nginx and certbot...${NC}"
apt-get update
apt-get install -y nginx certbot python3-certbot-nginx curl

# Step 2: Configure firewall
echo ""
echo -e "${YELLOW}Step 2: Configuring firewall...${NC}"
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
echo "Firewall configured"

# Step 3: Create initial nginx config (HTTP only for certbot verification)
echo ""
echo -e "${YELLOW}Step 3: Creating initial nginx configuration...${NC}"
cat > /etc/nginx/sites-available/${DOMAIN} << 'NGINX_CONF'
server {
    listen 80;
    listen [::]:80;
    server_name devpc.nanobyte.ca;

    # Let's Encrypt verification
    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    # Proxy to frontend
    location / {
        proxy_pass http://127.0.0.1:3000/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Proxy to backend API
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /health {
        proxy_pass http://127.0.0.1:8080/health;
    }
}
NGINX_CONF

# Step 4: Enable the site
echo ""
echo -e "${YELLOW}Step 4: Enabling nginx site...${NC}"
ln -sf /etc/nginx/sites-available/${DOMAIN} /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
echo "Nginx configured and reloaded"

# Step 5: Obtain SSL certificate
echo ""
echo -e "${YELLOW}Step 5: Obtaining SSL certificate from Let's Encrypt...${NC}"
certbot --nginx -d ${DOMAIN} --non-interactive --agree-tos --email ${EMAIL} --redirect

# Step 6: Update nginx config with full settings
echo ""
echo -e "${YELLOW}Step 6: Updating nginx with full SSL configuration...${NC}"
cat > /etc/nginx/sites-available/${DOMAIN} << 'NGINX_SSL_CONF'
server {
    listen 80;
    listen [::]:80;
    server_name devpc.nanobyte.ca;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name devpc.nanobyte.ca;

    # SSL Configuration (managed by Certbot)
    ssl_certificate /etc/letsencrypt/live/devpc.nanobyte.ca/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/devpc.nanobyte.ca/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_types text/plain text/css text/xml text/javascript application/x-javascript application/xml application/javascript application/json;

    client_max_body_size 10M;

    # API proxy to backend
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    location /health {
        proxy_pass http://127.0.0.1:8080/health;
        proxy_set_header Host $host;
    }

    location /actuator/ {
        proxy_pass http://127.0.0.1:8080/actuator/;
        proxy_set_header Host $host;
    }

    # Frontend proxy (SPA)
    location / {
        proxy_pass http://127.0.0.1:3000/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
NGINX_SSL_CONF

# Step 7: Test and reload nginx
echo ""
echo -e "${YELLOW}Step 7: Testing and reloading nginx...${NC}"
nginx -t && systemctl reload nginx
echo "Nginx reloaded with SSL configuration"

# Step 8: Verify auto-renewal
echo ""
echo -e "${YELLOW}Step 8: Verifying certificate auto-renewal...${NC}"
certbot renew --dry-run
systemctl status certbot.timer --no-pager || true

# Step 9: Quick verification
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Setup Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "Testing endpoints..."
echo ""

if curl -sf https://${DOMAIN}/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Backend health check: OK${NC}"
else
    echo -e "${RED}✗ Backend health check: FAILED${NC}"
fi

if curl -sf https://${DOMAIN} > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Frontend: OK${NC}"
else
    echo -e "${RED}✗ Frontend: FAILED (may need a moment to load)${NC}"
fi

echo ""
echo "URLs:"
echo "  Frontend:   https://${DOMAIN}"
echo "  Health:     https://${DOMAIN}/health"
echo "  API:        https://${DOMAIN}/api/v1/version"
echo ""
echo "Certificate will auto-renew via certbot timer."
