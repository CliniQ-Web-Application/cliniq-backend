package com.cliniq.backend.model;

import com.cliniq.backend.enums.SlotStatus;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.cliniq.backend.config.WholeNumberDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "schedule")
public class Schedule {

    public Schedule() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID slotId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_status", nullable = false)
    private SlotStatus slotStatus;//make enum

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    // Null means the legacy behaviour of one appointment per slot.
    @Column(name = "capacity")
    private Integer capacity;

    public UUID getSlotId() { return slotId; }
    public void setSlotId(UUID slotId) { this.slotId = slotId; }
    public LocalDate getSlotDate() { return slotDate; }
    public void setSlotDate(LocalDate slotDate) { this.slotDate = slotDate; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public SlotStatus getSlotStatus() { return slotStatus; }
    public void setSlotStatus(SlotStatus slotStatus) { this.slotStatus = slotStatus; }
    public Staff getStaff() { return staff; }
    public void setStaff(Staff staff) { this.staff = staff; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    @JsonSetter("capacity")
    @JsonDeserialize(using = WholeNumberDeserializer.class)
    public void readCapacity(Number value) {
        if (value != null && (!(value instanceof Integer || value instanceof Long)
                || value.longValue() > Integer.MAX_VALUE || value.longValue() < Integer.MIN_VALUE)) {
            throw new IllegalArgumentException("capacity must be a whole number");
        }
        this.capacity = value == null ? null : value.intValue();
    }

    // The effective booking limit; null means the legacy single-appointment behaviour.
    public int effectiveCapacity() { return capacity == null ? 1 : capacity; }
}
