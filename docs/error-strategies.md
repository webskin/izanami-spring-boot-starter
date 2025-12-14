# Error Strategies

This document explains how to configure and use error strategies for feature flag evaluation.

## Available Strategies

| Strategy | Description |
|----------|-------------|
| `DEFAULT_VALUE` | Returns the configured `defaultValue` on error (default behavior) |
| `NULL_VALUE` | Returns `null` on error |
| `FAIL` | Signals an error to the OpenFeature SDK |
| `CALLBACK` | Uses a callback function (same as `DEFAULT_VALUE` for application errors) |

## Configuration

```yaml
openfeature:
  flags:
    - name: my-feature
      id: project:my-feature
      value-type: boolean
      error-strategy: FAIL  # or DEFAULT_VALUE, NULL_VALUE, CALLBACK
      default-value: false  # only valid with DEFAULT_VALUE strategy
```

> **Note:** The `defaultValue` property is only valid with `errorStrategy: DEFAULT_VALUE`. Configuring both `defaultValue` and a different error strategy will cause an application startup error.

## FAIL Strategy Usage

OpenFeature by design **never throws exceptions** to application code from `getValue()` methods. When using `errorStrategy: FAIL`:

1. The provider throws a `GeneralError` (an `OpenFeatureError`)
2. The SDK catches it and returns the **caller's default value** with `errorCode=GENERAL`
3. To detect the error, use `getDetails()` instead of `getValue()`

### Example

```java
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.OpenFeatureAPI;

@Service
public class MyService {

    private final Client client;

    public MyService() {
        this.client = OpenFeatureAPI.getInstance().getClient();
    }

    public void doSomething() {
        // This will NOT throw - returns default value even on FAIL
        Boolean value = client.getBooleanValue("my-feature", false);

        // To detect errors, use getDetails():
        FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("my-feature", false);

        if (details.getErrorCode() != null) {
            // Error occurred - FAIL strategy was triggered
            throw new RuntimeException("Flag evaluation failed: " + details.getErrorMessage());
        }

        Boolean safeValue = details.getValue();
        // Use safeValue...
    }
}
```

### Helper Method

You can create a helper method to enforce fail-on-error behavior:

```java
public static <T> T getValueOrThrow(FlagEvaluationDetails<T> details) {
    if (details.getErrorCode() != null) {
        throw new FeatureFlagException(
            "Flag '" + details.getFlagKey() + "' evaluation failed: " + details.getErrorMessage(),
            details.getErrorCode()
        );
    }
    return details.getValue();
}

// Usage:
Boolean value = getValueOrThrow(client.getBooleanDetails("my-feature", false));
```

## NULL_VALUE Strategy

When `errorStrategy: NULL_VALUE` is configured, the provider returns `null` on error:

```java
Boolean value = client.getBooleanValue("my-feature", false);
// value will be null if an error occurred (not the default 'false')

if (value == null) {
    // Handle the error case
}
```

## Izanami vs OpenFeature Design Philosophy

Izanami and OpenFeature have different design philosophies regarding error handling:

| Aspect | Izanami Native | OpenFeature |
|--------|----------------|-------------|
| **FAIL strategy** | Throws exception to caller | Returns default + error details |
| **Error handling** | Application chooses behavior | SDK guarantees no crash |
| **Philosophy** | "Fail fast if configured" | "Never crash on flag evaluation" |

**OpenFeature's rationale:** Feature flags should never crash your application. A flag misconfiguration or network issue shouldn't bring down production. The SDK always returns a value, but provides error information for applications that want to handle it.

**Izanami's FAIL strategy intent:** "This flag is critical - if we can't evaluate it, fail loudly rather than silently use a default."

In the OpenFeature context, the FAIL strategy effectively becomes: "Signal an error that the application can detect and handle" - which requires using `getDetails()` instead of `getValue()`.

### How OpenFeature SDK Handles Errors

When a provider throws an exception or returns an `errorCode`, the OpenFeature SDK **replaces the provider's value with the caller's default**:

```java
// Inside OpenFeature SDK
if (details.getErrorCode() != null) {
    details.setValue(defaultValue);  // Overwrites provider's value!
    details.setReason("ERROR");
}
```

This has different implications for each strategy:

| Strategy | What provider does | errorCode set? | SDK behavior | Works as expected? |
|----------|-------------------|----------------|--------------|-------------------|
| `DEFAULT_VALUE` | Returns configured default | No | Preserves value | Yes |
| `NULL_VALUE` | Returns `null` | No | Preserves `null` | Yes |
| `FAIL` | Throws `GeneralError` | Yes (by SDK) | **Replaces with caller's default** | No - requires `getDetails()` |
| `CALLBACK` | Returns callback result | No | Preserves value | Yes |

**Key insight:**
- **NULL_VALUE works correctly** because we return `null` without setting an `errorCode`
- **FAIL has limitations** because throwing an exception causes the SDK to set `errorCode` and replace the value with the caller's default

### Practical Options

1. **Accept OpenFeature's design** - Use `getDetails()` and check `errorCode` when you need fail-on-error behavior

2. **Create a wrapper client** that throws on error:
   ```java
   public Boolean getBooleanValueOrFail(String key, Boolean defaultValue) {
       var details = client.getBooleanDetails(key, defaultValue);
       if (details.getErrorCode() != null) {
           throw new FeatureFlagException(details.getErrorMessage());
       }
       return details.getValue();
   }
   ```

3. **Use Izanami client directly** (bypass OpenFeature) for flags that need true fail-fast behavior

The FAIL strategy still has value - it prevents the Izanami client from silently falling back to defaults. The difference is where the exception stops: at the OpenFeature SDK boundary rather than propagating to your code.

## References

- [OpenFeature Flag Evaluation API](https://openfeature.dev/specification/sections/flag-evaluation/)
- [OpenFeature Java SDK](https://openfeature.dev/docs/reference/sdks/server/java/)
