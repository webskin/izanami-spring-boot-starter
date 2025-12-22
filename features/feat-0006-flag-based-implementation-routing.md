# Feature 0006: Flag-Based Implementation Routing (README Documentation)

## Summary

Document a common usage pattern for routing between different service implementations based on feature flag values. This pattern enables gradual rollouts, A/B testing, and safe migrations between service versions.

## README Section to Add

Add the following section to `README.md` under a "Usage Patterns" or "Recipes" heading:

---

### Flag-Based Implementation Routing

A common pattern when migrating between service versions or running A/B tests is to route requests to different implementations based on a feature flag. This allows gradual rollouts and instant rollback capability.

#### Pattern Overview

```java
@Service
public class FlaggedPricingEngine implements PricingEngine {

    private final PricingEngine v1;
    private final PricingEngine v2;
    private final IzanamiService izanami;

    public FlaggedPricingEngine(
            PricingEngineV1 v1,
            PricingEngineV2 v2,
            IzanamiService izanami
    ) {
        this.v1 = v1;
        this.v2 = v2;
        this.izanami = izanami;
    }

    @Override
    public BigDecimal price(Order order) {
        boolean useV2 = izanami.forFlagKey("pricing-v2-enabled")
                .withUser(order.getCustomerId())
                .booleanValue()
                .join();

        return useV2 ? v2.price(order) : v1.price(order);
    }
}
```

#### Key Benefits

- **Gradual Rollout**: Enable the new implementation for a percentage of users or specific user segments
- **Instant Rollback**: Disable the flag to immediately revert to the previous implementation
- **A/B Testing**: Route traffic between implementations to compare performance or behavior
- **Risk Mitigation**: Test new code paths in production with limited exposure

#### Configuration

Configure the Izanami connection in your `application.yml`:

```yaml
izanami:
  enabled: true
  base-url: http://localhost:9999
  client-id: your-client-id
  client-secret: your-client-secret
  cache:
    enabled: true
    refresh-interval: PT60S
```

#### Using Flag by Name

If you prefer human-readable flag names, use `forFlagName()`:

```java
@Override
public BigDecimal price(Order order) {
    boolean useV2 = izanami.forFlagName("pricing.v2")
            .withUser(order.getCustomerId())
            .withContext("production")
            .booleanValue()
            .join();

    return useV2 ? v2.price(order) : v1.price(order);
}
```

#### Async Evaluation

For non-blocking evaluation in reactive contexts:

```java
@Override
public CompletableFuture<BigDecimal> priceAsync(Order order) {
    return izanami.forFlagKey("pricing-v2-enabled")
            .withUser(order.getCustomerId())
            .booleanValue()
            .thenApply(useV2 -> useV2 ? v2.price(order) : v1.price(order));
}
```

#### Best Practices

1. **Safe Defaults**: Configure your Izanami client error strategy to return a safe default (e.g., `false` for the old implementation) if Izanami is unavailable

2. **User Targeting**: Pass user context via `withUser()` to enable percentage rollouts or user segment targeting in Izanami

3. **Context Support**: Use `withContext()` to pass environment or additional context for more granular targeting

4. **Logging**: Consider logging which implementation was used for debugging:
   ```java
   log.debug("Using pricing engine {} for order {}", useV2 ? "V2" : "V1", order.getId());
   ```

5. **Metrics**: Track usage of each implementation to monitor rollout progress:
   ```java
   meterRegistry.counter("pricing.engine.used", "version", useV2 ? "v2" : "v1").increment();
   ```

6. **Cleanup**: Remove the routing code and flag once migration is complete to avoid technical debt

---

## Implementation Notes

This is a documentation-only feature. No code changes required.

## References

- Izanami documentation: https://maif.github.io/izanami/
