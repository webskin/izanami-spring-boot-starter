#!/usr/bin/env bash
set -euo pipefail

# Debug: use IZ_VERBOSE=1 to enable verbose output (prints to stderr)
# Example: IZ_VERBOSE=1 ./scripts/seed-izanami.sh

# Configuration with defaults
IZANAMI_BASE_URL="${IZANAMI_BASE_URL:-http://localhost:9999}"
IZANAMI_API_PATH="${IZANAMI_API_PATH:-/api}"
IZANAMI_ADMIN_USERNAME="${IZANAMI_ADMIN_USERNAME:-${IZANAMI_ADMIN_DEFAULT_USERNAME:-RESERVED_ADMIN_USER}}"
IZANAMI_ADMIN_PASSWORD="${IZANAMI_ADMIN_PASSWORD:-${IZANAMI_ADMIN_DEFAULT_PASSWORD:-password}}"
IZANAMI_TENANT="${IZANAMI_TENANT:-starter-it}"
IZANAMI_PROJECT="${IZANAMI_PROJECT:-starter-project}"
IZANAMI_KEY_NAME="${IZANAMI_KEY_NAME:-starter-it-key}"
IZANAMI_SEED_OUTPUT="${IZANAMI_SEED_OUTPUT:-export}" # export|github-env

# Feature IDs used by integration tests
#
# This script creates the following features in Izanami:
#
# | ID                                   | Name            | Type    | Value                  | Enabled | Condition                              |
# |--------------------------------------|-----------------|---------|------------------------|---------|----------------------------------------|
# | a4c0d04f-69ac-41aa-a6e4-febcee541d51 | turbo-mode      | boolean | true (enabled)         | true    | (none)                                 |
# | b5d1e15f-7abd-42bb-b7f5-0cdef6652e62 | secret-codename | string  | Operation Thunderbolt  | true    | (none)                                 |
# | c6e2f26f-8bce-43cc-c8f6-1def07763f73 | max-power-level | number  | 9001                   | true    | (none)                                 |
# | d7f3037f-9cdf-44dd-d9f7-2ef008874084 | discount-rate   | number  | 0.15                   | true    | (none)                                 |
# | e8f4148f-0def-55ee-eaf8-3f0109985195 | json-config     | string  | {"enabled":true,...}   | true    | (none)                                 |
# | f9a5259f-1ef0-66ff-fbf9-4f020aa96206 | inactive-bool   | boolean | (none)                 | false   | (none)                                 |
# | 0ab6360f-2f01-7700-0c0a-5f131bba7317 | inactive-string | string  | hidden value           | false   | (none)                                 |
# | 1bc7471f-3012-8811-1d1b-6f242ccb8428 | inactive-number | number  | 42                     | false   | (none)                                 |
# | 2cd8582f-4123-9922-2e2c-7f353ddc9539 | user-targeted   | boolean | (conditional)          | true    | UserList: provider-user, other-allowed |
# | 3de9693f-5234-aa33-3f3d-8f464eec0640 | context-targeted| boolean | (context overloads)    | false   | Context-based overloads (see below)    |
#
# Context-targeted feature has overloads:
#   - PROD context: enabled for user "prod-user"
#   - PROD/mobile context: enabled for user "mobile-user"
#   - PROD/mobile/EU context: enabled for user "eu-user"
#
IZANAMI_TURBO_MODE_ID="${IZANAMI_TURBO_MODE_ID:-a4c0d04f-69ac-41aa-a6e4-febcee541d51}"
IZANAMI_SECRET_CODENAME_ID="${IZANAMI_SECRET_CODENAME_ID:-b5d1e15f-7abd-42bb-b7f5-0cdef6652e62}"
IZANAMI_MAX_POWER_LEVEL_ID="${IZANAMI_MAX_POWER_LEVEL_ID:-c6e2f26f-8bce-43cc-c8f6-1def07763f73}"
IZANAMI_DISCOUNT_RATE_ID="${IZANAMI_DISCOUNT_RATE_ID:-d7f3037f-9cdf-44dd-d9f7-2ef008874084}"
IZANAMI_JSON_CONFIG_ID="${IZANAMI_JSON_CONFIG_ID:-e8f4148f-0def-55ee-eaf8-3f0109985195}"
IZANAMI_INACTIVE_BOOL_ID="${IZANAMI_INACTIVE_BOOL_ID:-f9a5259f-1ef0-66ff-fbf9-4f020aa96206}"
IZANAMI_INACTIVE_STRING_ID="${IZANAMI_INACTIVE_STRING_ID:-0ab6360f-2f01-7700-0c0a-5f131bba7317}"
IZANAMI_INACTIVE_NUMBER_ID="${IZANAMI_INACTIVE_NUMBER_ID:-1bc7471f-3012-8811-1d1b-6f242ccb8428}"
IZANAMI_USER_TARGETED_ID="${IZANAMI_USER_TARGETED_ID:-2cd8582f-4123-9922-2e2c-7f353ddc9539}"
IZANAMI_CONTEXT_TARGETED_ID="${IZANAMI_CONTEXT_TARGETED_ID:-3de9693f-5234-aa33-3f3d-8f464eec0640}"

