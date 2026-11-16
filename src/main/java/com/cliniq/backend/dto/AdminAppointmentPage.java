package com.cliniq.backend.dto;

import java.util.List;

public record AdminAppointmentPage(List<AdminAppointment> items, int page, int size, long totalElements, int totalPages) { }
