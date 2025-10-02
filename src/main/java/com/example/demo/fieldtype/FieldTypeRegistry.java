package com.example.demo.fieldtype;

import com.example.demo.enums.FieldType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class FieldTypeRegistry {

    private final Map<FieldType, FieldTypeDescriptor> descriptors = new EnumMap<>(FieldType.class);

    final Map<String, Object> numberExtraConfig = new HashMap<>();

    public FieldTypeRegistry() {

        numberExtraConfig.put("allowsDecimal", true);
        numberExtraConfig.put("decimalPlaces", 2);

        descriptors.put(FieldType.SHORT_TEXT, new FieldTypeDescriptor(
                "Short text",
                false,
                false,
                "TextField",
                Map.of()
        ));
        descriptors.put(FieldType.TEXT_AREA, new FieldTypeDescriptor(
                "Text area",
                false,
                false,
                "TextArea",
                Map.of()
        ));
        descriptors.put(FieldType.RTF, new FieldTypeDescriptor(
                "RTF area",
                false,
                false,
                "RTFArea",
                Map.of()
        ));
        descriptors.put(FieldType.NUMBER, new FieldTypeDescriptor(
                "Number",
                false,
                false,
                "NumberInput",
                numberExtraConfig
        ));
        descriptors.put(FieldType.CHECKBOX, new FieldTypeDescriptor(
                "Checkbox",
                true,
                true,
                "CheckboxGroup",
                Map.of()
        ));
        descriptors.put(FieldType.RADIO, new FieldTypeDescriptor(
                "Radio Button",
                true,
                false,
                "RadioGroup",
                Map.of()
        ));
        descriptors.put(FieldType.DATE, new FieldTypeDescriptor(
                "Date",
                false,
                false,
                "Date",
                Map.of()
        ));
        descriptors.put(FieldType.DATETIME, new FieldTypeDescriptor(
                "Date",
                false,
                false,
                "Datetime",
                Map.of()
        ));
        descriptors.put(FieldType.SINGLE_SELECT, new FieldTypeDescriptor(
                "Single dropdown",
                true,
                false,
                "Select",
                Map.of()
        ));
        descriptors.put(FieldType.MULTI_SELECT, new FieldTypeDescriptor(
                "Multiple dropdown",
                true,
                true,
                "MultiSelect",
                Map.of()
        ));
        descriptors.put(FieldType.CASCADING_SELECT, new FieldTypeDescriptor(
                "Cascade dropdown",
                true,
                true,
                "CascadeSelect",
                Map.of()
        ));
        descriptors.put(FieldType.LINK, new FieldTypeDescriptor(
                "Link input",
                false,
                false,
                "LinkInput",
                Map.of()
        ));
        descriptors.put(FieldType.SINGLE_USER, new FieldTypeDescriptor(
                "Single user",
                false,
                false,
                "UserPicker",
                Map.of()
        ));
        descriptors.put(FieldType.MULTI_USER, new FieldTypeDescriptor(
                "Multiple user",
                false,
                true,
                "MultiUserPicker",
                Map.of()
        ));
        descriptors.put(FieldType.SINGLE_GROUP, new FieldTypeDescriptor(
                "Single group",
                false,
                false,
                "GroupPicker",
                Map.of()
        ));
        descriptors.put(FieldType.MULTI_GROUP, new FieldTypeDescriptor(
                "Multiple group",
                false,
                true,
                "MultiGroupPicker",
                Map.of()
        ));
    }

    public FieldTypeDescriptor getDescriptor(FieldType type) {
        return descriptors.get(type);
    }

    public Map<FieldType, FieldTypeDescriptor> getAll() {
        return Map.copyOf(descriptors);
    }

    public Optional<FieldType> toEntityEnum(FieldTypeDescriptor descriptor) {
        return descriptors.entrySet().stream()
                .filter(entry -> entry.getValue().equals(descriptor))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public FieldType toEntityEnumOrThrow(FieldTypeDescriptor descriptor) {
        return toEntityEnum(descriptor)
                .orElseThrow(() -> new IllegalArgumentException("Unknown descriptor: " + descriptor));
    }

}