# Locate iz CLI
find_iz_cmd() {
  local cmd
  if command -v iz &>/dev/null; then
    cmd="iz"
  elif [[ -x "../izanami-go-cli/build/iz" ]]; then
    cmd="../izanami-go-cli/build/iz"
  else
    echo "Error: iz CLI not found. Install it or build from ../izanami-go-cli" >&2
    exit 1
  fi

  # Add --verbose flag if IZ_VERBOSE is set
  if [[ "${IZ_VERBOSE:-}" == "1" ]]; then
    cmd="${cmd} --verbose"
  fi

  echo "${cmd}"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_cmd jq
IZ_CMD="$(find_iz_cmd)"

if [[ "${IZ_VERBOSE:-}" == "1" ]]; then
  echo "Using iz CLI: ${IZ_CMD} (verbose mode enabled)" >&2
else
  echo "Using iz CLI: ${IZ_CMD}" >&2
fi

# Wait for Izanami to be healthy
wait_for_izanami() {
  echo "Waiting for Izanami to be healthy ..." >&2
  for _ in $(seq 1 30); do
    if ${IZ_CMD} health --url "${IZANAMI_BASE_URL}" &>/dev/null; then
      echo "Izanami is healthy" >&2
      return 0
    fi
    sleep 2
  done
  echo "Izanami did not become healthy in time" >&2
  return 1
}

wait_for_izanami

# Ensure profile exists (to avoid interactive prompt on first run)
IZANAMI_PROFILE="${IZANAMI_PROFILE:-izsbit}"
if ! ${IZ_CMD} profiles list 2>/dev/null | grep -q "^${IZANAMI_PROFILE}\$"; then
  echo "Creating iz CLI profile '${IZANAMI_PROFILE}' ..." >&2
  ${IZ_CMD} profiles add "${IZANAMI_PROFILE}" --url "${IZANAMI_BASE_URL}" >&2
fi

# Login as admin
echo "Logging in as admin '${IZANAMI_ADMIN_USERNAME}' ..." >&2
${IZ_CMD} login "${IZANAMI_BASE_URL}" "${IZANAMI_ADMIN_USERNAME}" --password "${IZANAMI_ADMIN_PASSWORD}" --profile "${IZANAMI_PROFILE}" >&2

# Ensure tenant exists
echo "Ensuring tenant '${IZANAMI_TENANT}' ..." >&2
${IZ_CMD} admin tenants create "${IZANAMI_TENANT}" \
  --description "Integration tests tenant" >/dev/null 2>&1 || true

# Ensure project exists
echo "Ensuring project '${IZANAMI_PROJECT}' in tenant '${IZANAMI_TENANT}' ..." >&2
${IZ_CMD} admin projects create "${IZANAMI_PROJECT}" \
  --tenant "${IZANAMI_TENANT}" \
  --description "Integration tests project" >/dev/null 2>&1 || true

# Create API key (delete and recreate to get fresh credentials)
echo "Creating API key '${IZANAMI_KEY_NAME}' in tenant '${IZANAMI_TENANT}' ..." >&2
${IZ_CMD} admin keys delete "${IZANAMI_KEY_NAME}" \
  --tenant "${IZANAMI_TENANT}" \
  --force 2>/dev/null || true

KEY_JSON="$(${IZ_CMD} admin keys create "${IZANAMI_KEY_NAME}" \
  --tenant "${IZANAMI_TENANT}" \
  --description "Integration tests key" \
  --enabled \
  --admin \
  --output json)"

IZANAMI_CLIENT_ID="$(echo "${KEY_JSON}" | jq -r '.clientId')"
IZANAMI_CLIENT_SECRET="$(echo "${KEY_JSON}" | jq -r '.clientSecret')"

if [[ -z "${IZANAMI_CLIENT_ID}" || "${IZANAMI_CLIENT_ID}" == "null" ]]; then
  echo "Failed to extract clientId from API key creation response" >&2
  echo "${KEY_JSON}" >&2
  exit 1
fi

echo "Seeding features used by integration tests ..." >&2

# Helper function to create a feature
create_feature() {
  local id="$1"
  local name="$2"
  local description="$3"
  local result_type="$4"
  local value="${5:-}"
  local enabled="${6:-true}"

  # Delete existing feature (ignore error)
  ${IZ_CMD} admin features delete "${id}" \
    --tenant "${IZANAMI_TENANT}" \
    --force 2>/dev/null || true

  # Build JSON payload
  local json
  if [[ -n "${value}" ]]; then
    json=$(jq -n \
      --arg id "${id}" \
      --arg name "${name}" \
      --arg desc "${description}" \
      --arg rt "${result_type}" \
      --arg val "${value}" \
      --argjson en "${enabled}" \
      '{id: $id, name: $name, description: $desc, enabled: $en, resultType: $rt, value: $val}')
  else
    json=$(jq -n \
      --arg id "${id}" \
      --arg name "${name}" \
      --arg desc "${description}" \
      --arg rt "${result_type}" \
      --argjson en "${enabled}" \
      '{id: $id, name: $name, description: $desc, enabled: $en, resultType: $rt}')
  fi

  ${IZ_CMD} admin features create "${name}" \
    --tenant "${IZANAMI_TENANT}" \
    --project "${IZANAMI_PROJECT}" \
    --data "${json}" >&2
}

# Helper function to create a feature with activation conditions
create_feature_with_conditions() {
  local id="$1"
  local name="$2"
  local description="$3"
  local result_type="$4"
  local conditions_json="$5"

  # Delete existing feature (ignore error)
  ${IZ_CMD} admin features delete "${id}" \
    --tenant "${IZANAMI_TENANT}" \
    --force 2>/dev/null || true

  # Build JSON payload with conditions
  local json
  json=$(jq -n \
    --arg id "${id}" \
    --arg name "${name}" \
    --arg desc "${description}" \
    --arg rt "${result_type}" \
    --argjson cond "${conditions_json}" \
    '{id: $id, name: $name, description: $desc, enabled: true, resultType: $rt, conditions: $cond}')

  ${IZ_CMD} admin features create "${name}" \
    --tenant "${IZANAMI_TENANT}" \
    --project "${IZANAMI_PROJECT}" \
    --data "${json}" >&2
}

# Helper function to create a global context
create_global_context() {
  local name="$1"

  # Create global context (ignore error if already exists)
  ${IZ_CMD} admin contexts create "${name}" \
    --global \
    --tenant "${IZANAMI_TENANT}" 2>/dev/null || true
}

# Helper function to create a project-specific context with parent
create_project_context() {
  local name="$1"
  local parent="$2"

  # Create project context (ignore error if already exists)
  ${IZ_CMD} admin contexts create "${name}" \
    --project "${IZANAMI_PROJECT}" \
    --parent "${parent}" \
    --tenant "${IZANAMI_TENANT}" 2>/dev/null || true
}

# Helper function to set a feature overload for a context
create_overload() {
  local feature_name="$1"
  local context="$2"
  local data_json="$3"

  ${IZ_CMD} admin overloads set "${feature_name}" \
    --context "${context}" \
    --project "${IZANAMI_PROJECT}" \
    --tenant "${IZANAMI_TENANT}" \
    --data "${data_json}" >&2
}

# Create boolean feature: turbo-mode
create_feature \
  "${IZANAMI_TURBO_MODE_ID}" \
  "turbo-mode" \
  "Enable turbo mode for maximum performance" \
  "boolean"

# Create string feature: secret-codename
create_feature \
  "${IZANAMI_SECRET_CODENAME_ID}" \
  "secret-codename" \
  "The secret codename for this release" \
  "string" \
  "Operation Thunderbolt"

# Create integer feature: max-power-level
create_feature \
  "${IZANAMI_MAX_POWER_LEVEL_ID}" \
  "max-power-level" \
  "Maximum power level allowed (over 9000!)" \
  "number" \
  "9001"

# Create double feature: discount-rate
create_feature \
  "${IZANAMI_DISCOUNT_RATE_ID}" \
  "discount-rate" \
  "Current discount rate as a decimal" \
  "number" \
  "0.15"

# Create string feature with JSON value: json-config
create_feature \
  "${IZANAMI_JSON_CONFIG_ID}" \
  "json-config" \
  "Configuration stored as JSON string" \
  "string" \
  '{
  "enabled": true,
  "settings": {
    "theme": "dark",
    "maxRetries": 3
  }
}'

