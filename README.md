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

### OpenFeature flags

```yaml
openfeature:
  flags:
    - key: "a4c0d04f-69ac-41aa-a6e4-febcee541d51"
      name: "turbo-mode"
      description: "Enable turbo mode for maximum performance"
      errorStrategy: "DEFAULT_VALUE"
      valueType: "boolean"
      defaultValue: false
    - key: "b5d1e15f-7abd-42bb-b7f5-0cdef6652e62"
      name: "secret-codename"
      description: "The secret codename for this release"
      errorStrategy: "DEFAULT_VALUE"
      valueType: "string"
      defaultValue: "classified"
    - key: "c6e2f26f-8bce-43cc-c8f6-1def07763f73"
      name: "max-power-level"
      description: "Maximum power level allowed"
      errorStrategy: "DEFAULT_VALUE"
      valueType: "integer"
      defaultValue: 100
    - key: "d7f3037f-9cdf-44dd-d9f7-2ef008874084"
      name: "discount-rate"
      description: "Current discount rate as a decimal"
      errorStrategy: "DEFAULT_VALUE"
      valueType: "double"
      defaultValue: 0.0
```

## Usage

### Inject the OpenFeature client

```java
import dev.openfeature.sdk.Client;
import org.springframework.stereotype.Service;

@Service
public class FeatureService {
  private final Client client;

  public FeatureService(Client client) {
    this.client = client;
  }

  public boolean isTurboModeEnabled() {
    return Boolean.TRUE.equals(client.getBooleanValue("turbo-mode", false));
  }

  public String getSecretCodename() {
    return client.getStringValue("secret-codename", "classified");
  }

  public int getMaxPowerLevel() {
    return client.getIntegerValue("max-power-level", 100);
  }

  public double getDiscountRate() {
    return client.getDoubleValue("discount-rate", 0.0);
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

## Actuator Health Indicator

When Spring Boot Actuator is on the classpath, an `IzanamiHealthIndicator` is automatically registered.

It reports:
- `UP` - Izanami client is connected and flags are preloaded
- `DOWN` - Flag preloading is still in progress
- `OUT_OF_SERVICE` - Preloading completed but failed to connect

Check health status:

```bash
curl http://localhost:8080/actuator/health
```

### Kubernetes Readiness Probe

To block traffic until flags are preloaded, include the Izanami health indicator in the readiness group:

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState,izanami
```

### Waiting for Flags at Startup

If you need flags immediately at application startup (e.g., in `@PostConstruct` or `ApplicationRunner`), wait for preloading to complete:

```java
import fr.maif.izanami.spring.service.IzanamiService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MyStartupRunner implements ApplicationRunner {

    private final IzanamiService izanamiService;
    private final Client featureClient;

    public MyStartupRunner(IzanamiService izanamiService, Client featureClient) {
        this.izanamiService = izanamiService;
        this.featureClient = featureClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        izanamiService.whenLoaded().join(); // Wait for preload
        boolean enabled = featureClient.getBooleanValue("turbo-mode", false);
        // ... use the flag value
    }
}
```

For typical HTTP request handling, this is usually unnecessary since flags are preloaded by the time traffic arrives.

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

It also creates the features used by integration tests:

- `turbo-mode` (boolean, enabled=true)
- `secret-codename` (string, value="Operation Thunderbolt")
- `max-power-level` (integer, value=9001)
- `discount-rate` (double, value=0.15)

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
