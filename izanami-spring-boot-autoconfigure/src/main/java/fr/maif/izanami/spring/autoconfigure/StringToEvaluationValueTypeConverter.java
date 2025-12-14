package fr.maif.izanami.spring.autoconfigure;

import fr.maif.izanami.spring.openfeature.EvaluationValueType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

/**
 * Spring Boot {@code @ConfigurationProperties} converter for {@link EvaluationValueType}.
 */
final class StringToEvaluationValueTypeConverter implements Converter<String, EvaluationValueType> {
    @Override
    public EvaluationValueType convert(@NonNull String source) {
        return EvaluationValueType.fromString(source);
    }
}

