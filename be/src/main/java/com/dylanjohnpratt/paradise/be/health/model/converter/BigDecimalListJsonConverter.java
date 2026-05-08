package com.dylanjohnpratt.paradise.be.health.model.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;
import java.util.List;

@Converter
public class BigDecimalListJsonConverter implements AttributeConverter<List<BigDecimal>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<BigDecimal>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<BigDecimal> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize List<BigDecimal> to JSON", e);
        }
    }

    @Override
    public List<BigDecimal> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize List<BigDecimal> from JSON", e);
        }
    }
}
