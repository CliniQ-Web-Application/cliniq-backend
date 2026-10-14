package com.cliniq.backend.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "department")
public class Department {

    public Department() {
    }
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID departmentId;

    @Column(nullable = false)
    private String name;

    @Column(name = "operating_hours", nullable = false)
    private String operatingHours;

    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOperatingHours() { return operatingHours; }
    public void setOperatingHours(String operatingHours) { this.operatingHours = operatingHours; }
}