package com.example.demo.dto;

import com.example.demo.enums.ItemTypeSetRoleType;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemTypeSetRoleDTO {
    
    private Long id;
    
    private ItemTypeSetRoleType roleType;
    
    private String name;
    
    private String description;
    
    private Long itemTypeSetId;
    
    private String relatedEntityType;
    
    private Long relatedEntityId;
    
    private String secondaryEntityType;
    
    private Long secondaryEntityId;
    
    private String tertiaryEntityType;
    
    private Long tertiaryEntityId;
    
    private Long tenantId;
    
    // Assegnazione DUAL
    private Long grantId;
    private String grantName;
    private Long roleTemplateId;
    private String roleTemplateName;
    private String assignmentType; // "GRANT", "ROLE", "GRANTS", "NONE"
}
