package com.cliniq.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
public class Schedule {
    @Id
    private int schedule_Id;
    private LocalDate scheduleDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String scheduleStatus;
    private int staffId;

}
