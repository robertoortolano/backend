package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrantRoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Grant grant;

    @ManyToOne(optional = false)
    private Role role;

    @ManyToOne(optional = false)
    private Tenant tenant;

    @ManyToOne
    private Project project; // solo se il ruolo ha scope PROJECT

}

