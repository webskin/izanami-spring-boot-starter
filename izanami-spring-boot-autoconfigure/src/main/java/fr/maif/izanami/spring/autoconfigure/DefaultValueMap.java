package fr.maif.izanami.spring.autoconfigure;

import java.util.LinkedHashMap;

/**
 * Specialized map type used for binding {@code openfeature.flags[*].defaultValue}.
 * <p>
 * This wrapper type avoids bean collision with generic {@code Converter<*, Map<String, Object>>}
 * beans that may be registered by other libraries.
 */
public final class DefaultValueMap extends LinkedHashMap<String, Object> {
}
