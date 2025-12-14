package fr.maif.izanami.spring.openfeature.internal;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagsProperties;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default {@link FlagConfigService} implementation backed by {@link FlagsProperties}.
 * <p>
 * Transforms raw YAML-bound {@link RawFlagConfig} instances into immutable {@link FlagConfig}
 * records with validated and coerced default values.
 */
public final class FlagConfigServiceImpl implements FlagConfigService {
    private static final Logger log = LoggerFactory.getLogger(FlagConfigServiceImpl.class);

    private final Map<String, FlagConfig> configsByName;
    private final Map<String, FlagConfig> configsById;
    private final Map<String, String> nameToId;

    /**
     * Create a service from configured {@code openfeature.flags}.
     *
     * @param flagsProperties properties containing raw flag configurations
     */
    public FlagConfigServiceImpl(FlagsProperties flagsProperties) {
        Map<String, FlagConfig> byName = new LinkedHashMap<>();
        Map<String, FlagConfig> byId = new LinkedHashMap<>();
        Map<String, String> nameToIdMap = new LinkedHashMap<>();

        for (RawFlagConfig raw : flagsProperties.flags()) {
            if (raw == null) {
                continue;
            }

            FlagConfig config = transformAndValidate(raw);

            if (config.name() != null) {
                FlagConfig previous = byName.put(config.name(), config);
                if (previous != null && previous != config) {
                    log.warn("Duplicate flag name '{}' detected; last one wins", config.name());
                }
                if (config.id() != null) {
                    nameToIdMap.put(config.name(), config.id());
                }
            }
            if (config.id() != null) {
                FlagConfig previous = byId.put(config.id(), config);
                if (previous != null && previous != config) {
                    log.warn("Duplicate flag id '{}' detected; last one wins", config.id());
                }
            }
        }

        this.configsByName = Map.copyOf(byName);
        this.configsById = Map.copyOf(byId);
        this.nameToId = Map.copyOf(nameToIdMap);
    }

    @Override
    public Optional<String> findFlagIdByName(String name) {
        return Optional.ofNullable(nameToId.get(name));
    }

    @Override
    public Optional<FlagConfig> getFlagConfigByName(String name) {
        return Optional.ofNullable(configsByName.get(name));
    }

    @Override
    public Optional<FlagConfig> getFlagConfigById(String id) {
        return Optional.ofNullable(configsById.get(id));
    }

    @Override
    public Collection<FlagConfig> getAllFlagConfigs() {
        return configsByName.values();
    }

    /**
     * Transforms a raw YAML-bound config into an immutable {@link FlagConfig}.
     * Validates configuration rules and coerces the default value to the correct type.
     */
    private FlagConfig transformAndValidate(RawFlagConfig raw) {
        ErrorStrategy strategy = raw.errorStrategy();
        FlagValueType valueType = raw.valueType();
        String flagIdentifier = raw.getName() != null ? raw.getName() : raw.getId();

        // Validate: defaultValue only allowed with DEFAULT_VALUE strategy
        if (strategy != ErrorStrategy.DEFAULT_VALUE && raw.getDefaultValue() != null) {
            throw new IllegalArgumentException(
                "Flag '" + flagIdentifier + "' has errorStrategy=" + strategy.name()
                    + " but also defines a defaultValue. "
                    + "The defaultValue property is only valid with errorStrategy=DEFAULT_VALUE."
            );
        }

        // Validate: CALLBACK strategy should have callbackBean
        if (strategy == ErrorStrategy.CALLBACK
                && (raw.getCallbackBean() == null || raw.getCallbackBean().isBlank())) {
            log.warn("Flag '{}' has errorStrategy=CALLBACK but no callbackBean specified; "
                + "will fall back to type-safe defaults on error", flagIdentifier);
        }

        // Validate: callbackBean only allowed with CALLBACK strategy
        if (strategy != ErrorStrategy.CALLBACK && raw.getCallbackBean() != null) {
            throw new IllegalArgumentException(
                "Flag '" + flagIdentifier + "' has callbackBean='" + raw.getCallbackBean()
                    + "' but errorStrategy=" + strategy.name() + ". "
                    + "The callbackBean property is only valid with errorStrategy=CALLBACK."
            );
        }

        // Extract and validate raw default value
        Object rawDefaultValue = raw.unwrapRawDefaultValue();
        validateDefaultValueType(rawDefaultValue, valueType, flagIdentifier);

        // Coerce the default value to the correct type
        Object coercedDefaultValue = coerceDefaultValue(rawDefaultValue, valueType, strategy);

        return new FlagConfig(
            raw.getId(),
            raw.getName(),
            raw.getDescription(),
            valueType,
            strategy,
            coercedDefaultValue,
            raw.getCallbackBean()
        );
    }

    private void validateDefaultValueType(Object defaultValue, FlagValueType valueType, String flagIdentifier) {
        if (defaultValue == null) {
            return;
        }

        switch (valueType) {
            case BOOLEAN -> {
                if (!(defaultValue instanceof Boolean || defaultValue instanceof String || defaultValue instanceof Number)) {
                    throw new IllegalArgumentException(
                        "Flag '" + flagIdentifier + "' has valueType=BOOLEAN but defaultValue cannot be converted to Boolean: " + defaultValue.getClass().getSimpleName()
                    );
                }
            }
            case STRING -> {
                // Any value can be converted to String via toString()
            }
            case INTEGER, DOUBLE -> {
                if (defaultValue instanceof Number) {
                    return;
                }
                if (defaultValue instanceof String s) {
                    try {
                        Double.parseDouble(s);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                            "Flag '" + flagIdentifier + "' has valueType=" + valueType.name()
                                + " but defaultValue '" + s + "' is not a valid number"
                        );
                    }
                    return;
                }
                throw new IllegalArgumentException(
                    "Flag '" + flagIdentifier + "' has valueType=" + valueType.name()
                        + " but defaultValue cannot be converted to a number: " + defaultValue.getClass().getSimpleName()
                );
            }
            case OBJECT -> {
                // Object type accepts Map, List, or String (JSON)
                if (!(defaultValue instanceof Map || defaultValue instanceof List || defaultValue instanceof String)) {
                    throw new IllegalArgumentException(
                        "Flag '" + flagIdentifier + "' has valueType=OBJECT but defaultValue is not a Map, List, or String: " + defaultValue.getClass().getSimpleName()
                    );
                }
            }
        }
    }

    private Object coerceDefaultValue(Object rawValue, FlagValueType valueType, ErrorStrategy strategy) {
        if (rawValue == null) {
            if (strategy != ErrorStrategy.DEFAULT_VALUE) {
                return null;
            }
            // Provide type-safe defaults when no value is configured
            return switch (valueType) {
                case BOOLEAN -> Boolean.FALSE;
                case STRING -> "";
                case INTEGER -> 0;
                case DOUBLE -> 0.0;
                case OBJECT -> Map.of();
            };
        }

        return switch (valueType) {
            case BOOLEAN -> coerceToBoolean(rawValue);
            case STRING -> rawValue.toString();
            case INTEGER -> coerceToInteger(rawValue);
            case DOUBLE -> coerceToDouble(rawValue);
            case OBJECT -> rawValue; // Already normalized
        };
    }

    private Boolean coerceToBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(raw.toString());
    }

    private Integer coerceToInteger(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Double.valueOf(raw.toString()).intValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Double coerceToDouble(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.valueOf(raw.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
