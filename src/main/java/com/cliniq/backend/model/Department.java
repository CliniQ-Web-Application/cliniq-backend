package com.cliniq.backend.model;

import jakarta.persistence.Id;

public class Department {
    @Id
    private int departmentID;
    private String departmentName;
    private String openingHours;
}
