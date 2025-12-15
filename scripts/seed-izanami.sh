#!/usr/bin/env bash
set -euo pipefail

IZANAMI_BASE_URL="${IZANAMI_BASE_URL:-http://localhost:9999}"
IZANAMI_API_PATH="${IZANAMI_API_PATH:-/api}"
IZANAMI_ADMIN_USERNAME="${IZANAMI_ADMIN_USERNAME:-${IZANAMI_ADMIN_DEFAULT_USERNAME:-RESERVED_ADMIN_USER}}"
IZANAMI_ADMIN_PASSWORD="${IZANAMI_ADMIN_PASSWORD:-${IZANAMI_ADMIN_DEFAULT_PASSWORD:-password}}"
IZANAMI_TENANT="${IZANAMI_TENANT:-starter-it}"
IZANAMI_PROJECT="${IZANAMI_PROJECT:-starter-project}"
IZANAMI_KEY_NAME="${IZANAMI_KEY_NAME:-starter-it-key}"
IZANAMI_SEED_OUTPUT="${IZANAMI_SEED_OUTPUT:-export}" # export|github-env

# Feature IDs used by IzanamiOpenFeatureIT (override if needed)
IZANAMI_PERFORMANCE_MODE_ID="${IZANAMI_PERFORMANCE_MODE_ID:-a4c0d04f-69ac-41aa-a6e4-febcee541d51}"
IZANAMI_JSON_CONTENT_ID="${IZANAMI_JSON_CONTENT_ID:-00812ba5-aebc-49e8-959a-4b96a5cebbff}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_cmd curl
require_cmd python3

API_BASE="${IZANAMI_BASE_URL%/}${IZANAMI_API_PATH}"

wait_for_izanami() {
  local health_urls=(
    "${API_BASE}/_health"
    "${IZANAMI_BASE_URL%/}/health"
  )

  echo "Waiting for Izanami to be healthy ..." >&2
  for _ in $(seq 1 60); do
    for url in "${health_urls[@]}"; do
      if curl -fsS "${url}" >/dev/null 2>&1; then
        echo "Izanami is healthy: ${url}" >&2
        return 0
      fi
    done
    sleep 2
  done
  echo "Izanami did not become healthy in time" >&2
  return 1
}

admin_login() {
  local headers_file
  headers_file="$(mktemp)"

  curl -sS -D "${headers_file}" -o /dev/null \
    -u "${IZANAMI_ADMIN_USERNAME}:${IZANAMI_ADMIN_PASSWORD}" \
    -X POST "${API_BASE}/admin/login"

  python3 - "${headers_file}" <<'PY'
import re
import sys

headers_path = sys.argv[1]
token = None

with open(headers_path, "r", encoding="utf-8", errors="ignore") as f:
    for line in f:
        if line.lower().startswith("set-cookie:"):
            match = re.search(r"\btoken=([^;\s]+)", line)
            if match:
                token = match.group(1)
                break

if not token:
    sys.stderr.write("Failed to extract admin JWT cookie from /api/admin/login response\n")
    sys.exit(1)

print(token)
PY
  rm -f "${headers_file}"
}

admin_json() {
  local method="$1"
  local path="$2"
  local json_body="${3:-}"

  local output_file
  output_file="$(mktemp)"

  local code
  if [[ -n "${json_body}" ]]; then
    code="$(curl -sS -o "${output_file}" -w "%{http_code}" \
      -X "${method}" \
      -H "Content-Type: application/json" \
      -H "Cookie: token=${ADMIN_TOKEN}" \
      -d "${json_body}" \
      "${API_BASE}${path}")"
  else
    code="$(curl -sS -o "${output_file}" -w "%{http_code}" \
      -X "${method}" \
      -H "Cookie: token=${ADMIN_TOKEN}" \
      "${API_BASE}${path}")"
  fi

  cat "${output_file}"
  rm -f "${output_file}"
  echo ""
  echo "${code}"
}

