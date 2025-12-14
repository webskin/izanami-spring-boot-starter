package fr.maif.izanami.spring.autoconfigure;

import org.springframework.core.convert.converter.Converter;

/**
 * Spring Boot {@code @ConfigurationProperties} converter for binding scalar strings into a map-based default value.
 */
final class StringToDefaultValueConverter implements Converter<String, DefaultValueMap> {
    private static final String SCALAR_KEY = "_scalar";

    @Override
    public DefaultValueMap convert(String source) {
        DefaultValueMap map = new DefaultValueMap();
        if (source != null) {
            map.put(SCALAR_KEY, source);
        }
        return map;
    }
}

