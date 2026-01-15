# Programmatic Feature Flag Configuration

This guide covers advanced configuration patterns for defining feature flags programmatically and merging them with YAML-based configuration.

## Overview

The starter supports three ways to configure feature flags:

1. **YAML configuration** - Define flags under `openfeature.flags` prefix
2. **Programmatic configuration** - Use `@OpenFeatureFlags` annotated beans
3. **Mixed** - Combine both approaches with automatic merging

## Basic YAML Configuration

```yaml
openfeature:
  flags:
    my-feature:
      key: "abc-123-uuid"
      valueType: BOOLEAN
      errorStrategy: DEFAULT_VALUE
      defaultValue: false
```

## Programmatic Configuration

### Single Module

Define a bean annotated with `@OpenFeatureFlags`:

```java
@Configuration
public class FeatureFlagConfig {

    @Bean
    @OpenFeatureFlags
    public FlagsProperties featureFlags() {
        return FlagsProperties.builder()
            .flag("my-feature", flag -> flag
                .key("abc-123-uuid")
                .valueType(FlagValueType.BOOLEAN)
                .errorStrategy(ErrorStrategy.DEFAULT_VALUE)
                .defaultValue(false))
            .flag("another-feature", flag -> flag
                .key("def-456-uuid")
                .valueType(FlagValueType.STRING)
                .defaultValue("default-value"))
            .build();
    }
}
```

### Multi-Module Projects

Each module can define its own `@OpenFeatureFlags` bean. All beans are automatically discovered and merged:

**Module A:**
```java
@Bean
@OpenFeatureFlags
public FlagsProperties moduleAFlags() {
    return FlagsProperties.builder()
        .flag("module-a-feature", flag -> flag
            .key("module-a-uuid")
            .valueType(FlagValueType.BOOLEAN)
            .defaultValue(true))
        .build();
}
```

**Module B:**
```java
@Bean
@OpenFeatureFlags
public FlagsProperties moduleBFlags() {
    return FlagsProperties.builder()
        .flag("module-b-feature", flag -> flag
            .key("module-b-uuid")
            .valueType(FlagValueType.INTEGER)
            .defaultValue(42))
        .build();
}
```

Both modules' flags are merged into a single configuration at runtime.

### Using Environment Variables

Programmatic configuration doesn't automatically resolve Spring property placeholders like `${VAR:default}`. Use `@Value` or `Environment` to inject externalized values:

**Using `@Value`:**
```java
@Configuration
public class FeatureFlagConfig {

    @Value("${FLAG_NEW_DASHBOARD_ID:0c1774d1-9a26-4284-b8a6-0179eb7cf2f7}")
    private String newDashboardId;

    @Bean
    @OpenFeatureFlags
    public FlagsProperties featureFlags() {
        return FlagsProperties.builder()
            .flag("new-dashboard", flag -> flag
                .key(newDashboardId)
                .valueType(FlagValueType.BOOLEAN)
                .defaultValue(false))
            .build();
    }
}
```

**Using `Environment`:**
```java
@Bean
@OpenFeatureFlags
public FlagsProperties featureFlags(Environment env) {
    return FlagsProperties.builder()
        .flag("new-dashboard", flag -> flag
            .key(env.getProperty("FLAG_NEW_DASHBOARD_ID", "0c1774d1-9a26-4284-b8a6-0179eb7cf2f7"))
            .valueType(FlagValueType.BOOLEAN)
            .defaultValue(false))
        .build();
}
```

## Merging Behavior

### Merge Order

1. YAML-configured flags are loaded first
2. Programmatic `@OpenFeatureFlags` beans are merged on top
3. If the same flag name exists in both, **programmatic wins**

### Example: Override YAML with Programmatic

```yaml
# application.yml
openfeature:
  flags:
    shared-flag:
      key: "yaml-key"
      defaultValue: false
```

```java
@Bean
@OpenFeatureFlags
public FlagsProperties overrideFlags() {
    return FlagsProperties.builder()
        .flag("shared-flag", flag -> flag
            .key("programmatic-key")  // Overrides YAML
            .defaultValue(true))      // Overrides YAML
        .build();
}
```

Result: `shared-flag` uses `programmatic-key` with default `true`.

## Custom YAML Prefix

To use a different YAML prefix (e.g., `myapp.features` instead of `openfeature`), define a bean named `flagsProperties`:

```java
@Bean
@ConfigurationProperties(prefix = "myapp.features")
public FlagsProperties flagsProperties() {
    return new FlagsProperties();
}
```

```yaml
# application.yml
myapp:
  features:
    flags:
      my-feature:
        key: "uuid"
        valueType: BOOLEAN
```

This custom YAML source is still merged with any `@OpenFeatureFlags` beans.

## Custom Izanami Prefix

Similarly, to use a custom prefix for Izanami connection properties:

```java
@Bean
@ConfigurationProperties(prefix = "myapp.izanami")
public IzanamiProperties izanamiProperties() {
    return new IzanamiProperties();
}
```

```yaml
myapp:
  izanami:
    base-url: http://izanami:9000
    client-id: my-client
    client-secret: my-secret
```

## FlagConfig Builder API

The `FlagConfigBuilder` provides a fluent API:

```java
FlagsProperties.builder()
    .flag("feature-name", flag -> flag
        .key("izanami-uuid")              // Required: Izanami feature key
        .description("Feature description")
        .valueType(FlagValueType.BOOLEAN) // BOOLEAN, STRING, INTEGER, DOUBLE, OBJECT
        .errorStrategy(ErrorStrategy.DEFAULT_VALUE) // DEFAULT_VALUE, FAIL, CALLBACK
        .defaultValue(false)              // Default when error strategy is 
    .build();
```

### Value Types

| Type | Java Type | Example Default |
|------|-----------|-----------------|
| `BOOLEAN` | `Boolean` | `true`, `false` |
| `STRING` | `String` | `"value"` |
| `INTEGER` | `Integer` | `42` |
| `DOUBLE` | `Double` | `3.14` |
| `OBJECT` | `Map<String, Object>` | `Map.of("key", "value")` |

### Error Strategies

| Strategy | Behavior |
|----------|----------|
| `DEFAULT_VALUE` | Return configured default value |
| `FAIL` | Throw exception |
| `CALLBACK` | Call custom `IzanamiErrorCallback` bean |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    mergedFlagsProperties                    │
│                        (@Primary)                           │
├─────────────────────────────────────────────────────────────┤
│                           │                                 │
│         ┌─────────────────┴─────────────────┐               │
│         │                                   │               │
│         ▼                                   ▼               │
│  flagsProperties                   @OpenFeatureFlags        │
│  (YAML-bound)                      beans (collected)        │
│                                                             │
│  openfeature.flags.*               Multiple modules can     │
│  (or custom prefix)                contribute flags         │
└─────────────────────────────────────────────────────────────┘
```

## Best Practices

1. **Use programmatic for shared libraries** - Define flags in reusable modules
2. **Use YAML for environment-specific overrides** - Override defaults per environment
3. **Consistent naming** - Use the same flag names across YAML and programmatic config
4. **Document flag purposes** - Use the `description` field for documentation
