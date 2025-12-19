# Feature: Extended Request Builder Options

## Summary

Added support for advanced `FeatureRequest` options in both `FeatureRequestBuilder` (single flag) and `BatchFeatureRequestBuilder` (batch flags) through a shared `BaseFeatureRequestBuilder` interface.

## Motivation

The underlying Izanami Java client supports several request configuration options that were not exposed through the Spring Boot starter's fluent API. This feature exposes these options to give developers fine-grained control over feature flag evaluation requests.

## Implementation

### New Interface: BaseFeatureRequestBuilder

A new generic interface `BaseFeatureRequestBuilder<T>` serves as the base for both builders:

```java
public interface BaseFeatureRequestBuilder<T extends BaseFeatureRequestBuilder<T>> {
    T withUser(@Nullable String user);
    T withContext(@Nullable String context);
    T ignoreCache(boolean ignoreCache);
    T withCallTimeout(@Nullable Duration timeout);
    T withPayload(@Nullable String payload);
    T withBooleanCastStrategy(BooleanCastStrategy strategy);
    T withErrorStrategy(@Nullable FeatureClientErrorStrategy<?> errorStrategy);
}
```

### Updated Interfaces

- `FeatureRequestBuilder extends BaseFeatureRequestBuilder<FeatureRequestBuilder>` - contains terminal methods only
- `BatchFeatureRequestBuilder extends BaseFeatureRequestBuilder<BatchFeatureRequestBuilder>` - contains `values()` terminal method only

### New Methods Available

| Method | Description | Default |
|--------|-------------|---------|
| `ignoreCache(boolean)` | Bypass cache for fresh value from server | `false` |
| `withCallTimeout(Duration)` | Per-request HTTP timeout | Client default |
| `withPayload(String)` | Extra context sent to server | `null` |
| `withBooleanCastStrategy(BooleanCastStrategy)` | Control casting (STRICT vs LAX) | `LAX` |
| `withErrorStrategy(FeatureClientErrorStrategy<?>)` | Per-request error strategy override | FlagConfig default |

## Usage Examples

### Single Flag Evaluation

```java
// With all new options
izanamiService.forFlagKey("feature-uuid")
    .withUser("user-123")
    .withContext("production")
    .ignoreCache(true)
    .withCallTimeout(Duration.ofSeconds(5))
    .withPayload("{\"tier\": \"premium\"}")
    .withBooleanCastStrategy(BooleanCastStrategy.STRICT)
    .withErrorStrategy(FeatureClientErrorStrategy.failStrategy())
    .booleanValue()
    .thenAccept(enabled -> { ... });
```

### Batch Flag Evaluation

```java
// Batch with per-request error strategy override
izanamiService.forFlagKeys("uuid-1", "uuid-2", "uuid-3")
    .withUser("user-123")
    .ignoreCache(true)
    .withErrorStrategy(FeatureClientErrorStrategy.failStrategy())
    .values()
    .thenAccept(result -> {
        // If FAIL strategy + error occurred, accessing values throws
        Boolean enabled = result.booleanValue("uuid-1");  // May throw
    });
```

## Error Strategy Override Hierarchy

Per-request `withErrorStrategy()` allows exceptional override of FlagConfig:

1. **Per-request override** (if provided via `withErrorStrategy()`)
2. **FlagConfig default** (from `openfeature.flags` configuration)
3. **Application default** (for flags not in configuration)

This is useful when a specific request needs different error handling (e.g., fail-fast for critical operations).

## FAIL Strategy Behavior

### Single Flag Evaluation

When using FAIL strategy with single flag evaluation, exceptions bubble up through the `CompletableFuture`:

```java
try {
    Boolean value = izanamiService.forFlagKey("uuid")
        .withErrorStrategy(FeatureClientErrorStrategy.failStrategy())
        .booleanValue()
        .join();  // Throws CompletionException if error
} catch (CompletionException e) {
    // Handle error
}
```

### Batch Flag Evaluation

For batch evaluation, `values().join()` succeeds even if individual flags have errors. Exceptions are thrown when accessing individual flag values:

```java
BatchResult result = izanamiService.forFlagKeys("uuid-1", "uuid-2")
    .withErrorStrategy(FeatureClientErrorStrategy.failStrategy())
    .values()
    .join();  // Succeeds

// Exception thrown when accessing value for flag with FAIL + error
try {
    Boolean value = result.booleanValue("uuid-1");  // May throw RuntimeException
} catch (RuntimeException e) {
    // Handle individual flag error
}
```

## Files Modified

### New Files
- `api/BaseFeatureRequestBuilder.java` - Shared base interface

### Modified Files
- `api/FeatureRequestBuilder.java` - Extends base interface
- `api/BatchFeatureRequestBuilder.java` - Extends base interface
- `IzanamiServiceImpl.java` - Added new fields and implementations to builders
- `IzanamiFeatureEvaluator.java` - Accepts and uses new options
- `IzanamiBatchFeatureEvaluator.java` - Accepts and uses new options
- `BatchResultImpl.java` - Added FAIL strategy check in accessors

## Backward Compatibility

This change is fully backward compatible:
- All new methods have sensible defaults
- Existing code continues to work without modification
- Default behaviors match previous implementation (LAX casting, FlagConfig error strategy)
