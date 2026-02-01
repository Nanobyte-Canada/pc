#!/bin/bash
set -e

# Portfolio Construction VPS Setup Script
# Run this on the VPS to prepare for deployments
# Usage: ./setup-vps.sh

echo "=== Portfolio Construction VPS Setup ==="
echo ""

# Check if running as appropriate user
if [ "$EUID" -eq 0 ]; then
    echo "Please run as a non-root user (but with sudo privileges)"
    echo "Example: ./setup-vps.sh"
    exit 1
fi

# Update system
echo "Updating system packages..."
sudo apt-get update
sudo apt-get upgrade -y

# Create deployment directory
echo "Creating deployment directory..."
sudo mkdir -p /opt/portfolio
sudo chown $USER:$USER /opt/portfolio

# Install Docker if not present
if ! command -v docker &> /dev/null; then
    echo "Installing Docker..."
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    sudo usermod -aG docker $USER
    rm get-docker.sh
    echo "Docker installed."
else
    echo "Docker already installed."
fi

# Install Docker Compose plugin if not present
if ! docker compose version &> /dev/null; then
    echo "Installing Docker Compose..."
    sudo apt-get install -y docker-compose-plugin
else
    echo "Docker Compose already installed."
fi

# Install nginx if not present
if ! command -v nginx &> /dev/null; then
    echo "Installing nginx..."
    sudo apt-get install -y nginx
else
    echo "Nginx already installed."
fi

# Install certbot if not present
if ! command -v certbot &> /dev/null; then
    echo "Installing certbot..."
    sudo apt-get install -y certbot python3-certbot-nginx
else
    echo "Certbot already installed."
fi

# Install curl if not present (for health checks)
if ! command -v curl &> /dev/null; then
    echo "Installing curl..."
    sudo apt-get install -y curl
fi

# Configure firewall
echo "Configuring firewall..."
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable

echo ""
echo "=== VPS Setup Complete ==="
echo ""
echo "IMPORTANT: You may need to log out and back in for Docker group changes to take effect."
echo ""
echo "Next steps:"
echo ""
echo "1. Copy the nginx configuration:"
echo "   sudo cp infra/nginx/devpc.nanobyte.ca.conf /etc/nginx/sites-available/devpc.nanobyte.ca"
echo ""
echo "2. Enable the site:"
echo "   sudo ln -s /etc/nginx/sites-available/devpc.nanobyte.ca /etc/nginx/sites-enabled/"
echo "   sudo rm -f /etc/nginx/sites-enabled/default"
echo ""
echo "3. Obtain SSL certificate:"
echo "   sudo certbot --nginx -d devpc.nanobyte.ca"
echo ""
echo "4. Test and reload nginx:"
echo "   sudo nginx -t && sudo systemctl reload nginx"
echo ""
echo "5. Configure GitHub Secrets (see plan for required secrets)"
echo ""
echo "GitHub Actions will handle deployments to /opt/portfolio"
