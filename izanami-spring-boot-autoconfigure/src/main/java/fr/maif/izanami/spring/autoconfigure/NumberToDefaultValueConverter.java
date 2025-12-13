package fr.maif.izanami.spring.autoconfigure;

import org.springframework.core.convert.converter.Converter;

import java.util.Map;

/**
 * Spring Boot {@code @ConfigurationProperties} converter for binding scalar numbers into a map-based default value.
 */
final class NumberToDefaultValueConverter implements Converter<Number, Map<String, Object>> {
    private static final String SCALAR_KEY = "_scalar";

    @Override
    public Map<String, Object> convert(Number source) {
        if (source == null) {
            return Map.of();
        }
        return Map.of(SCALAR_KEY, source);
    }
}

