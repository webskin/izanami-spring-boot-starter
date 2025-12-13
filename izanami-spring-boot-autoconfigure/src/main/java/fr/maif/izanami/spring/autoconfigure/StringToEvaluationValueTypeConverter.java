package fr.maif.izanami.spring.autoconfigure;

import fr.maif.izanami.spring.openfeature.EvaluationValueType;
import org.springframework.core.convert.converter.Converter;

/**
 * Spring Boot {@code @ConfigurationProperties} converter for {@link EvaluationValueType}.
 */
final class StringToEvaluationValueTypeConverter implements Converter<String, EvaluationValueType> {
    @Override
    public EvaluationValueType convert(String source) {
        return EvaluationValueType.fromString(source);
    }
}

