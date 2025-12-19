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

### Custom Configuration Prefix

If you need to nest the configuration under a custom prefix (e.g., `organisation.izanami` instead of `izanami`), define your own beans with `@Primary`:

```java
import fr.maif.izanami.spring.autoconfigure.IzanamiProperties;
import fr.maif.izanami.spring.openfeature.FlagsProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CustomIzanamiConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "organisation.izanami")
    public IzanamiProperties izanamiProperties() {
        return new IzanamiProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "organisation.openfeature")
    public FlagsProperties flagsProperties() {
        return new FlagsProperties();
    }
}
```

Then configure your YAML under the custom prefix:

```yaml
organisation:
  izanami:
    base-url: ${IZANAMI_BASE_URL:http://localhost:9999}
    client-id: ${IZANAMI_CLIENT_ID:}
    client-secret: ${IZANAMI_CLIENT_SECRET:}
  openfeature:
    flags:
      - key: "a4c0d04f-69ac-41aa-a6e4-febcee541d51"
        name: "turbo-mode"
        valueType: "boolean"
        defaultValue: false
```

## Usage

### Simple Usage with IzanamiService (Fluent API)

```java
import fr.maif.izanami.spring.service.api.IzanamiService;
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

### Batch Evaluation

OpenFeature evaluates flags one at a time. For batch evaluation of multiple flags in a single request, use `IzanamiService.forFlagKeys()` or `forFlagNames()`:

```java
import fr.maif.izanami.spring.service.api.IzanamiService;
import fr.maif.izanami.spring.service.api.BatchResult;

// Batch evaluation by keys (UUIDs)
BatchResult result = izanamiService.forFlagKeys(
        "a4c0d04f-69ac-41aa-a6e4-febcee541d51",
        "b5d1e15f-7abd-42bb-b7f5-0cdef6652e62",
        "c6e2f26f-8bce-43cc-c8f6-1def07763f73")
    .withUser("user-123")
    .withContext("production")
    .values()
    .join();

Boolean turboEnabled = result.booleanValue("a4c0d04f-69ac-41aa-a6e4-febcee541d51");
String codename = result.stringValue("b5d1e15f-7abd-42bb-b7f5-0cdef6652e62");
BigDecimal powerLevel = result.numberValue("c6e2f26f-8bce-43cc-c8f6-1def07763f73");

// Batch evaluation by names (results are accessible by name)
BatchResult byName = izanamiService.forFlagNames("turbo-mode", "secret-codename")
    .withUser("user-123")
    .values()
    .join();

Boolean enabled = byName.booleanValue("turbo-mode");
String secret = byName.stringValue("secret-codename");

// With details
ResultValueWithDetails<Boolean> details = result.booleanValueDetails("a4c0d04f-...");
String source = details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE);
```

**BatchResult methods:**

| Method | Description |
|--------|-------------|
| `booleanValue(id)` | Get boolean value by flag key/name |
| `stringValue(id)` | Get string value by flag key/name |
| `numberValue(id)` | Get BigDecimal value by flag key/name |
| `booleanValueDetails(id)` | Get boolean with metadata |
| `stringValueDetails(id)` | Get string with metadata |
| `numberValueDetails(id)` | Get number with metadata |
| `flagIdentifiers()` | Get all flag identifiers in result |
| `hasFlag(id)` | Check if flag is in result |

**Builder options:**

| Method | Description |
|--------|-------------|
| `withUser(user)` | Set user identifier for all flags |
| `withContext(context)` | Set evaluation context for all flags |
| `ignoreCache(true)` | Bypass cache for this request |
| `values()` | Execute and return `CompletableFuture<BatchResult>` |

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

### Advanced Usage with ValueDetails (IzanamiService)

Use `*ValueDetails()` methods to get both the typed value and evaluation metadata:

```java
import fr.maif.izanami.spring.service.api.IzanamiService;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;

// Boolean with details
ResultValueWithDetails<Boolean> boolResult = izanamiService.forFlagName("turbo-mode")
    .withUser("user-123")
    .booleanValueDetails()
    .join();

Boolean enabled = boolResult.value();
Map<String, String> metadata = boolResult.metadata();

// String with details
ResultValueWithDetails<String> stringResult = izanamiService.forFlagName("secret-codename")
    .withUser("user-123")
    .stringValueDetails()
    .join();

// Number with details
ResultValueWithDetails<BigDecimal> numberResult = izanamiService.forFlagName("discount-rate")
    .withUser("user-123")
    .numberValueDetails()
    .join();
```

**Metadata keys:**

| Key | Description |
|-----|-------------|
| `FLAG_VALUE_SOURCE` | Where the value originated |
| `FLAG_EVALUATION_REASON` | Why this value was returned |

**`FLAG_VALUE_SOURCE` values:**

| Value | Meaning |
|-------|---------|
| `IZANAMI` | Value from Izanami server |
| `APPLICATION_ERROR_STRATEGY` | Disabled feature, using configured `defaultValue` |
| `IZANAMI_ERROR_STRATEGY` | Server error, using Izanami client error strategy |

**`FLAG_EVALUATION_REASON` values:**

| Value | Meaning |
|-------|---------|
| `ORIGIN_OR_CACHE` | Feature is active, value from Izanami |
| `DISABLED` | Feature is disabled (boolean=false, or non-boolean using default) |
| `ERROR` | Server error occurred |

```java
// Example: checking evaluation details
String source = metadata.get(FlagMetadataKeys.FLAG_VALUE_SOURCE);
String reason = metadata.get(FlagMetadataKeys.FLAG_EVALUATION_REASON);

