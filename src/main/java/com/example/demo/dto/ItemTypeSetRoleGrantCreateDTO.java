package com.example.demo.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemTypeSetRoleGrantCreateDTO {
    
    private Long itemTypeSetRoleId;
    
    private Long grantId;
}
