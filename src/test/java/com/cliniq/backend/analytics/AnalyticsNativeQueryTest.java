package com.cliniq.backend.analytics;

import com.cliniq.backend.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the PostgreSQL parameter typing required by the native analytics queries. */
class AnalyticsNativeQueryTest {

    @Test
    void optionalParametersAreExplicitlyCastInEveryNativeAnalyticsQuery() {
        for (String name : new String[]{"countAppointmentsByStatus", "averageWaitMinutes",
                "averageWaitByDepartment", "arrivalsByHour"}) {
            var method = Arrays.stream(AppointmentRepository.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name))
                    .findFirst().orElseThrow();
            String sql = method.getAnnotation(Query.class).value();

            assertTrue(sql.contains("cast(:from as date) is null"), name + " must cast :from");
            assertTrue(sql.contains("cast(:to as date) is null"), name + " must cast :to");
            assertTrue(sql.contains("cast(:departmentId as uuid) is null"), name + " must cast :departmentId");
            assertTrue(sql.contains("cast(:doctorId as uuid) is null"), name + " must cast :doctorId");
        }
    }
}
