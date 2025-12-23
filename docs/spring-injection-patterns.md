# Spring Injection Patterns with Feature Flags

This document explores patterns and anti-patterns for flag-driven dependency injection in Spring applications using Izanami.

## Key Insight

**Spring DI is static (startup-time), but feature flags are dynamic (runtime)**. The routing/proxy pattern bridges this gap by injecting all variants and selecting at invocation time.

---

## Viable Patterns

### 1. Runtime Routing (Recommended)

Inject all implementations, route at runtime based on flag value:

```java
@Service
public class FlaggedService implements MyService {
    private final MyService v1;
    private final MyService v2;
    private final IzanamiService izanami;

    public FlaggedService(MyServiceV1 v1, MyServiceV2 v2, IzanamiService izanami) {
        this.v1 = v1;
        this.v2 = v2;
        this.izanami = izanami;
    }

    @Override
    public Result execute() {
        boolean useV2 = izanami.forFlagKey("feature").booleanValue().join();
        return useV2 ? v2.execute() : v1.execute();
    }
}
```

| Pros | Cons |
|------|------|
| Runtime toggle, no restart needed | All implementations instantiated |
| Fully testable | Slightly more memory usage |
| Type-safe | |

### 2. Strategy Map with String Flag

Use a string flag value to select from a map of named strategies:

```java
@Service
public class StrategyRouter {
    private final Map<String, PricingStrategy> strategies;
    private final IzanamiService izanami;

    public StrategyRouter(List<PricingStrategy> strategies, IzanamiService izanami) {
        this.strategies = strategies.stream()
            .collect(Collectors.toMap(PricingStrategy::getName, Function.identity()));
        this.izanami = izanami;
    }

    public BigDecimal price(Order order) {
        String strategyName = izanami.forFlagKey("pricing.strategy")
            .stringValue().join();
        return strategies.getOrDefault(strategyName, strategies.get("default"))
            .price(order);
    }
}
```

| Pros | Cons |
|------|------|
| Extensible without code changes | Must validate flag values |
| Supports multiple variants | Risk of misconfiguration |
| Easy to add new strategies | |

### 3. Number-based Variant Selection

Use a numeric flag to select from an ordered list of implementations (useful for A/B testing):

```java
@Service
public class ABTestRouter {
    private final List<PricingEngine> variants; // ordered: v1, v2, v3...
    private final IzanamiService izanami;

    public ABTestRouter(List<PricingEngine> variants, IzanamiService izanami) {
        this.variants = variants;
        this.izanami = izanami;
    }

    public BigDecimal price(Order order) {
        int variantIndex = izanami.forFlagKey("pricing.variant")
            .numberValue().join().intValue();
        int safeIndex = Math.max(0, Math.min(variantIndex, variants.size() - 1));
        return variants.get(safeIndex).price(order);
    }
}
```

| Pros | Cons |
|------|------|
| Simple variant selection | Index must be bounded |
| Good for A/B/n testing | Less readable than named strategies |

---

## Anti-Patterns

### 1. Startup-Time Bean Selection

Evaluating the flag at bean creation time defeats the purpose of feature flags:

```java
// DON'T DO THIS
@Configuration
public class PricingConfig {
    @Bean
    public PricingEngine pricingEngine(
            PricingEngineV1 v1,
            PricingEngineV2 v2,
            IzanamiService izanami) {
        // Flag evaluated ONCE at startup - defeats the purpose!
        boolean useV2 = izanami.forFlagKey("pricing.v2").booleanValue().join();
        return useV2 ? v2 : v1;
    }
}
```

**Why it's bad:**
- Requires application restart to change behavior
- Loses runtime toggle capability
- Negates the value of feature flags

### 2. Dynamic Bean Lookup by Flag Value

Using flag value directly as a bean name is a security and stability risk:

```java
// DON'T DO THIS - Security risk!
@Service
public class DangerousRouter {
    @Autowired ApplicationContext context;
    @Autowired IzanamiService izanami;

    public Object execute() {
        String beanName = izanami.forFlagKey("bean.name").stringValue().join();
        return context.getBean(beanName); // Arbitrary bean access!
    }
}
```

**Why it's bad:**
- Flag misconfiguration could reference any bean (security risk)
- `NoSuchBeanDefinitionException` at runtime
- Bypasses compile-time type safety
- Unpredictable behavior if flag value is changed unexpectedly

### 3. Constructor Injection with Flag

Evaluating flag in constructor only runs once for singleton beans:

```java
// DON'T DO THIS - Misleading behavior
@Service
public class BadService {
    private final MyService delegate;

    // This doesn't work as intended - evaluated at construction time only
    public BadService(MyService v1, MyService v2, IzanamiService izanami) {
        this.delegate = izanami.forFlagKey("use.v2").booleanValue().join() ? v2 : v1;
    }

    public Result execute() {
        return delegate.execute(); // Always uses the same delegate!
    }
}
```

**Why it's bad:**
- Singleton scope means constructor runs once at startup
- Flag changes have no effect until restart
- Misleading code that appears dynamic but isn't

### 4. @ConditionalOnProperty for Feature Flags

Spring's conditional annotations are for configuration, not feature flags:

```java
// DON'T DO THIS for feature flags
@Configuration
public class ConditionalConfig {
    @Bean
    @ConditionalOnProperty(name = "feature.v2.enabled", havingValue = "true")
    public PricingEngine pricingEngineV2() {
        return new PricingEngineV2();
    }

    @Bean
    @ConditionalOnProperty(name = "feature.v2.enabled", havingValue = "false", matchIfMissing = true)
    public PricingEngine pricingEngineV1() {
        return new PricingEngineV1();
    }
}
```

**Why it's bad:**
- Property evaluated at startup only
- Requires restart to change
- Not connected to Izanami (uses Spring properties)
- Appropriate for environment config, not feature flags

---

## Summary

| Pattern | Runtime Toggle | Type Safe | Recommended |
|---------|----------------|-----------|-------------|
| Runtime Routing (Proxy) | Yes | Yes | **Yes** |
| Strategy Map + String | Yes | Partial | Yes (with validation) |
| Number-based Variant | Yes | Partial | Yes (for A/B) |
| Startup Bean Selection | No | Yes | **No** |
| Dynamic `getBean()` | Yes | No | **No** |
| `@ConditionalOnProperty` | No | Yes | **No** (for flags) |

## Best Practices

1. **Always evaluate flags at invocation time**, not at bean creation time
2. **Inject all implementations** and route dynamically
3. **Validate string flag values** against a known set of valid options
4. **Use safe defaults** when flag values are unexpected
5. **Keep flag evaluation close to the decision point** for clarity
6. **Consider lazy initialization** if some implementations are expensive to create
