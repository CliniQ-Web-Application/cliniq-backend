package com.cliniq.backend.dto;

import java.util.List;

public record AnalyticsResponse(
        Metrics metrics,
        List<DepartmentWait> waitByDepartment,
        List<ArrivalHour> peakArrivalHours,
        List<Outcome> outcomes,
        List<FilterOption> departments,
        List<FilterOption> doctors
) {
    public record Metrics(
            long appointments,
            long completed,
            long cancelled,
            long noShows,
            long averageWaitMinutes
    ) {}

    public record DepartmentWait(String department, long averageWaitMinutes) {}

    public record ArrivalHour(int hour, long count) {}

    public record Outcome(String status, long count) {}

    public record FilterOption(String id, String label) {}
}
