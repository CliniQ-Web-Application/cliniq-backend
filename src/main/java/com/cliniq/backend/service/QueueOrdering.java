package com.cliniq.backend.service;

import com.cliniq.backend.enums.Priority;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Queue;

import java.time.LocalDateTime;
import java.util.Comparator;

/** Shared clinical order; missing legacy timestamps/IDs sort last within their priority. */
public final class QueueOrdering {
    private QueueOrdering() { }

    public static final Comparator<Queue> NEXT_PATIENT = Comparator
            .comparingInt((Queue queue) -> queue.getAppointment().getPriority() == Priority.URGENT ? 0 : 1)
            .thenComparing(queue -> bookingMoment(queue.getAppointment()),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(queue -> queue.getAppointment().getAppointmentId(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Queue::getQueueId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static LocalDateTime bookingMoment(Appointment appointment) {
        if (appointment.getBookedAt() != null) return appointment.getBookedAt();
        if (appointment.getCheckInTime() != null) return appointment.getCheckInTime();
        return appointment.getCreatedAt();
    }
}
