#!/usr/bin/env bash
#
# Source this file to get convenient helper aliases/functions for day-to-day development:
#
#   source ./scripts/aliases.sh
#
# Then run:
#   izsb_verify
#   izsb_install
#   izsb_it_verify
#   izsb_up / izsb_down
#

izsb_find_root() {
  if command -v git >/dev/null 2>&1; then
    local git_root
    git_root="$(git -C "${PWD}" rev-parse --show-toplevel 2>/dev/null || true)"
    if [[ -n "${git_root}" ]]; then
      printf '%s\n' "${git_root}"
      return 0
    fi
  fi

  local dir="${PWD}"
  while [[ "${dir}" != "/" ]]; do
    if [[ -f "${dir}/pom.xml" && -d "${dir}/izanami-spring-boot-autoconfigure" && -d "${dir}/izanami-spring-boot-starter" ]]; then
      printf '%s\n' "${dir}"
      return 0
    fi
    dir="$(dirname "${dir}")"
  done
  return 1
}

IZSB_ROOT="${IZSB_ROOT:-$(izsb_find_root 2>/dev/null || true)}"
if [[ -z "${IZSB_ROOT}" ]]; then
  IZSB_ROOT="${PWD}"
fi

izsb_cd() {
  cd "${IZSB_ROOT}" || return 1
}

izsb_mvn() {
  local mvn_cmd="mvn"
  if [[ -x "${IZSB_ROOT}/mvnw" ]]; then
    mvn_cmd="${IZSB_ROOT}/mvnw"
  fi
  (cd "${IZSB_ROOT}" && "${mvn_cmd}" "$@")
}

izsb_clean() { izsb_mvn -B -ntp clean; }
izsb_compile() { izsb_mvn -B -ntp -DskipTests compile; }
izsb_test() { izsb_mvn -B -ntp test; }
izsb_verify() { izsb_mvn -B -ntp verify; }
izsb_install() { izsb_mvn -B -ntp install; }

izsb_up() {
  (cd "${IZSB_ROOT}" && docker compose -f docker-compose.izanami.yml up -d)
}

izsb_down() {
  (cd "${IZSB_ROOT}" && docker compose -f docker-compose.izanami.yml down -v)
}

izsb_seed() {
  local previous_pwd="${PWD}"
  cd "${IZSB_ROOT}" || return 1

  # Keep variables in the current shell: use eval on the output.
  eval "$(IZANAMI_SEED_OUTPUT=export ./scripts/seed-izanami.sh)"

  cd "${previous_pwd}" || return 1
}

izsb_it_verify() {
  izsb_up
  izsb_seed
  IZANAMI_INTEGRATION_TEST=true izsb_mvn -B -ntp -Pintegration-tests verify
}

izsb_help() {
  cat <<'EOF'
Izanami Spring Boot Starter helpers (source ./scripts/aliases.sh)

Maven:
  izsb_clean
  izsb_compile
  izsb_test
  izsb_verify
  izsb_install

Izanami (Docker):
  izsb_up
  izsb_down
  izsb_seed
  izsb_it_verify
EOF
}

alias izsb='izsb_help'
alias izsbv='izsb_verify'
alias izsbt='izsb_test'
alias izsbi='izsb_install'
alias izsbit='izsb_it_verify'
