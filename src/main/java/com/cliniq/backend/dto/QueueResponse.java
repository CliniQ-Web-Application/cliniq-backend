package com.cliniq.backend.dto;

import com.cliniq.backend.enums.AvailabilityStatus;
import com.cliniq.backend.model.Queue;

import java.time.LocalTime;
import java.util.UUID;

public record QueueResponse(
        UUID queueId,
        Integer position,
        String availabilityStatus,
        LocalTime timeStamp,
        DepartmentResponse department,
        AppointmentResponse appointment
) {
    public static QueueResponse from(Queue queue) {
        if (queue == null) return null;
        AvailabilityStatus availability = queue.getAvailabilityStatus();
        return new QueueResponse(
                queue.getQueueId(),
                queue.getPosition(),
                availability == null ? null : availability.name(),
                queue.getTimeStamp(),
                DepartmentResponse.from(queue.getDepartment()),
                AppointmentResponse.from(queue.getAppointment())
        );
    }
}
