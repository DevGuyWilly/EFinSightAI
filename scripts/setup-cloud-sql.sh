#!/usr/bin/env bash
# Provisions a Cloud SQL for PostgreSQL instance for local dev against efinsightai,
# and writes the resulting connection details into src/main/resources/application.properties.
#
# Usage: ./scripts/setup-cloud-sql.sh
#
# Prerequisites: gcloud CLI authenticated (gcloud auth list) with an active project
# that has billing enabled.

set -euo pipefail

PROJECT_ID="agentic-wk"
REGION="europe-west2"
INSTANCE_NAME="efinsight-dev"
DB_NAME="truelayer_app"
DB_USER="postgres"
TIER="db-g1-small"
PG_VERSION="POSTGRES_15"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPERTIES_FILE="$SCRIPT_DIR/../src/main/resources/application.properties"

echo "== Detecting your current public IP for the authorized-networks rule =="
MY_IP="$(curl -s https://api.ipify.org)"
if [[ -z "$MY_IP" ]]; then
  echo "Could not determine public IP. Set MY_IP manually and re-run." >&2
  exit 1
fi
echo "Public IP: $MY_IP (will authorize $MY_IP/32)"

echo "== Enabling Cloud SQL Admin API (no-op if already enabled) =="
gcloud services enable sqladmin.googleapis.com --project="$PROJECT_ID"

echo "== Checking for existing instance '$INSTANCE_NAME' =="
if gcloud sql instances describe "$INSTANCE_NAME" --project="$PROJECT_ID" >/dev/null 2>&1; then
  echo "Instance '$INSTANCE_NAME' already exists — skipping create, will just sync networks/password/db."
else
  echo "== Creating Cloud SQL instance '$INSTANCE_NAME' (this takes several minutes) =="
  gcloud sql instances create "$INSTANCE_NAME" \
    --project="$PROJECT_ID" \
    --database-version="$PG_VERSION" \
    --region="$REGION" \
    --tier="$TIER" \
    --storage-type=SSD \
    --storage-size=10GB \
    --no-storage-auto-increase \
    --backup-start-time=03:00 \
    --authorized-networks="$MY_IP/32"
fi

echo "== Ensuring authorized network includes current IP =="
gcloud sql instances patch "$INSTANCE_NAME" \
  --project="$PROJECT_ID" \
  --authorized-networks="$MY_IP/32" \
  --quiet

echo "== Creating database '$DB_NAME' (no-op if it already exists) =="
gcloud sql databases create "$DB_NAME" \
  --instance="$INSTANCE_NAME" \
  --project="$PROJECT_ID" 2>/dev/null || echo "Database '$DB_NAME' already exists."

echo "== Generating a fresh password for user '$DB_USER' =="
DB_PASSWORD="$(openssl rand -hex 16)"
gcloud sql users set-password "$DB_USER" \
  --instance="$INSTANCE_NAME" \
  --project="$PROJECT_ID" \
  --password="$DB_PASSWORD"

echo "== Fetching instance public (PRIMARY) IP =="
INSTANCE_IP="$(gcloud sql instances describe "$INSTANCE_NAME" --project="$PROJECT_ID" \
  --format='value(ipAddresses.filter("type:PRIMARY").extract("ipAddress"))' | tr -d "[]'" | tr ',' '\n' | head -n1)"
if [[ -z "$INSTANCE_IP" ]]; then
  echo "Could not determine PRIMARY IP for instance '$INSTANCE_NAME'." >&2
  exit 1
fi

echo "== Writing connection settings into $PROPERTIES_FILE =="
if [[ ! -f "$PROPERTIES_FILE" ]]; then
  echo "application.properties not found at $PROPERTIES_FILE — aborting write, create it from application.properties.example first." >&2
  exit 1
fi

python3 - "$PROPERTIES_FILE" "$INSTANCE_IP" "$DB_NAME" "$DB_USER" "$DB_PASSWORD" <<'PYEOF'
import re, sys

path, host, dbname, user, password = sys.argv[1:6]
with open(path) as f:
    content = f.read()

def set_prop(content, key, value):
    pattern = re.compile(rf"^{re.escape(key)}=.*$", re.MULTILINE)
    line = f"{key}={value}"
    if pattern.search(content):
        return pattern.sub(line, content, count=1)
    return content + f"\n{line}\n"

content = set_prop(content, "spring.datasource.url",
    f"jdbc:postgresql://{host}:5432/{dbname}?sslmode=prefer")
content = set_prop(content, "spring.datasource.username", user)
content = set_prop(content, "spring.datasource.password", password)

with open(path, "w") as f:
    f.write(content)
PYEOF

echo ""
echo "Done. Instance '$INSTANCE_NAME' is up at $INSTANCE_IP, database '$DB_NAME' created,"
echo "and spring.datasource.* in application.properties has been updated in place."
echo ""
echo "Re-run this script any time your public IP changes (e.g. new network) to refresh the authorized-networks rule."