if ("DISABLED".equals(reason)) {
    // Feature is disabled - value is false (boolean) or defaultValue (string/number)
}
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

FlagEvaluationDetails<Boolean> details = client.getBooleanDetailsByName("turbo-mode", ctx);

Boolean value = details.getValue();
String reason = details.getReason();        // e.g., "DISABLED", "ORIGIN_OR_CACHE"
ErrorCode errorCode = details.getErrorCode(); // null on success
String valueSource = details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE);
```

### Direct IzanamiClient Access (Advanced)

For advanced use cases requiring direct access to the underlying [Izanami Java client](https://github.com/MAIF/izanami-java-client), use `unwrapClient()`:

```java
import fr.maif.IzanamiClient;
import fr.maif.izanami.spring.service.api.IzanamiService;

@Autowired IzanamiService izanamiService;

// Access the underlying client (returns Optional)
Optional<IzanamiClient> client = izanamiService.unwrapClient();

client.ifPresent(c -> {
    // Use native IzanamiClient API directly
    // See: https://github.com/MAIF/izanami-java-client
});
```

> **Note**: The client may be absent if Izanami is not configured or failed to initialize. Always handle the `Optional` appropriately.

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

## OpenFeature Notice

### Blocking Calls

OpenFeature API methods are **synchronous/blocking**. If you need non-blocking async evaluation, use `IzanamiService` which returns `CompletableFuture<T>`:

```java
// OpenFeature - blocking
boolean enabled = client.getBooleanValue("my-flag", false);

// IzanamiService - non-blocking async
izanamiService.forFlagName("my-flag")
    .booleanValue()
    .thenAccept(enabled -> { /* handle async */ });
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

## Resilience

The starter is designed to be **fail-safe** and will never crash your application due to Izanami connectivity issues or misconfiguration.

### Graceful Degradation

| Scenario | Behavior |
|----------|----------|
| **Missing credentials** (`client-id`/`client-secret`) | Client stays inactive, evaluations return configured defaults |
| **Missing URL** (`base-url`) | Client stays inactive, evaluations return configured defaults |
| **Server unreachable** | Evaluations use Izanami client error strategy (cached value or default) |
| **Flag not configured** | Returns sensible defaults (`false` for boolean, `null` for string/number) with `FLAG_NOT_FOUND` reason |
| **Preload failure** | Application starts normally, evaluations fall back to configured defaults |

### Metadata for Observability

When evaluations fall back to defaults, the metadata indicates the source:

```java
ResultValueWithDetails<Boolean> result = izanamiService.forFlagName("my-flag")
    .booleanValueDetails()
    .join();

String source = result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE);
String reason = result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON);

// source = "IZANAMI_ERROR_STRATEGY" when server error occurred
// source = "APPLICATION_ERROR_STRATEGY" when feature is disabled
// reason = "FLAG_NOT_FOUND" when flag is not configured
// reason = "ERROR" when server error occurred
```

### Startup Behavior

The service initialization is resilient:

- **No exceptions thrown** from Spring lifecycle callbacks (`afterPropertiesSet`, `destroy`)
- **Preload failures are logged** but don't prevent application startup
- **Health indicator** reports `OUT_OF_SERVICE` if preloading failed, allowing Kubernetes probes to detect issues

This design ensures your application remains available even when Izanami is temporarily unreachable.

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
import fr.maif.izanami.spring.service.api.IzanamiService;
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

- Runs unit tests on JDK 21 (bytecode targets Java 17).
- Starts Izanami via Compose, seeds flags, runs integration tests.

## Releasing

Releases are automated via GitHub Actions (`.github/workflows/release.yml`).

### Release Process

1. **Tag and push** (version derived automatically from tag):

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

2. **Workflow automatically**:
   - Sets version from tag via `-Drevision=1.0.0`
   - Builds, tests, and publishes to Maven Central
   - Generates SBOM (CycloneDX)
   - Creates build provenance attestation
   - Creates GitHub Release with JARs, checksums, and SBOM

### Required GitHub Secrets

Configure these in **Settings → Secrets and variables → Actions**:

| Secret | Description |
|--------|-------------|
| `CENTRAL_USERNAME` | Maven Central token username (from central.sonatype.com) |
| `CENTRAL_TOKEN` | Maven Central token password |
| `GPG_PRIVATE_KEY` | Armored GPG private key (`gpg --armor --export-secret-keys <KEY_ID>`) |
| `GPG_PASSPHRASE` | GPG key passphrase |

### Verify Attestation

Users can verify artifact provenance:

```bash
gh attestation verify izanami-spring-boot-starter-1.0.0.jar --owner webskin
```

### CI-Friendly Versioning

This project uses Maven CI-friendly versions (`${revision}` property):

- Default version in `pom.xml`: `0.1.0-SNAPSHOT`
- Override at build time: `mvn -Drevision=1.0.0 package`
- Release workflow automatically sets version from git tag