ensure_tenant() {
  echo "Ensuring tenant '${IZANAMI_TENANT}' ..." >&2
  local body code
  body="$(admin_json GET "/admin/tenants/${IZANAMI_TENANT}")"
  code="$(echo "${body}" | tail -n 1)"
  if [[ "${code}" == "200" ]]; then
    return 0
  fi

  local payload
  payload="$(python3 - "${IZANAMI_TENANT}" <<'PY'
import json
import sys

tenant = sys.argv[1]
payload = {"name": tenant, "description": "Integration tests tenant"}
print(json.dumps(payload))
PY
)"

  body="$(admin_json POST "/admin/tenants" "${payload}")"
  code="$(echo "${body}" | tail -n 1)"
  if [[ "${code}" != "201" && "${code}" != "200" ]]; then
    echo "Failed to create tenant '${IZANAMI_TENANT}' (HTTP ${code})" >&2
    echo "${body}" | sed '$d' >&2
    exit 1
  fi
}

ensure_project() {
  echo "Ensuring project '${IZANAMI_PROJECT}' in tenant '${IZANAMI_TENANT}' ..." >&2
  local body code
  body="$(admin_json GET "/admin/tenants/${IZANAMI_TENANT}/projects/${IZANAMI_PROJECT}")"
  code="$(echo "${body}" | tail -n 1)"
  if [[ "${code}" == "200" ]]; then
    return 0
  fi

  local payload
  payload="$(python3 - "${IZANAMI_PROJECT}" <<'PY'
import json
import sys

project = sys.argv[1]
payload = {"name": project, "description": "Integration tests project"}
print(json.dumps(payload))
PY
)"

  body="$(admin_json POST "/admin/tenants/${IZANAMI_TENANT}/projects" "${payload}")"
  code="$(echo "${body}" | tail -n 1)"
  if [[ "${code}" != "201" && "${code}" != "200" ]]; then
    echo "Failed to create project '${IZANAMI_PROJECT}' (HTTP ${code})" >&2
    echo "${body}" | sed '$d' >&2
    exit 1
  fi
}

wait_for_izanami

echo "Logging in as admin '${IZANAMI_ADMIN_USERNAME}' ..." >&2
ADMIN_TOKEN="$(admin_login)"

ensure_tenant
ensure_project

echo "Creating API key '${IZANAMI_KEY_NAME}' in tenant '${IZANAMI_TENANT}' ..." >&2

admin_json DELETE "/admin/tenants/${IZANAMI_TENANT}/keys/${IZANAMI_KEY_NAME}" >/dev/null 2>&1 || true

KEY_PAYLOAD="$(python3 - "${IZANAMI_KEY_NAME}" <<'PY'
import json
import sys

name = sys.argv[1]
payload = {
  "name": name,
  "description": "Integration tests key",
  "enabled": True,
  "admin": True
}
print(json.dumps(payload))
PY
)"

KEY_RESPONSE="$(admin_json POST "/admin/tenants/${IZANAMI_TENANT}/keys" "${KEY_PAYLOAD}")"
KEY_CODE="$(echo "${KEY_RESPONSE}" | tail -n 1)"
if [[ "${KEY_CODE}" != "201" && "${KEY_CODE}" != "200" ]]; then
  echo "Failed to create API key '${IZANAMI_KEY_NAME}' (HTTP ${KEY_CODE})" >&2
  echo "${KEY_RESPONSE}" | sed '$d' >&2
  exit 1
fi

KEY_JSON="$(echo "${KEY_RESPONSE}" | sed '$d')"
IZANAMI_CLIENT_ID="$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("clientId",""))' <<<"${KEY_JSON}")"
IZANAMI_CLIENT_SECRET="$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("clientSecret",""))' <<<"${KEY_JSON}")"

if [[ -z "${IZANAMI_CLIENT_ID}" || -z "${IZANAMI_CLIENT_SECRET}" ]]; then
  echo "Failed to extract clientId/clientSecret from API key creation response" >&2
  echo "${KEY_JSON}" >&2
  exit 1
fi

echo "Seeding features used by integration tests ..." >&2
admin_json DELETE "/admin/tenants/${IZANAMI_TENANT}/features/${IZANAMI_PERFORMANCE_MODE_ID}" >/dev/null 2>&1 || true
admin_json DELETE "/admin/tenants/${IZANAMI_TENANT}/features/${IZANAMI_JSON_CONTENT_ID}" >/dev/null 2>&1 || true

