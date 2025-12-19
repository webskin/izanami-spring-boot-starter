# Feature: Graceful FlagNotFoundException Handling

## Problem

Currently, when a flag key or name is not found in the `openfeature.flags` configuration, `IzanamiService` methods throw `FlagNotFoundException` immediately:

```java
// IzanamiServiceImpl.java
public FeatureRequestBuilder forFlagKey(String flagKey) {
    FlagConfig flagConfig = flagConfigService
        .getFlagConfigByKey(flagKey)
        .orElseThrow(() -> new FlagNotFoundException(flagKey, FlagNotFoundException.IdentifierType.KEY));
    return new FeatureRequestBuilder(this, flagConfig);
}
```

This behavior can crash the application if the caller doesn't handle the exception, making the service less resilient.

## Proposed Solution

Instead of throwing `FlagNotFoundException`, return default values using the default error strategy:

- Boolean: `false`
- String: `""`
- Number: `BigDecimal.ZERO`

The result should include metadata indicating:
- `FLAG_VALUE_SOURCE`: `APPLICATION_ERROR_STRATEGY`
- `FLAG_EVALUATION_REASON`: `FLAG_NOT_FOUND`

## Affected Methods

- `forFlagKey(String flagKey)`
- `forFlagName(String flagName)`
- `forFlagKeys(String... flagKeys)`
- `forFlagNames(String... flagNames)`

## Implementation Notes

1. **Need `getDefaultErrorStrategy()` or similar** in `IzanamiServiceImpl` to access the default values:
   ```java
   FeatureClientErrorStrategy.DefaultValueStrategy defaultErrorStrategy =
       FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO);
   ```

2. **Single flag methods** (`forFlagKey`, `forFlagName`): Return a builder that produces defaults when evaluated, without throwing.

3. **Batch methods** (`forFlagKeys`, `forFlagNames`): Include missing flags in the result with default values, don't throw for any missing flag.

4. **Metadata** should indicate the flag was not found so callers can detect this case if needed.

## Pay Attention: OpenFeature Side

The OpenFeature provider (`IzanamiFeatureProvider.java`) already handles missing flags gracefully using `ErrorCode.FLAG_NOT_FOUND`:

```java
// IzanamiFeatureProvider.java:267-274
protected ProviderEvaluation<T> flagNotFound() {
    return ProviderEvaluation.<T>builder()
        .value(getFlagNotFoundValue())  // Returns caller's default value
        .errorCode(ErrorCode.FLAG_NOT_FOUND)
        .errorMessage("Feature flag '" + flagKey + "' not found in openfeature.flags")
        .flagMetadata(flagNotFoundMetadata())  // Uses APPLICATION_ERROR_STRATEGY source
        .build();
}
```

**Key difference**:
- **OpenFeature API**: Does NOT throw, returns `ErrorCode.FLAG_NOT_FOUND` with caller-provided default
- **IzanamiService API**: Currently THROWS `FlagNotFoundException`

The implementation should align `IzanamiService` behavior with the OpenFeature pattern.

## API Impact

This is a **breaking change** for code that catches `FlagNotFoundException`. However, it improves resilience:

**Before:**
```java
try {
    boolean enabled = izanamiService.forFlagKey("unknown-flag").booleanValue().join();
} catch (FlagNotFoundException e) {
    enabled = false; // Fallback
}
```

**After:**
```java
boolean enabled = izanamiService.forFlagKey("unknown-flag").booleanValue().join();
// Returns false (default) without throwing
```

Callers needing to detect missing flags can check the metadata:
```java
ResultValueWithDetails<Boolean> result = izanamiService.forFlagKey("unknown-flag")
    .booleanValueDetails().join();
if ("FLAG_NOT_FOUND".equals(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))) {
    // Handle missing flag case
}
```
