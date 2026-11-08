package com.gooodhandletruck.zippick.domain.interest.entity;

import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** List<BuildingType> ↔ "APT,OFFICETEL" */
@Converter
public class BuildingTypeListConverter implements AttributeConverter<List<BuildingType>, String> {

    @Override
    public String convertToDatabaseColumn(List<BuildingType> values) {
        if (values == null) {
            return "";
        }
        return values.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public List<BuildingType> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return List.of();
        }
        return Arrays.stream(column.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(BuildingType::valueOf)
                .toList();
    }
}
