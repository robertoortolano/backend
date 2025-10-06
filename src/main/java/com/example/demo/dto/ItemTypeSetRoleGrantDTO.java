package com.example.demo.dto;

import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemTypeSetRoleGrantDTO {
    
    private Long id;
    
    private Long itemTypeSetRoleId;
    
    private Long grantId;
    
    private Long tenantId;
    
    // Informazioni del grant per facilitare la visualizzazione
    private Set<Long> grantedUserIds;
    
    private Set<Long> grantedGroupIds;
    
    private Set<Long> negatedUserIds;
    
    private Set<Long> negatedGroupIds;
}

