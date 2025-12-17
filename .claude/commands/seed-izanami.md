Seed Izanami server with test data for integration tests using the `iz` CLI.

## Prerequisites
- Izanami server running (default: http://localhost:9999)
- `iz` CLI: either in PATH or at `../izanami-go-cli/build/iz`
- `jq` for JSON parsing

## Usage
```bash
./scripts/seed-izanami.sh
```

## Configuration
Environment variables (with defaults):
- `IZANAMI_BASE_URL` = http://localhost:9999
- `IZANAMI_API_PATH` = /api
- `IZANAMI_ADMIN_USERNAME` = RESERVED_ADMIN_USER
- `IZANAMI_ADMIN_PASSWORD` = password
- `IZANAMI_TENANT` = starter-it
- `IZANAMI_PROJECT` = starter-project
- `IZANAMI_KEY_NAME` = starter-it-key
- `IZANAMI_SEED_OUTPUT` = export (or github-env)

## Features Seeded
| ID | Name | Type | Value |
|----|------|------|-------|
| a4c0d04f-69ac-41aa-a6e4-febcee541d51 | turbo-mode | boolean | true |
| b5d1e15f-7abd-42bb-b7f5-0cdef6652e62 | secret-codename | string | "Operation Thunderbolt" |
| c6e2f26f-8bce-43cc-c8f6-1def07763f73 | max-power-level | integer | 9001 |
| d7f3037f-9cdf-44dd-d9f7-2ef008874084 | discount-rate | double | 0.15 |

## Output
The script outputs environment variables for integration tests:
```bash
export IZANAMI_CLIENT_ID=<clientId>
export IZANAMI_CLIENT_SECRET=<clientSecret>
export IZANAMI_BASE_URL=http://localhost:9999
export IZANAMI_API_PATH=/api
export IZANAMI_INTEGRATION_TEST=true
```

Use with `eval`:
```bash
eval "$(./scripts/seed-izanami.sh)"
mvn verify -Pintegration-tests
```

## Debugging
Enable verbose output to see `iz` CLI requests/responses:
```bash
IZ_VERBOSE=1 ./scripts/seed-izanami.sh
```

## Notes
- Uses `iz` CLI from PATH or `../izanami-go-cli/build/iz`
- All operations are idempotent (safe to run multiple times)
- Feature IDs are fixed UUIDs matching integration test expectations
- Verbose output is sent to stderr, environment exports to stdout
