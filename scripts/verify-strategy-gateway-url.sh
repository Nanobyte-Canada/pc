#!/usr/bin/env bash
# Fails if any compose file omits BROKER_GATEWAY_URL for the strategy service.
set -euo pipefail

check() {
  local file="$1" expected="$2"
  local actual
  actual=$(python3 -c "
import yaml,sys
d = yaml.safe_load(open('$file'))
env = d['services']['portfolio-strategy'].get('environment') or {}
print(env.get('BROKER_GATEWAY_URL', ''))
")
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL $file: BROKER_GATEWAY_URL='$actual' (expected '$expected')" >&2
    return 1
  fi
  echo "OK   $file: $actual"
}

check deploy/prod/docker-compose.yml "http://prod-portfolio-broker-gateway:8084"
check deploy/uat/docker-compose.yml  "http://uat-portfolio-broker-gateway:8084"
check docker-compose.yml             "http://portfolio-broker-gateway:8084"
echo "All compose files wire BROKER_GATEWAY_URL for the strategy service."