BOOLEAN_FEATURE_PAYLOAD="$(python3 - "${IZANAMI_PERFORMANCE_MODE_ID}" <<'PY'
import json
import sys

feature_id = sys.argv[1]
payload = {
  "id": feature_id,
  "name": "performance-mode",
  "description": "Integration test flag (boolean)",
  "enabled": True,
  "resultType": "boolean",
  "conditions": [],
  "metadata": {}
}
print(json.dumps(payload))
PY
)"

BOOLEAN_FEATURE_RESPONSE="$(admin_json POST "/admin/tenants/${IZANAMI_TENANT}/projects/${IZANAMI_PROJECT}/features" "${BOOLEAN_FEATURE_PAYLOAD}")"
BOOLEAN_FEATURE_CODE="$(echo "${BOOLEAN_FEATURE_RESPONSE}" | tail -n 1)"
if [[ "${BOOLEAN_FEATURE_CODE}" != "201" && "${BOOLEAN_FEATURE_CODE}" != "200" ]]; then
  echo "Failed to create feature '${IZANAMI_PERFORMANCE_MODE_ID}' (HTTP ${BOOLEAN_FEATURE_CODE})" >&2
  echo "${BOOLEAN_FEATURE_RESPONSE}" | sed '$d' >&2
  exit 1
fi

OBJECT_JSON='{"name":"Izanami","flags":[{"id":"f1","active":true},{"id":"f2","active":false}],"meta":{"version":1}}'
STRING_FEATURE_PAYLOAD="$(python3 - "${IZANAMI_JSON_CONTENT_ID}" "${OBJECT_JSON}" <<'PY'
import json
import sys

feature_id = sys.argv[1]
value = sys.argv[2]
payload = {
  "id": feature_id,
  "name": "json-content",
  "description": "Integration test flag (object as JSON string)",
  "enabled": True,
  "resultType": "string",
  "value": value,
  "conditions": [],
  "metadata": {}
}
print(json.dumps(payload))
PY
)"

STRING_FEATURE_RESPONSE="$(admin_json POST "/admin/tenants/${IZANAMI_TENANT}/projects/${IZANAMI_PROJECT}/features" "${STRING_FEATURE_PAYLOAD}")"
STRING_FEATURE_CODE="$(echo "${STRING_FEATURE_RESPONSE}" | tail -n 1)"
if [[ "${STRING_FEATURE_CODE}" != "201" && "${STRING_FEATURE_CODE}" != "200" ]]; then
  echo "Failed to create feature '${IZANAMI_JSON_CONTENT_ID}' (HTTP ${STRING_FEATURE_CODE})" >&2
  echo "${STRING_FEATURE_RESPONSE}" | sed '$d' >&2
  exit 1
fi

echo "Seed complete." >&2

case "${IZANAMI_SEED_OUTPUT}" in
  github-env)
    echo "IZANAMI_CLIENT_ID=${IZANAMI_CLIENT_ID}"
    echo "IZANAMI_CLIENT_SECRET=${IZANAMI_CLIENT_SECRET}"
    echo "IZANAMI_BASE_URL=${IZANAMI_BASE_URL}"
    echo "IZANAMI_API_PATH=${IZANAMI_API_PATH}"
    echo "IZANAMI_INTEGRATION_TEST=true"
    ;;
  export)
    printf "export IZANAMI_CLIENT_ID=%q\n" "${IZANAMI_CLIENT_ID}"
    printf "export IZANAMI_CLIENT_SECRET=%q\n" "${IZANAMI_CLIENT_SECRET}"
    printf "export IZANAMI_BASE_URL=%q\n" "${IZANAMI_BASE_URL}"
    printf "export IZANAMI_API_PATH=%q\n" "${IZANAMI_API_PATH}"
    printf "export IZANAMI_INTEGRATION_TEST=%q\n" "true"
    ;;
  *)
    echo "Unknown IZANAMI_SEED_OUTPUT value: ${IZANAMI_SEED_OUTPUT}" >&2
    exit 1
    ;;
esac
