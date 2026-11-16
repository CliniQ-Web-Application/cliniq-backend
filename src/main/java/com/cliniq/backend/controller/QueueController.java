package com.cliniq.backend.controller;

import com.cliniq.backend.dto.PageResponse;
import com.cliniq.backend.dto.QueueResponse;
import com.cliniq.backend.model.Queue;
import com.cliniq.backend.service.QueueService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/queues")
public class QueueController {
    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<QueueResponse>> getAll(
            @PageableDefault(size = 200, sort = "position") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(queueService.getQueues(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<QueueResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(queueService.getQueueDto(id));
    }

    @PostMapping
    public ResponseEntity<QueueResponse> create(@RequestBody Queue queue) {
        return ResponseEntity.status(HttpStatus.CREATED).body(queueService.createQueueDto(queue));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QueueResponse> update(@PathVariable UUID id, @RequestBody Queue queue) {
        return ResponseEntity.ok(queueService.updateQueueDto(id, queue));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        queueService.deleteQueue(id);
        return ResponseEntity.noContent().build();
    }
}
