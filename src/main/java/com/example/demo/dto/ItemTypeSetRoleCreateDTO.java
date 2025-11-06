package com.example.demo.dto;

import com.example.demo.enums.ItemTypeSetRoleType;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemTypeSetRoleCreateDTO {
    
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
    
    @Builder.Default
    private Set<Long> grantIds = Set.of();
}
