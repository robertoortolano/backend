package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
public class License {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "license_id")
    private Long id;

    @Column(name = "license_key", unique = true, nullable = false)
    private String licenseKey;

    private LocalDate startDate;

    private LocalDate expirationDate;

    @OneToOne(mappedBy = "license", cascade = CascadeType.PERSIST)
    private Tenant tenant;

    // Costruttori
    public License() {}

    public License(String licenseKey, LocalDate expirationDate) {
        this.licenseKey = licenseKey;
        this.expirationDate = expirationDate;
    }

}
