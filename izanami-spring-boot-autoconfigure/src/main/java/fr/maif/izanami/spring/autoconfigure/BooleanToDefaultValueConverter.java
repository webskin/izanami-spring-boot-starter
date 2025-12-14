package fr.maif.izanami.spring.autoconfigure;

import org.springframework.core.convert.converter.Converter;

/**
 * Spring Boot {@code @ConfigurationProperties} converter for binding scalar booleans into a map-based default value.
 */
final class BooleanToDefaultValueConverter implements Converter<Boolean, DefaultValueMap> {
    private static final String SCALAR_KEY = "_scalar";

    @Override
    public DefaultValueMap convert(Boolean source) {
        DefaultValueMap map = new DefaultValueMap();
        if (source != null) {
            map.put(SCALAR_KEY, source);
        }
        return map;
    }
}

