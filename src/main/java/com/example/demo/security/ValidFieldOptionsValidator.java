package com.example.demo.security;

import com.example.demo.dto.FieldConfigurationCreateDto;
import com.example.demo.enums.FieldType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidFieldOptionsValidator implements ConstraintValidator<ValidFieldOptions, FieldConfigurationCreateDto> {

    @Override
    public boolean isValid(FieldConfigurationCreateDto dto, ConstraintValidatorContext context) {
        if (dto == null) return true; // skip nulls

        // Example: require options for FieldType.SELECT and FieldType.MULTI_SELECT
        if (dto.fieldType() == FieldType.MULTI_SELECT ||
                dto.fieldType() == FieldType.CASCADING_SELECT ||
                dto.fieldType() == FieldType.CHECKBOX ||
                dto.fieldType() == FieldType.RADIO ||
                dto.fieldType() == FieldType.SINGLE_SELECT
        ) {
            return dto.options() != null && !dto.options().isEmpty();
        }
        return true;
    }
}
