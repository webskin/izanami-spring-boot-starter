# Feature 0001: Batch Feature Values API for IzanamiService

## Summary

Add support for batch feature flag evaluation to `IzanamiService` by exposing the underlying `IzanamiClient.featureValues()` capability through a Spring-friendly fluent API.

## Background

### Current State

`IzanamiService` currently supports only single-flag evaluation via:
- `forFlagKey(key)` / `forFlagName(name)` -> builder -> `booleanValue()` / `stringValue()` / `numberValue()`

For batch evaluation, users must use the escape hatch:
```java
izanamiService.unwrapClient().ifPresent(client -> {
    client.featureValues(FeatureRequest.newFeatureRequest()
            .withFeatures("flag-1", "flag-2", "flag-3"))
        .thenAccept(results -> { ... });
});
```

This is verbose, requires null-checking, and bypasses the Spring configuration layer.

### Client API Reference

From `../izanami-java-client`, the `IzanamiClient.featureValues()` method:

```java
public CompletableFuture<IzanamiResult> featureValues(FeatureRequest request)
```

**FeatureRequest** supports:
- `.withFeatures(String... features)` - Feature IDs (UUIDs)
- `.withUser(String user)` - User targeting
- `.withContext(String context)` - Evaluation context
- `.withPayload(String payload)` - Additional payload
- `.withErrorStrategy(FeatureClientErrorStrategy<?> strategy)` - Error handling
- `.ignoreCache(boolean)` - Bypass cache
- `.withBooleanCastStrategy(BooleanCastStrategy)` - LAX or STRICT

**IzanamiResult** provides:
- `booleanValue(String feature)` - Get boolean for a feature
- `stringValue(String feature)` - Get string for a feature
- `numberValue(String feature)` - Get BigDecimal for a feature
- Internal `Map<String, Result>` with success/error per feature

## Requirements

### Functional Requirements

1. **Batch evaluation by keys**: Evaluate multiple flags by their Izanami UUIDs in a single request
2. **Batch evaluation by names**: Evaluate multiple flags by their OpenFeature names in a single request
3. **User targeting**: Support user ID for all flags in the batch
4. **Context support**: Support evaluation context for all flags in the batch
5. **Consistent return type**: Return results in a Spring-friendly wrapper that:
   - Maps flag keys/names to their values
   - Includes evaluation details (reason, error info) when needed
   - Handles missing flags gracefully

### Non-Functional Requirements

1. **Async-first**: Return `CompletableFuture<T>` like existing API
2. **Resilience**: Handle client unavailability gracefully
3. **Minimal API surface**: Keep the API simple and discoverable

## Proposed API Design

### Fluent Builder Pattern (Recommended)

```java
// By keys (UUIDs)
izanamiService.forFlagKeys("uuid-1", "uuid-2", "uuid-3")
    .withUser("user-123")
    .withContext("production")
    .values()  // CompletableFuture<BatchResult>
    .thenAccept(result -> {
        Boolean enabled = result.booleanValue("uuid-1");
        String value = result.stringValue("uuid-2");
    });

// By names
izanamiService.forFlagNames("feature-a", "feature-b")
    .withUser("user-123")
    .values()
    .thenAccept(result -> {
        Boolean enabled = result.booleanValue("feature-a");
    });
```

## Result Type Design

```java
public interface BatchResult {
    // Get value by flag key or name (depending on how request was built)
    Boolean booleanValue(String flagKeyOrName);
    String stringValue(String flagKeyOrName);
    BigDecimal numberValue(String flagKeyOrName);

    // With details
    ResultValueWithDetails<Boolean> booleanValueDetails(String flagKeyOrName);
    ResultValueWithDetails<String> stringValueDetails(String flagKeyOrName);
    ResultValueWithDetails<BigDecimal> numberValueDetails(String flagKeyOrName);

    // Iteration
    Set<String> flagIdentifiers();  // All flag keys/names in result
    boolean hasFlag(String flagKeyOrName);
}
```

## Implementation Notes

### Key Considerations

1. **Name-to-Key Resolution**: When using `forFlagNames()`, resolve names to Izanami UUIDs via `FlagConfigService` before calling client
2. **Result Mapping**: Map client's `IzanamiResult` back to names if request was by name
3. **Error Handling**:
   - If client is null (not configured), return empty/default results
   - Per-flag errors should be captured in result details
   - **Application error strategy must be applied when a feature is disabled** (not just on errors)
4. **FlagConfig Integration**: Use configured error strategies per flag when available

### Inspiration Code

The following pattern demonstrates how to use `SpecificFeatureRequest` for per-feature error strategies in a bulk query:

```java
// Build SpecificFeatureRequests with per-feature error strategies
Set<SpecificFeatureRequest> specificRequests = buildSpecificFeatureRequests();

// Build request with all features and their error strategies
fr.maif.requests.FeatureRequest featureRequest = fr.maif.requests.FeatureRequest
        .newFeatureRequest()
        .withSpecificFeatures(specificRequests);

featureRequest = addContextToRequest(featureRequest, context);

// Single bulk call to Izanami
log.debug("Making bulk Izanami query for {} features", specificRequests.size());
IzanamiResult izanamiResult = izanamiService.featureValues(featureRequest).get();

if (izanamiResult == null || izanamiResult.results == null) {
    log.warn("Izanami returned null result, using application defaults");
    return buildAllDefaultFeatures();
}

// Build features using functional approach
List<Feature<?>> features = featureConfigs.entrySet().stream()
        .map(entry -> buildFeatureFromConfig(entry, izanamiResult.results))
        .collect(Collectors.toList());

log.debug("Successfully built {} features from bulk query", features.size());
return features;
```

Key points from this pattern:
- Use `SpecificFeatureRequest` to attach per-feature error strategies
- Use `withSpecificFeatures()` instead of `withFeatures()` to preserve per-feature configuration
- Handle null results gracefully by falling back to application defaults
- Map results back to domain objects using the original configuration

### Files to Modify

1. `IzanamiService.java` (interface) - Add new method signatures
2. `IzanamiServiceImpl.java` - Implement batch evaluation logic
3. New: `BatchFeatureRequestBuilder.java` - Fluent builder for batch requests
4. New: `BatchResult.java` - Result wrapper interface
5. New: `BatchResultImpl.java` - Implementation

### Testing

1. Unit tests with mocked `IzanamiClient`
2. Integration tests against real Izanami server (existing IT pattern)
3. Test cases:
   - Multiple flags, all succeed
   - Some flags missing
   - Client not configured (graceful degradation)
   - Mixed value types in batch

## Open Questions

1. Should batch API support mixed key/name requests? (Recommendation: No, keep it simple)
2. Should we expose `ignoreCache` option? (Recommendation: Yes, as optional builder method)
3. Should we support per-flag error strategies or use a single batch-level strategy? (Recommendation: Per-flag from FlagConfig when available)

## References

- `../izanami-java-client/src/main/java/fr/maif/IzanamiClient.java:131` - `featureValues()` method
- `../izanami-java-client/src/main/java/fr/maif/requests/FeatureRequest.java` - Request builder
- `../izanami-java-client/src/main/java/fr/maif/features/values/IzanamiResult.java` - Result type
- Current single-flag API: `IzanamiServiceImpl.java:251-339` - `FeatureRequestBuilder`
