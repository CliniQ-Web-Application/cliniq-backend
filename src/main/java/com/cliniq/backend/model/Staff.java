package com.cliniq.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Staff {
    @Id
    private int staffId;
    private String name;
    private String availability;
    private String email;
    private int departmentId;
    private boolean isActive;

}
