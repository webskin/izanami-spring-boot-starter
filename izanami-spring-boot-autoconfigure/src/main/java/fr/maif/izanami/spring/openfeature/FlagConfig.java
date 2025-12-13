package fr.maif.izanami.spring.openfeature;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Configuration for a single OpenFeature flag backed by Izanami.
 * <p>
 * This type is designed for Spring Boot {@code @ConfigurationProperties} binding, including support for
 * YAML objects/arrays as {@code defaultValue} when {@code valueType=object}.
 */
public final class FlagConfig {

    private static final String SCALAR_KEY = "_scalar";

    private String id;
    private String name;
    private String description;
    private EvaluationValueType valueType = EvaluationValueType.BOOLEAN;
    private ErrorStrategy errorStrategy = ErrorStrategy.DEFAULT_VALUE;
    @Nullable
    private Map<String, Object> defaultValue;

    /**
     * Create an empty config instance for configuration binding.
     */
    public FlagConfig() {
    }

    /**
     * @return the Izanami feature id.
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the Izanami feature id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the OpenFeature flag key (human-friendly name).
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the OpenFeature flag key (human-friendly name)
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the flag description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the flag description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the configured value type (defaults to {@link EvaluationValueType#BOOLEAN}).
     */
    public EvaluationValueType getValueType() {
        return valueType;
    }

    /**
     * @param valueType the configured value type
     */
    public void setValueType(EvaluationValueType valueType) {
        this.valueType = valueType;
    }

    /**
     * @return the configured error strategy (defaults to {@link ErrorStrategy#DEFAULT_VALUE}).
     */
    public ErrorStrategy getErrorStrategy() {
        return errorStrategy;
    }

    /**
     * @param errorStrategy the configured error strategy
     */
    public void setErrorStrategy(ErrorStrategy errorStrategy) {
        this.errorStrategy = errorStrategy;
    }

    /**
     * @return the raw configured default value.
     * <p>
     * For YAML scalars, this map contains a single {@code _scalar} entry.
     * For YAML objects, this map contains the YAML keys.
     * For YAML arrays, this map contains numeric keys ({@code "0"}, {@code "1"}, ...).
     */
    @Nullable
    public Map<String, Object> getDefaultValue() {
        return defaultValue;
    }

    /**
     * @param defaultValue the raw configured default value
     */
    public void setDefaultValue(@Nullable Map<String, Object> defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Record-like accessor for compatibility within this library.
     *
     * @return the id
     */
    public String id() {
        return getId();
    }

    /**
     * Record-like accessor for compatibility within this library.
     *
     * @return the name
     */
    public String name() {
        return getName();
    }

    /**
     * Record-like accessor for compatibility within this library.
     *
     * @return the description
     */
    public String description() {
        return getDescription();
    }

    /**
     * Record-like accessor for compatibility within this library.
     *
     * @return the value type (never {@code null})
     */
    public EvaluationValueType valueType() {
        return valueType != null ? valueType : EvaluationValueType.BOOLEAN;
    }

    /**
     * Record-like accessor for compatibility within this library.
     *
     * @return the error strategy (never {@code null})
     */
    public ErrorStrategy errorStrategy() {
        return errorStrategy != null ? errorStrategy : ErrorStrategy.DEFAULT_VALUE;
    }

    /**
     * Record-like accessor for compatibility within this library.
     * <p>
     * If {@code defaultValue} is not provided and {@code errorStrategy=DEFAULT_VALUE}, this returns a sensible default
     * based on {@link #valueType()}.
     *
     * @return the default value, possibly computed
     */
    @Nullable
    public Object defaultValue() {
        if (defaultValue != null) {
            return unwrapDefaultValue(defaultValue);
        }
        if (errorStrategy() != ErrorStrategy.DEFAULT_VALUE) {
            return null;
        }
        return switch (valueType()) {
            case BOOLEAN -> Boolean.FALSE;
            case STRING -> "";
            case NUMBER -> BigDecimal.ZERO;
            case OBJECT -> Map.of();
        };
    }

    private Object unwrapDefaultValue(Map<String, Object> raw) {
        if (raw.containsKey(SCALAR_KEY)) {
            Object scalar = raw.get(SCALAR_KEY);
            return coerceScalar(scalar);
        }
        if (valueType() == EvaluationValueType.OBJECT) {
            return normalizeObjectOrArray(raw);
        }
        return raw;
    }

    private Object normalizeObjectOrArray(Object value) {
        if (value instanceof Map<?, ?> map) {
            java.util.Map<String, Object> normalized = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
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
        if (value instanceof java.util.List<?> list) {
            return list.stream().map(this::normalizeObjectOrArray).toList();
        }
        return value;
    }

    private Object coerceScalar(@Nullable Object scalar) {
        if (scalar == null) {
            return null;
        }
        if (valueType() == EvaluationValueType.STRING || valueType() == EvaluationValueType.OBJECT) {
            return scalar.toString();
        }
        if (valueType() == EvaluationValueType.BOOLEAN) {
            if (scalar instanceof Boolean b) {
                return b;
            }
            return Boolean.parseBoolean(scalar.toString());
        }
        if (valueType() == EvaluationValueType.NUMBER) {
            if (scalar instanceof BigDecimal bd) {
                return bd;
            }
            try {
                return new BigDecimal(scalar.toString());
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
        return scalar;
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

    private static java.util.List<Object> indexedMapToList(Map<String, Object> raw) {
        return raw.entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey(java.util.Comparator.comparingInt(Integer::parseInt)))
            .map(java.util.Map.Entry::getValue)
            .toList();
    }
}
