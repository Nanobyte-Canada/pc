#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Home Server Setup Script for Portfolio Construction App
# Run as root on a fresh Debian 13 server.
# Usage: sudo bash setup-server.sh
# ============================================================

echo "=== Portfolio Home Server Setup ==="

# --- 1. System updates ---
echo "[1/7] Updating system packages..."
apt-get update && apt-get upgrade -y

# --- 2. Install Docker ---
echo "[2/7] Installing Docker Engine..."
apt-get install -y ca-certificates curl gnupg

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable docker
systemctl start docker

echo "Docker version: $(docker --version)"

# --- 3. Install cloudflared ---
echo "[3/7] Installing cloudflared..."
curl -L --output /tmp/cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
dpkg -i /tmp/cloudflared.deb
rm /tmp/cloudflared.deb

echo "cloudflared version: $(cloudflared --version)"

# --- 4. Create deploy user ---
echo "[4/7] Creating deploy user..."
if id "deploy" &>/dev/null; then
    echo "User 'deploy' already exists, skipping."
else
    useradd -m -s /bin/bash -G docker deploy
    echo "User 'deploy' created and added to docker group."
fi

# --- 5. Create directory structure ---
echo "[5/7] Creating application directories..."
mkdir -p /opt/portfolio/{prod,uat,monitoring/prometheus,monitoring/loki,monitoring/grafana/provisioning/datasources,monitoring/grafana/provisioning/alerting,cloudflared,scripts,backups/prod,backups/uat}
chown -R deploy:deploy /opt/portfolio

# --- 6. Install and configure UFW ---
echo "[6/7] Configuring firewall (UFW)..."
apt-get install -y ufw

ufw default deny incoming
ufw default allow outgoing

# Allow SSH from local network only (adjust subnet as needed)
ufw allow from 192.168.0.0/16 to any port 22 proto tcp comment "SSH from LAN"
ufw allow from 10.0.0.0/8 to any port 22 proto tcp comment "SSH from LAN"

ufw --force enable
echo "UFW status:"
ufw status verbose

# --- 7. Enable unattended upgrades ---
echo "[7/7] Enabling unattended security upgrades..."
apt-get install -y unattended-upgrades apt-listchanges
dpkg-reconfigure -plow unattended-upgrades

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Next steps:"
echo "  1. SSH key setup:  ssh-copy-id deploy@this-server"
echo "  2. Tunnel setup:   sudo -u deploy cloudflared tunnel login"
echo "                     sudo -u deploy cloudflared tunnel create portfolio-tunnel"
echo "  3. Copy configs:   cp deploy files to /opt/portfolio/"
echo "  4. Create .env:    cp .env.example .env && edit with real values"
echo "  5. Start stacks:   cd /opt/portfolio/prod && docker compose up -d"
echo ""
