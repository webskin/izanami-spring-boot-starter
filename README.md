# izanami-spring-boot-starter

Spring Boot starter providing a resilient Izanami-backed OpenFeature `FeatureProvider`.

## Modules

- `izanami-spring-boot-autoconfigure`: auto-configuration + implementation
- `izanami-spring-boot-starter`: thin starter bringing transitive dependencies

## Maven coordinates

**Starter (recommended)**

```xml
<dependency>
  <groupId>fr.maif</groupId>
  <artifactId>izanami-spring-boot-starter</artifactId>
  <version><!-- your version --></version>
</dependency>
```

**Autoconfigure (advanced / if you want full control over dependencies)**

```xml
<dependency>
  <groupId>fr.maif</groupId>
  <artifactId>izanami-spring-boot-autoconfigure</artifactId>
  <version><!-- your version --></version>
</dependency>
```

## Configuration

### Izanami

```yaml
izanami:
  enabled: true
  base-url: ${IZANAMI_BASE_URL:http://localhost:9999}
  api-path: ${IZANAMI_API_PATH:/api}
  client-id: ${IZANAMI_CLIENT_ID:}
  client-secret: ${IZANAMI_CLIENT_SECRET:}
  cache:
    enabled: true
    refresh-interval: 5m
    sse:
      enabled: true
      keep-alive-interval: 25s
```

Disable everything:

```yaml
izanami:
  enabled: false
```

### OpenFeature flags (including `object`)

```yaml
openfeature:
  flags:
    - id: "a4c0d04f-69ac-41aa-a6e4-febcee541d51"
      name: "performance-mode"
      description: "Enable performance optimizations"
      errorStrategy: "DEFAULT_VALUE"
      valueType: "boolean"
      defaultValue: false
    - id: "00812ba5-aebc-49e8-959a-4b96a5cebbff"
      name: "json-content"
      description: "Example object flag (JSON)"
      errorStrategy: "DEFAULT_VALUE"
      valueType: "object"
      defaultValue:
        name: "fallback"
        flags:
          - id: "f1"
            active: true
          - id: "f2"
            active: false
        meta:
          version: 1
```

## Usage

### Inject the OpenFeature client

```java
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.Value;
import org.springframework.stereotype.Service;

@Service
public class FeatureService {
  private final Client client;

  public FeatureService(Client client) {
    this.client = client;
  }

  public boolean performanceMode() {
    return Boolean.TRUE.equals(client.getBooleanValue("performance-mode", false));
  }

  public Value jsonContent() {
    return client.getObjectValue("json-content", new Value());
  }
}
```

### Optional explicit opt-in

Auto-configuration is enabled by default when the starter is present.
If you prefer an explicit opt-in, use `@EnableIzanami`:

```java
import fr.maif.izanami.spring.autoconfigure.EnableIzanami;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableIzanami
public class IzanamiConfig {}
```

`izanami.enabled=false` still disables everything.

## Testing

### Dev aliases

Source helper aliases/functions:

```bash
source ./scripts/aliases.sh
izsb
```

### Unit tests

```bash
mvn -B -ntp verify
```

### Integration tests (real Izanami on `http://localhost:9999`)

Integration tests are opt-in:

- Maven profile: `-Pintegration-tests`
- Environment gate: `IZANAMI_INTEGRATION_TEST=true`

Start Izanami:

```bash
docker compose -f docker-compose.izanami.yml up -d
```

Seed a dedicated tenant/project, create an API key, and export the required environment variables:

```bash
eval "$(IZANAMI_SEED_OUTPUT=export ./scripts/seed-izanami.sh)"
```

This script uses the admin credentials from `docker-compose.izanami.yml` by default:

- `IZANAMI_ADMIN_USERNAME=RESERVED_ADMIN_USER`
- `IZANAMI_ADMIN_PASSWORD=password`

It also creates the two features used by integration tests:

- `performance-mode` (boolean, enabled)
- `json-content` (string containing JSON, parsed as an OpenFeature object)

Run:

```bash
mvn -B -ntp -Pintegration-tests verify
```

## CI

GitHub Actions workflow: `.github/workflows/ci.yml`

- Runs unit tests on JDK 17.
- Starts Izanami via Compose, seeds flags, runs integration tests.
- Runs SonarCloud analysis when the following secrets are present:
  - `SONAR_TOKEN`
  - `SONAR_ORGANIZATION`
  - `SONAR_PROJECT_KEY`
