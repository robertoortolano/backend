package com.example.demo.dto;

import lombok.Data;
import java.util.Map;

@Data
public class FieldTypeDescriptorDto {
    private String displayName;
    private boolean supportsOptions;
    private boolean supportsMultiple;
    private String frontendComponent;
    private Map<String, Object> extraConfig;

    public FieldTypeDescriptorDto() {}

    public FieldTypeDescriptorDto(String displayName, boolean supportsOptions, boolean supportsMultiple,
                                  String frontendComponent, Map<String, Object> extraConfig) {
        this.displayName = displayName;
        this.supportsOptions = supportsOptions;
        this.supportsMultiple = supportsMultiple;
        this.frontendComponent = frontendComponent;
        this.extraConfig = extraConfig;
    }
}


