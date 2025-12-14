package fr.maif.izanami.spring.autoconfigure;

import org.springframework.core.convert.converter.Converter;

/**
 * Spring Boot {@code @ConfigurationProperties} converter for binding scalar numbers into a map-based default value.
 */
final class NumberToDefaultValueConverter implements Converter<Number, DefaultValueMap> {
    private static final String SCALAR_KEY = "_scalar";

    @Override
    public DefaultValueMap convert(Number source) {
        DefaultValueMap map = new DefaultValueMap();
        if (source != null) {
            map.put(SCALAR_KEY, source);
        }
        return map;
    }
}

