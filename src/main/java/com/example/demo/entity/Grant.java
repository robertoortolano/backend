package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "grant_assignment")
public class Grant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Role role;

    @ManyToMany
    private Set<User> users = new HashSet<>();

    @ManyToMany
    private Set<Group> groups = new HashSet<>();

    @ManyToMany @JoinTable(name = "grant_negated_users")
    private Set<User> negatedUsers = new HashSet<>();

    @ManyToMany @JoinTable(name = "grant_negated_groups")
    private Set<Group> negatedGroups = new HashSet<>();

}
