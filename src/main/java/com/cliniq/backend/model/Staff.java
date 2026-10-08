package com.cliniq.backend.model;

import com.cliniq.backend.enums.AvailabilityStatus;
import com.cliniq.backend.enums.StaffRole;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "staff")
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID staffId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StaffRole staffRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 10)
    private AvailabilityStatus availabilityStatus;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;
}
