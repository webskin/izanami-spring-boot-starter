package fr.maif.izanami.spring.openfeature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Java objects to OpenFeature {@link Value} instances.
 * <p>
 * This converter handles:
 * <ul>
 *   <li>Primitives: String, Boolean, Integer, Double, Number</li>
 *   <li>Collections: List, Map</li>
 *   <li>OpenFeature types: Value, Structure</li>
 *   <li>Temporal types: Instant</li>
 *   <li>JSON strings (parsed via ObjectMapper)</li>
 * </ul>
 */
public final class ValueConverter {
    private static final Logger log = LoggerFactory.getLogger(ValueConverter.class);

    /**
     * Convert a Java object to an OpenFeature {@link Value}.
     * <p>
     * Supported types:
     * <ul>
     *   <li>{@link Value} - returned as-is</li>
     *   <li>{@code null} - returns empty Value</li>
     *   <li>{@link String}, {@link Boolean}, {@link Integer}, {@link Double} - wrapped in Value</li>
     *   <li>{@link Number} - converted to double</li>
     *   <li>{@link Structure} - wrapped in Value</li>
     *   <li>{@link List} - recursively converted to List of Values</li>
     *   <li>{@link Instant} - wrapped in Value</li>
     *   <li>{@link Map} - converted to MutableStructure</li>
     * </ul>
     *
     * @param object the object to convert
     * @return the converted Value
     * @throws TypeMismatchError if the object type is not supported
     */
    public Value objectToValue(Object object) {
        if (object instanceof Value v) {
            return v;
        }
        if (object == null) {
            return new Value();
        }
        if (object instanceof String s) {
            return new Value(s);
        }
        if (object instanceof Boolean b) {
            return new Value(b);
        }
        if (object instanceof Integer i) {
            return new Value(i);
        }
        if (object instanceof Double d) {
            return new Value(d);
        }
        if (object instanceof Number n) {
            return new Value(n.doubleValue());
        }
        if (object instanceof Structure s) {
            return new Value(s);
        }
        if (object instanceof List<?> list) {
            List<Value> values = list.stream().map(this::objectToValue).toList();
            return new Value(values);
        }
        if (object instanceof Instant instant) {
            return new Value(instant);
        }
        if (object instanceof Map<?, ?> map) {
            Map<String, Value> attributes = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                attributes.put(entry.getKey().toString(), objectToValue(entry.getValue()));
            }
            return new Value(new MutableStructure(attributes));
        }
        throw new TypeMismatchError(
            "Flag value '" + object + "' had unexpected type " + object.getClass());
    }
}
