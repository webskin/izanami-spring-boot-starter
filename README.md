# izanami-spring-boot-starter

Spring Boot starter for Izanami feature flags, providing `IzanamiService` (fluent async API) and `ExtendedOpenFeatureClient` (OpenFeature-compliant client).

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

Use this module when you need to exclude transitive dependencies or provide your own versions of the Izanami Java client or OpenFeature SDK.

```xml
<dependency>
  <groupId>fr.maif</groupId>
  <artifactId>izanami-spring-boot-autoconfigure</artifactId>
  <version><!-- your version --></version>
</dependency>
```

## Configuration

### Izanami

Recommended configuration with SSE-based real-time updates:

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

When `defaultValue` is provided, the error strategy defaults to `DEFAULT_VALUE` automatically:

```yaml
openfeature:
  flags:
    - key: "a4c0d04f-69ac-41aa-a6e4-febcee541d51"
      name: "turbo-mode"
      description: "Enable turbo mode for maximum performance"
      valueType: "boolean"
      defaultValue: false
    - key: "b5d1e15f-7abd-42bb-b7f5-0cdef6652e62"
      name: "secret-codename"
      description: "The secret codename for this release"
      valueType: "string"
      defaultValue: "classified"
    - key: "c6e2f26f-8bce-43cc-c8f6-1def07763f73"
      name: "max-power-level"
      description: "Maximum power level allowed"
      valueType: "integer"
      defaultValue: 100
    - key: "d7f3037f-9cdf-44dd-d9f7-2ef008874084"
      name: "discount-rate"
      description: "Current discount rate as a decimal"
      valueType: "double"
      defaultValue: 0.0
    - key: "e8f4148f-0def-55ee-eaf8-3f0109985195"
      name: "json-config"
      description: "Configuration stored as JSON string"
      valueType: "string"
      defaultValue: "{}"
```

### Alternative Error Strategies

> **Warning**: `FAIL`, `NULL_VALUE`, and `CALLBACK` strategies are only fully supported via `IzanamiService`.
> When using `ExtendedOpenFeatureClient`, these strategies fall back to the caller-provided default value.

```yaml
openfeature:
  flags:
    # FAIL strategy - throws exception on error (IzanamiService only)
    - key: "..."
      name: "critical-flag"
      valueType: "boolean"
      errorStrategy: "FAIL"

    # NULL_VALUE strategy - returns null on error (IzanamiService only)
    - key: "..."
      name: "optional-flag"
      valueType: "string"
      errorStrategy: "NULL_VALUE"

    # CALLBACK strategy - invokes custom bean on error
    - key: "..."
      name: "fallback-flag"
      valueType: "string"
      errorStrategy: "CALLBACK"
      callbackBean: "myErrorCallback"
```

## Usage

### Simple Usage with IzanamiService (Fluent API)

```java
import fr.maif.izanami.spring.service.IzanamiService;
import org.springframework.beans.factory.annotation.Autowired;

@Autowired IzanamiService izanamiService;

// Simple evaluation (no user/context)
Boolean enabled = izanamiService.forFlagName("turbo-mode")
    .booleanValue()
    .join();

// With user targeting
String codename = izanamiService.forFlagKey("b5d1e15f-7abd-42bb-b7f5-0cdef6652e62")
    .withUser("user-123")
    .stringValue()
    .join();

// With user and context
BigDecimal rate = izanamiService.forFlagName("discount-rate")
    .withUser("user-123")
    .withContext("premium-tier")
    .numberValue()
    .join();
```

### Simple Usage with ExtendedOpenFeatureClient

In OpenFeature, the **targeting key** identifies the entity (user) being evaluated. Additional context is passed via the `context` attribute in `EvaluationContext`.

```java
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClient;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Value;

@Autowired ExtendedOpenFeatureClient client;

// Simple evaluation (no targeting)
Boolean enabled = client.getBooleanValue("a4c0d04f-69ac-41aa-a6e4-febcee541d51");

// By name
String codename = client.getStringValueByName("secret-codename");

// With user targeting (targeting key = user identifier)
EvaluationContext userCtx = new ImmutableContext("user-123");
Boolean userEnabled = client.getBooleanValueByName("turbo-mode", userCtx);

// With user and context targeting
Map<String, Value> attributes = Map.of("context", new Value("premium-tier"));
EvaluationContext fullCtx = new ImmutableContext("user-123", attributes);
Boolean premium = client.getBooleanValueByName("turbo-mode", fullCtx);
```

### Advanced Usage with ResultWithMetadata (IzanamiService)

```java
import fr.maif.izanami.spring.service.IzanamiService;
import fr.maif.izanami.spring.service.ResultWithMetadata;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;

ResultWithMetadata result = izanamiService.forFlagName("turbo-mode")
    .withUser("user-123")
    .featureResultWithMetadata()
    .join();

// Access the raw Izanami result
IzanamiResult.Result izanamiResult = result.result();
boolean value = izanamiResult.booleanValue(BooleanCastStrategy.LAX);

// Access metadata
Map<String, String> metadata = result.metadata();
String valueSource = metadata.get(FlagMetadataKeys.FLAG_VALUE_SOURCE);
// "IZANAMI" for successful evaluation
// "IZANAMI_ERROR_STRATEGY" for error strategy fallback
```

### Advanced Usage with FlagEvaluationDetails (ExtendedOpenFeatureClient)

```java
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.Value;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;

// With user and context targeting
Map<String, Value> attributes = Map.of("context", new Value("production"));
EvaluationContext ctx = new ImmutableContext("user-123", attributes);

FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("turbo-mode", ctx);

Boolean value = details.getValue();
String reason = details.getReason();        // e.g., "DISABLED" for false, "UNKNOWN" for true
ErrorCode errorCode = details.getErrorCode(); // null on success
String valueSource = details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE);
```

## Important: Error Strategy Limitations

When using OpenFeature clients (`dev.openfeature.sdk.Client` or `fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClient`), only the `DEFAULT_VALUE` error strategy is fully supported.

**The following error strategies have limited support through OpenFeature:**

| Strategy | Expected Behavior | Actual Behavior (OpenFeature) |
|----------|-------------------|-------------------------------|
| `FAIL` | Throws exception | Returns caller-provided default value |
| `NULL_VALUE` | Returns null | Returns caller-provided default value |
| `CALLBACK` | Invokes callback bean | Returns caller-provided default value |

For full support of `FAIL`, `NULL_VALUE`, and `CALLBACK` error strategies, use `IzanamiService` directly:

```java
// FAIL strategy - throws CompletionException on error
izanamiService.forFlagName("my-flag")
    .stringValue()
    .join();

// NULL_VALUE strategy - returns null on error
String value = izanamiService.forFlagName("my-flag")
    .stringValue()
    .join();

// CALLBACK strategy - invokes callback bean and returns its value on error
String value = izanamiService.forFlagName("my-flag")
    .stringValue()
    .join();
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

**Prerequisite**: The [iz CLI](https://github.com/MAIF/izanami-go-cli) is required to seed test data. Install it or build from source at `../izanami-go-cli`.

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
- `json-config` (string, value=`{"enabled":true,"settings":{"theme":"dark","maxRetries":3}}`)

Run:

```bash
mvn -B -ntp -Pintegration-tests verify
```

## CI

GitHub Actions workflow: `.github/workflows/ci.yml`

- Runs unit tests on JDK 17.
- Starts Izanami via Compose, seeds flags, runs integration tests.
