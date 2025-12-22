# Feature: Default Root/Sub Context Resolution (WIP)

## Status

WIP plan.

## Summary

Introduce optional `RootContextProvider` and `SubContextResolver` interfaces with a composite resolver that can build a full context path (e.g., `BUILD/mobile`) when callers do not provide `withContext(...)` explicitly. Provide a default property-based root context under the izanami config key (`izanami.root-context`) that can be overridden by a custom bean.

## Goals

- Provide a clean, testable way to supply default root/sub context values.
- Keep the core API framework-agnostic (no servlet/reactive types in the core interfaces).
- Preserve existing behavior when callers set a full context explicitly.
- Allow per-request sub-context resolution in MVC without forcing it on all apps.

## Non-Goals

- Mandating any specific user identity strategy.
- Mixing reactive context resolution into the core interfaces.

## Proposed API

```java
public interface RootContextProvider {
    Optional<String> root();
}

public interface SubContextResolver {
    Optional<String> subContext();
}
```

### Default Property Provider

If no `RootContextProvider` bean is present, register a default provider that reads under the izanami config key:

```
izanami.root-context=BUILD
```

### Composite Resolver (Resolution Order)

1. Explicit `withContext(fullPath)` set by the caller.
2. `SubContextResolver` (if present), combined with root from `RootContextProvider`.
3. `RootContextProvider` (if present or from property).
4. Fallback behavior (see Resilience).

Note: If both root and sub are present, join with `/` and normalize slashes (see Normalization Rules).

## Spring Wiring

- `RootContextProvider` and `SubContextResolver` are optional beans.
- Use `ObjectProvider` to avoid failures when request scope is unavailable.
- Provide an MVC-only optional `@RequestScope` `SubContextResolver` that can read request data if desired.

## OpenFeature Context Compatibility

- Must be compatible with OpenFeature `ImmutableContext` attributes.
- Manual user context is encoded in the attributes map under the `context` key.

## Resilience (No Root Provided)

- **Allow sub-only**: If only `subContext` exists, treat it as full context and always log a warning.

## Implementation Plan

1. Add interfaces in an API package (TBD).
2. Add auto-configured composite resolver with the resolution order above.
3. Add property-based `RootContextProvider` (only if no custom bean).
4. Wire `IzanamiService` / builders to use the composite when `withContext(...)` is not set.
   - If the user does not provide a context, `withContext(...)` must not be called when the composite returns empty.
5. Add unit tests and integration tests for resolution order, missing root behavior, and logging.
6. Update documentation with property usage and examples (see below).

## Documentation Updates

- Document `izanami.root-context` and precedence vs. custom beans.
- Document `ImmutableContext` compatibility and the `context` attribute mapping.
- Include a `RootContextProvider` example.
- Include a request-scoped `SubContextResolver` example that resolves a `mobile` sub-context.

### Example: RootContextProvider

```java
@Component
public class BuildRootContextProvider implements RootContextProvider {
    @Override
    public Optional<String> root() {
        return Optional.of("BUILD");
    }
}
```

### Example: Request-Scoped SubContextResolver

```java
@Component
@RequestScope
public class MobileSubContextResolver implements SubContextResolver {
    private final HttpServletRequest request;

    public MobileSubContextResolver(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public Optional<String> subContext() {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.contains("Mobi")) {
            return Optional.of("mobile");
        }
        return Optional.empty();
    }
}
```

## Normalization Rules

- Trim whitespace.
- Remove leading and trailing slashes from root and sub.
- Collapse multiple adjacent slashes to a single slash.
