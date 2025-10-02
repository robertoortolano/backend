package com.example.demo.fieldtype;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class FieldTypeDescriptor {
    private String displayName;
    private boolean supportsOptions;
    private boolean supportsMultiple;
    private String frontendComponent;

    private Map<String, Object> extraConfig;
}
