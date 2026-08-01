package com.accesmed.backend.Domain.Converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Duration;

/**
 * Convierte {@code java.time.Duration} hacia y desde el tipo {@code interval} de
 * PostgreSQL, usando el formato de texto ISO-8601 ({@code Duration#toString()} /
 * {@code Duration#parse(CharSequence)}), que PostgreSQL acepta como literal de entrada
 * para columnas {@code interval}.
 *
 * <p>Se aplica con {@code @Convert(converter = DurationIntervalConverter.class)} en cada
 * atributo {@code Duration} del dominio (las siete tolerancias de {@link
 * com.accesmed.backend.Domain.Prestacion}).</p>
 */
@Converter
public class DurationIntervalConverter implements AttributeConverter<Duration, String> {

    //region ========== Métodos ==========

    /**
     * Convierte un {@code Duration} del dominio a su representación de texto ISO-8601
     * para persistir en una columna {@code interval}.
     *
     * @param attribute {@code Duration} valor del dominio, puede ser {@code null}
     * @return {@code String} representación ISO-8601, o {@code null} si el atributo es {@code null}
     */
    @Override
    public String convertToDatabaseColumn(Duration attribute) {

        return attribute == null ? null : attribute.toString();

    }

    /**
     * Reconstruye un {@code Duration} a partir del valor de texto leído de la columna
     * {@code interval}.
     *
     * @param dbData {@code String} valor leído de la base, puede ser {@code null}
     * @return {@code Duration} reconstruido, o {@code null} si el valor leído es {@code null}
     */
    @Override
    public Duration convertToEntityAttribute(String dbData) {

        return dbData == null ? null : Duration.parse(dbData);

    }

    //endregion

}
