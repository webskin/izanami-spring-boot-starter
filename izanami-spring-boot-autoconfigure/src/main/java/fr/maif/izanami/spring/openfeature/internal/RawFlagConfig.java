package fr.maif.izanami.spring.openfeature.internal;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.izanami.spring.autoconfigure.DefaultValueMap;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable configuration class for Spring Boot {@code @ConfigurationProperties} YAML binding.
 * <p>
 * This is an internal class. Use {@link fr.maif.izanami.spring.openfeature.FlagConfig} for the
 * immutable, validated domain model.
 */
public final class RawFlagConfig {

    private static final String SCALAR_KEY = "_scalar";

    private String key;
    private String name;
    private String description;
    private FlagValueType valueType = FlagValueType.BOOLEAN;
    private ErrorStrategy errorStrategy = ErrorStrategy.DEFAULT_VALUE;
    private DefaultValueMap defaultValue;
    private String callbackBean;

    public RawFlagConfig() {
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public FlagValueType getValueType() {
        return valueType;
    }

    public void setValueType(FlagValueType valueType) {
        this.valueType = valueType;
    }

    public ErrorStrategy getErrorStrategy() {
        return errorStrategy;
    }

    public void setErrorStrategy(ErrorStrategy errorStrategy) {
        this.errorStrategy = errorStrategy;
    }

    @Nullable
    public DefaultValueMap getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(@Nullable DefaultValueMap defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Nullable
    public String getCallbackBean() {
        return callbackBean;
    }

    public void setCallbackBean(@Nullable String callbackBean) {
        this.callbackBean = callbackBean;
    }

    /**
     * @return the value type, never null (defaults to BOOLEAN)
     */
    public FlagValueType valueType() {
        return valueType != null ? valueType : FlagValueType.BOOLEAN;
    }

    /**
     * @return the error strategy, never null (defaults to DEFAULT_VALUE)
     */
    public ErrorStrategy errorStrategy() {
        return errorStrategy != null ? errorStrategy : ErrorStrategy.DEFAULT_VALUE;
    }

    /**
     * Extracts the raw default value from the YAML-bound {@link DefaultValueMap}.
     * <p>
     * Handles scalar values (stored under {@code _scalar} key), objects, and arrays.
     *
     * @return the unwrapped raw value, or null if not configured
     */
    @Nullable
    public Object unwrapRawDefaultValue() {
        if (defaultValue == null) {
            return null;
        }
        if (defaultValue.containsKey(SCALAR_KEY)) {
            return defaultValue.get(SCALAR_KEY);
        }
        return normalizeObjectOrArray(defaultValue);
    }

    private Object normalizeObjectOrArray(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalized.put(entry.getKey().toString(), normalizeObjectOrArray(entry.getValue()));
            }
            if (isIndexedMap(normalized)) {
                return indexedMapToList(normalized).stream().map(this::normalizeObjectOrArray).toList();
            }
            return normalized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::normalizeObjectOrArray).toList();
        }
        return value;
    }

    private static boolean isIndexedMap(Map<String, Object> raw) {
        if (raw.isEmpty()) {
            return false;
        }
        for (String key : raw.keySet()) {
            try {
                Integer.parseInt(key);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static List<Object> indexedMapToList(Map<String, Object> raw) {
        return raw.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Integer::parseInt)))
            .map(Map.Entry::getValue)
            .toList();
    }
}
