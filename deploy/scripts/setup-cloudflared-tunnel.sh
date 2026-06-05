#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Cloudflare Tunnel Setup Script
# Sets up cloudflared as a systemd service with SSH support.
#
# Usage: sudo bash setup-cloudflared-tunnel.sh
#
# Prerequisites:
#   - cloudflared binary installed (setup-server.sh does this)
#   - SSH access to the server
#   - A Cloudflare account with the nanobyte.ca zone
#
# This script will pause and ask you to open a URL in your
# LOCAL browser for Cloudflare authentication.
# ============================================================

DOMAIN="nanobyte.ca"
TUNNEL_NAME="portfolio-tunnel"
CONFIG_DIR="/etc/cloudflared"

echo "=== Cloudflare Tunnel Setup ==="
echo ""

# --- 1. Verify dependencies are installed ---
if ! command -v jq &>/dev/null; then
    echo "Installing jq..."
    apt-get install -y jq
fi

if ! command -v cloudflared &>/dev/null; then
    echo "cloudflared not found. Installing..."
    curl -L --output /tmp/cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
    dpkg -i /tmp/cloudflared.deb
    rm /tmp/cloudflared.deb
fi
echo "cloudflared version: $(cloudflared --version)"

# --- 2. Authenticate with Cloudflare ---
echo ""
echo "============================================================"
echo "  MANUAL STEP: Cloudflare authentication"
echo "============================================================"
echo ""
echo "  The next command will print a URL."
echo "  Copy that URL and open it in a browser on YOUR LOCAL machine."
echo "  Select the '${DOMAIN}' zone and authorize."
echo "  Once authorized, this script will continue automatically."
echo ""
read -rp "Press Enter to start authentication..."

cloudflared tunnel login

# Verify cert was created
CERT_PATH="${HOME}/.cloudflared/cert.pem"
if [ ! -f "${CERT_PATH}" ]; then
    echo "ERROR: Certificate not found at ${CERT_PATH}"
    echo "Authentication may have failed. Try again."
    exit 1
fi
echo "Authentication successful. Certificate saved to ${CERT_PATH}"

# --- 3. Create the tunnel ---
echo ""
echo "[3/8] Creating tunnel '${TUNNEL_NAME}'..."

# Check if tunnel already exists
EXISTING_TUNNEL=$(cloudflared tunnel list --output json 2>/dev/null | jq -r ".[] | select(.name==\"${TUNNEL_NAME}\") | .id" || true)

if [ -n "${EXISTING_TUNNEL}" ]; then
    echo "Tunnel '${TUNNEL_NAME}' already exists with ID: ${EXISTING_TUNNEL}"
    TUNNEL_ID="${EXISTING_TUNNEL}"
else
    cloudflared tunnel create "${TUNNEL_NAME}"
    TUNNEL_ID=$(cloudflared tunnel list --output json | jq -r ".[] | select(.name==\"${TUNNEL_NAME}\") | .id")
fi

if [ -z "${TUNNEL_ID}" ]; then
    echo "ERROR: Failed to get tunnel ID"
    exit 1
fi
echo "Tunnel ID: ${TUNNEL_ID}"

# --- 4. Set up config directory ---
echo ""
echo "[4/8] Setting up config directory..."
mkdir -p "${CONFIG_DIR}"

# Copy credentials file
CREDS_SRC="${HOME}/.cloudflared/${TUNNEL_ID}.json"
CREDS_DST="${CONFIG_DIR}/${TUNNEL_ID}.json"

if [ -f "${CREDS_SRC}" ]; then
    cp "${CREDS_SRC}" "${CREDS_DST}"
    echo "Credentials copied to ${CREDS_DST}"
elif [ -f "${CREDS_DST}" ]; then
    echo "Credentials already exist at ${CREDS_DST}"
else
    echo "ERROR: Credentials file not found at ${CREDS_SRC} or ${CREDS_DST}"
    exit 1
fi

# --- 5. Write config file ---
echo ""
echo "[5/8] Writing tunnel config..."

cat > "${CONFIG_DIR}/config.yml" << EOF
tunnel: ${TUNNEL_ID}
credentials-file: ${CONFIG_DIR}/${TUNNEL_ID}.json

ingress:
  # Production — frontend Nginx (reverse proxies to backend services)
  - hostname: portfolio.${DOMAIN}
    service: http://localhost:10000

  # UAT — frontend Nginx (reverse proxies to backend services)
  - hostname: uatportfolio.${DOMAIN}
    service: http://localhost:20000

  # Uptime Kuma status page
  - hostname: status.${DOMAIN}
    service: http://localhost:13001

  # Grafana dashboards (protect with Cloudflare Access)
  - hostname: grafana.${DOMAIN}
    service: http://localhost:13000

  # Vault secrets manager (protect with Cloudflare Access)
  - hostname: vault.${DOMAIN}
    service: http://localhost:18200

  # SSH access for CI/CD deployments
  - hostname: ssh.${DOMAIN}
    service: ssh://localhost:22

  # Catch-all
  - service: http_status:404
EOF

echo "Config written to ${CONFIG_DIR}/config.yml"

# --- 6. Add DNS routes ---
echo ""
echo "[6/8] Adding DNS routes..."

HOSTNAMES=(
    "portfolio.${DOMAIN}"
    "uatportfolio.${DOMAIN}"
    "status.${DOMAIN}"
    "grafana.${DOMAIN}"
    "vault.${DOMAIN}"
    "ssh.${DOMAIN}"
)

for hostname in "${HOSTNAMES[@]}"; do
    echo "  Routing ${hostname}..."
    cloudflared tunnel route dns "${TUNNEL_NAME}" "${hostname}" 2>&1 || echo "  (may already exist — check Cloudflare dashboard if this failed)"
done

# --- 7. Validate config ---
echo ""
echo "[7/8] Validating tunnel config..."
cloudflared tunnel --config "${CONFIG_DIR}/config.yml" ingress validate

# --- 8. Install as systemd service ---
echo ""
echo "[8/8] Installing as systemd service..."

# Stop existing service if running
systemctl stop cloudflared 2>/dev/null || true
systemctl disable cloudflared 2>/dev/null || true

# Remove any existing service file (cloudflared service install won't overwrite)
rm -f /etc/systemd/system/cloudflared.service
systemctl daemon-reload

cloudflared service install
systemctl enable cloudflared
systemctl start cloudflared

echo ""
echo "Verifying service status..."
sleep 3
systemctl status cloudflared --no-pager

echo ""
echo "=== Tunnel Setup Complete ==="
echo ""
echo "Tunnel ID:  ${TUNNEL_ID}"
echo "Config:     ${CONFIG_DIR}/config.yml"
echo "Credentials: ${CONFIG_DIR}/${TUNNEL_ID}.json"
echo ""
echo "Hostnames routed:"
for hostname in "${HOSTNAMES[@]}"; do
    echo "  - ${hostname}"
done
echo ""
echo "Next steps:"
echo "  1. Verify your sites load (portfolio.${DOMAIN}, etc.)"
echo "  2. Set up SSH key for the deploy user:"
echo "     mkdir -p /home/deploy/.ssh"
echo "     echo '<public-key>' > /home/deploy/.ssh/authorized_keys"
echo "     chmod 700 /home/deploy/.ssh"
echo "     chmod 600 /home/deploy/.ssh/authorized_keys"
echo "     chown -R deploy:deploy /home/deploy/.ssh"
echo "  3. Test SSH from your local machine:"
echo "     ssh -o ProxyCommand=\"cloudflared access ssh --hostname ssh.${DOMAIN}\" deploy@ssh.${DOMAIN}"
echo ""
