# Izanami Spring Boot Starter

Spring Boot auto-configuration for [Izanami](https://maif.github.io/izanami/) feature flags with OpenFeature integration.

## Build & Test

```bash
mvn clean install          # Build all modules
mvn test                   # Run unit tests
mvn verify                 # Run integration tests (requires Izanami server)
```

## Project Structure

```
izanami-spring-boot-starter/
├── izanami-spring-boot-autoconfigure/   # Auto-configuration module
│   └── src/main/java/fr/maif/izanami/spring/
│       ├── autoconfigure/               # Spring Boot auto-config
│       ├── openfeature/                 # OpenFeature provider
│       │   ├── api/                     # Public interfaces
│       │   └── internal/                # Implementation details
│       └── service/                     # Core Izanami service
└── izanami-spring-boot-starter/         # Starter POM (aggregates dependencies)
```

## Architecture

### Core Components

**IzanamiService** (`service/IzanamiService.java`)
- Lifecycle wrapper around `IzanamiClient` (implements `InitializingBean`, `DisposableBean`)
- Resilient: never throws from Spring lifecycle callbacks
- Stays inactive if credentials/URL are missing
- Provides fluent API: `forFlagKey(key)` / `forFlagName(name)` -> `withUser()` -> `withContext()` -> `booleanValue()` etc.
- All evaluation methods return `CompletableFuture<T>` (async)
- Use `unwrapClient()` for direct `IzanamiClient` access (escape hatch)

**IzanamiFeatureProvider** (`openfeature/IzanamiFeatureProvider.java`)
- OpenFeature `FeatureProvider` implementation
- Resolves flags via `openfeature.flags` configuration
- Flag key can be either `FlagConfig.name()` or `FlagConfig.key()`
- Fallback rules:
  - Client not available -> configured default (`APPLICATION_ERROR_STRATEGY`)
  - Izanami error -> Izanami client error strategy (`IZANAMI_ERROR_STRATEGY`)
  - Invalid JSON for object flag -> configured default with `Reason.ERROR`
- Note: Izanami does not return "variant" field; always null in evaluations

**ExtendedOpenFeatureClientImpl** (`openfeature/internal/ExtendedOpenFeatureClientImpl.java`)
- Decorator over OpenFeature `Client`
- Adds convenience methods without explicit default value parameter
- Auto-computes default from `FlagConfig.defaultValue()`
- Validates: flag must exist, have `DEFAULT_VALUE` error strategy, and non-null default

### Data Model

**FlagConfig** (record)
```java
record FlagConfig(
    String key,           // Izanami feature UUID
    String name,          // Human-friendly OpenFeature key
    String description,
    FlagValueType valueType,  // BOOLEAN, STRING, INTEGER, DOUBLE, OBJECT
    ErrorStrategy rawErrorStrategy,
    FeatureClientErrorStrategy<?> errorStrategy,
    Object defaultValue,
    String callbackBean   // For CALLBACK error strategy
)
```

**FlagConfigService** - Lookup by key or name, provides all flag configs for preloading

### Error Strategies

- `DEFAULT_VALUE` - Return configured default (required for ExtendedOpenFeatureClient convenience methods)
- `FAIL` - Throw exception
- `CALLBACK` - Call custom Spring bean implementing `IzanamiErrorCallback`

## Configuration

```yaml
izanami:
  enabled: true              # Default: true
  base-url: http://localhost:9999
  api-path: /api             # Optional, appended to base-url
  client-id: your-client-id
  client-secret: your-client-secret
  cache:
    enabled: true
    refresh-interval: PT60S
    sse:
      enabled: true
      keep-alive-interval: PT25S

openfeature:
  flags:
    - key: "feature-uuid"        # Izanami feature key (UUID)
      name: "my-feature"         # Human-friendly name for OpenFeature
      description: "Feature description"
      valueType: BOOLEAN         # BOOLEAN, STRING, INTEGER, DOUBLE, OBJECT
      errorStrategy: DEFAULT_VALUE
      defaultValue: false
```

## Usage Examples

### Direct IzanamiService (Async)

```java
@Autowired IzanamiService izanamiService;

// Fluent async API
izanamiService.forFlagKey("feature-uuid")
    .withUser("user-123")
    .withContext("production")
    .booleanValue()
    .thenAccept(enabled -> {
        if (enabled) { /* ... */ }
    });

// By name
izanamiService.forFlagName("my-feature")
    .booleanValue()
    .join();  // Blocking
```

### OpenFeature Client

```java
@Autowired Client openFeatureClient;

// Standard OpenFeature API (requires default value)
boolean enabled = openFeatureClient.getBooleanValue("feature-uuid", false);
```

### Extended OpenFeature Client

```java
@Autowired ExtendedOpenFeatureClient client;

// Convenience: default value from config
boolean enabled = client.getBooleanValue("feature-uuid");
FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("feature-uuid");
```

## Code Conventions

- Async-first: Evaluation methods return `CompletableFuture<T>`, not `Optional`
- Records for immutable data (FlagConfig, ResultWithMetadata)
- Package structure: `api/` for public interfaces, `internal/` for implementations
- Resilience: Service stays inactive rather than failing on misconfiguration
