package com.cliniq.backend.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "department")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID departmentId;

    @Column(nullable = false)
    private String name;

    @Column(name = "operating_hours", nullable = false)
    private String operatingHours;
}