# Create inactive boolean feature
create_feature \
  "${IZANAMI_INACTIVE_BOOL_ID}" \
  "inactive-bool" \
  "Disabled boolean feature for testing" \
  "boolean" \
  "" \
  "false"

# Create inactive string feature
create_feature \
  "${IZANAMI_INACTIVE_STRING_ID}" \
  "inactive-string" \
  "Disabled string feature for testing" \
  "string" \
  "hidden value" \
  "false"

# Create inactive number feature
create_feature \
  "${IZANAMI_INACTIVE_NUMBER_ID}" \
  "inactive-number" \
  "Disabled number feature for testing" \
  "number" \
  "42" \
  "false"

# Create user-targeted feature with UserList condition
# This feature is enabled only for users "provider-user" and "other-allowed"
create_feature_with_conditions \
  "${IZANAMI_USER_TARGETED_ID}" \
  "user-targeted" \
  "Feature enabled only for specific users" \
  "boolean" \
  '[{"rule": {"type": "UserList", "users": ["provider-user", "other-allowed"]}}]'

# Create contexts for context-targeted feature testing
echo "Creating contexts for context-targeted feature ..." >&2
create_global_context "PROD"
create_project_context "mobile" "PROD"
create_project_context "EU" "PROD/mobile"

# Create context-targeted feature (disabled at base level)
create_feature \
  "${IZANAMI_CONTEXT_TARGETED_ID}" \
  "context-targeted" \
  "Feature with context-based overloads for testing" \
  "boolean" \
  "" \
  "false"

# Create overloads for context-targeted feature
# PROD context: enabled for user "prod-user"
create_overload "context-targeted" "PROD" \
  '{"enabled": true, "resultType": "boolean", "conditions": [{"rule": {"type": "UserList", "users": ["prod-user"]}}]}'

# PROD/mobile context: enabled for user "mobile-user"
create_overload "context-targeted" "PROD/mobile" \
  '{"enabled": true, "resultType": "boolean", "conditions": [{"rule": {"type": "UserList", "users": ["mobile-user"]}}]}'

# PROD/mobile/EU context: enabled for user "eu-user"
create_overload "context-targeted" "PROD/mobile/EU" \
  '{"enabled": true, "resultType": "boolean", "conditions": [{"rule": {"type": "UserList", "users": ["eu-user"]}}]}'

echo "Seed complete." >&2

# Output environment variables
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
