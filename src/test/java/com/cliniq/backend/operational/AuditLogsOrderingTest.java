package com.cliniq.backend.operational;

import com.cliniq.backend.controller.AuditLogsController;
import com.cliniq.backend.model.AuditLogs;
import com.cliniq.backend.repository.AuditLogsRepository;
import com.cliniq.backend.repository.StaffRepository;
import com.cliniq.backend.service.AuditLogsService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuditLogsOrderingTest {
    private final AuditLogsRepository logs = mock(AuditLogsRepository.class);
    private final AuditLogsService service = new AuditLogsService(logs, mock(StaffRepository.class));

    @Test
    void serviceReturnsDatabaseOrderedListWithoutInMemorySorting() {
        List<AuditLogs> ordered = entries();
        when(logs.findAllNewestFirst()).thenReturn(ordered);

        assertSame(ordered, service.getAllAuditLogs());
        verify(logs).findAllNewestFirst();
        verifyNoMoreInteractions(logs);
    }

    @Test
    void auditLogsEndpointPreservesNewestFirstTiesAndNullsLast() throws Exception {
        List<AuditLogs> ordered = entries();
        when(logs.findAllForList(any(Pageable.class))).thenReturn(new PageImpl<>(ordered));
        var mvc = MockMvcBuilders.standaloneSetup(new AuditLogsController(service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        mvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[0].logId").value(ordered.get(0).getLogId().toString()))
                .andExpect(jsonPath("$.content[1].logId").value(ordered.get(1).getLogId().toString()))
                .andExpect(jsonPath("$.content[2].logId").value(ordered.get(2).getLogId().toString()))
                .andExpect(jsonPath("$.content[3].logId").value(ordered.get(3).getLogId().toString()));
        verify(logs).findAllForList(any(Pageable.class));
        verifyNoMoreInteractions(logs);
    }

    private List<AuditLogs> entries() {
        LocalDateTime newest = LocalDateTime.of(2030, 1, 2, 9, 0);
        return List.of(entry(4, newest), entry(3, newest), entry(2, newest.minusDays(1)), entry(1, null));
    }

    private AuditLogs entry(long id, LocalDateTime createdAt) {
        AuditLogs entry = new AuditLogs();
        entry.setLogId(new UUID(0, id));
        entry.setCreatedAt(createdAt);
        return entry;
    }
}
