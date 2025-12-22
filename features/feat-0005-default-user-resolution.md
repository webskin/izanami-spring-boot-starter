# Feature: Default User Resolution (WIP)

## Status

WIP plan.

## Summary

Define how to resolve the `withUser(...)` value when the client does not set it explicitly. Provide an optional `UserProvider` interface with a composite resolver that can pull a user request-scoped beans, or custom implementations, without hard-coupling core APIs to web frameworks.

## Goals

- Provide a clean, testable way to supply a default user when `withUser(...)` is not set.
- Keep the core API framework-agnostic (no servlet/reactive types in the core interfaces).
- Ensure compatibility with OpenFeature `ImmutableContext`.
- Preserve existing behavior when callers set a user explicitly.

## Non-Goals

- Defining authentication or authorization strategies.
- Making user resolution mandatory.

## Proposed API

```java
public interface UserProvider {
    Optional<String> user();
}
```

## Composite Resolver (Resolution Order)

1. Explicit `withUser(user)` set by the caller.
3. `UserProvider` (if present).
4. Fallback behavior (see Resilience).

## Spring Wiring

- `UserProvider` is an optional bean.
- Use `ObjectProvider` to avoid failures when request scope is unavailable.
- Provide an MVC-only optional `@RequestScope` `UserProvider` that can read request or security data if desired.

## Resilience (No User Provided)

- If no user is resolved, do not call `withUser(...)`.

## Implementation Plan

1. Add `UserProvider` in an API package (TBD).
2. Add a composite user resolver that applies the resolution order above.
3. Wire `IzanamiService` / builders to use the composite when `withUser(...)` is not set.
4. Add unit tests and integration tests for resolution order
5. Update documentation with property usage and examples (see below).

## Documentation Updates

- Include a `UserProvider` example.
- Include a request-scoped `UserProvider` example that resolves the authenticated user.

### Example: UserProvider

```java
@Component
public class StaticUserProvider implements UserProvider {
    @Override
    public Optional<String> user() {
        return Optional.of("system-user");
    }
}
```

### Example: Request-Scoped UserProvider (Spring Security)

```java
@Component
@RequestScope
public class SecurityContextUserProvider implements UserProvider {
    @Override
    public Optional<String> user() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.ofNullable(authentication.getName());
    }
}
```
