package com.cliniq.backend.dto;

import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.config.WholeNumberDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AdminScheduleRequest(UUID staffId, LocalDate slotDate, LocalTime startTime,
                                   LocalTime endTime, @JsonDeserialize(using = WholeNumberDeserializer.class) Number capacity, SlotStatus slotStatus) { }
