package com.cliniq.backend.controller;

import com.cliniq.backend.model.Queue;
import com.cliniq.backend.service.QueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/queues")
public class QueueController {
    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping
    public ResponseEntity<List<Queue>> getAll() {
        return ResponseEntity.ok(queueService.getAllQueues());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Queue> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(queueService.getQueueById(id));
    }

    @PostMapping
    public ResponseEntity<Queue> create(@RequestBody Queue queue) {
        return ResponseEntity.status(HttpStatus.CREATED).body(queueService.createQueue(queue));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Queue> update(@PathVariable UUID id, @RequestBody Queue queue) {
        return ResponseEntity.ok(queueService.updateQueue(id, queue));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        queueService.deleteQueue(id);
        return ResponseEntity.noContent().build();
    }
}