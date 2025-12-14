package fr.maif.izanami.spring.autoconfigure;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

/**
 * Spring Boot {@code @ConfigurationProperties} converter for binding scalar strings into a map-based default value.
 */
final class StringToDefaultValueConverter implements Converter<String, DefaultValueMap> {
    private static final String SCALAR_KEY = "_scalar";

    @Override
    public DefaultValueMap convert(@NonNull String source) {
        DefaultValueMap map = new DefaultValueMap();
        map.put(SCALAR_KEY, source);
        return map;
    }
}

