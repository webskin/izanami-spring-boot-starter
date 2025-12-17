Run integration tests against a local Izanami instance.

Steps:
1. Start Izanami containers using docker-compose (supports docker and podman)
2. Wait for Izanami to be healthy and seed test data
3. Run integration tests with exported environment variables
4. Stop and remove containers after tests complete
5. Report test results

Execute the following commands:
1. `docker compose -f docker-compose.izanami.yml up -d` (or podman compose)
2. `./scripts/seed-izanami.sh` - capture and use the exported env vars
3. Run: `IZANAMI_INTEGRATION_TEST=true mvn verify -Pintegration-tests` with the env vars from step 2
4. `docker compose -f docker-compose.izanami.yml down -v`
