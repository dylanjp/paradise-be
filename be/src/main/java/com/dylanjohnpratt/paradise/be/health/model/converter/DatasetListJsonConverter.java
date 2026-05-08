package com.dylanjohnpratt.paradise.be.health.model.converter;

import com.dylanjohnpratt.paradise.be.health.model.Dataset;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class DatasetListJsonConverter implements AttributeConverter<List<Dataset>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Dataset>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<Dataset> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize List<Dataset> to JSON", e);
        }
    }

    @Override
    public List<Dataset> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize List<Dataset> from JSON", e);
        }
    }
}
