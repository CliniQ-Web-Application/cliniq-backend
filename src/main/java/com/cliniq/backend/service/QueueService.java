package com.cliniq.backend.service;

import com.cliniq.backend.model.Queue;
import com.cliniq.backend.repository.QueueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QueueService {
    private final QueueRepository queueRepository;
    private final AuditLogsService auditLogsService;

    @Autowired
    public QueueService(QueueRepository queueRepository, AuditLogsService auditLogsService) {
        this.queueRepository = queueRepository;
        this.auditLogsService = auditLogsService;
    }

    public QueueService(QueueRepository queueRepository) {
        this(queueRepository, null);
    }

    public List<Queue> getAllQueues() {
        return queueRepository.findAll();
    }

    public Queue getQueueById(UUID id) {
        return queueRepository.findById(id).orElseThrow(() -> new RuntimeException("Queue not found with id: " + id));
    }

    public Queue createQueue(Queue queue) {
        Queue saved = queueRepository.save(queue);
        if (auditLogsService != null) auditLogsService.record("QUEUE_CREATED", "Queue entry created", "Queue", saved.getQueueId(), null);
        return saved;
    }

    public Queue updateQueue(UUID id, Queue details) {
        Queue queue = getQueueById(id);
        queue.setPosition(details.getPosition());
        queue.setAvailabilityStatus(details.getAvailabilityStatus());
        queue.setTimeStamp(details.getTimeStamp());
        queue.setAppointment(details.getAppointment());
        queue.setDepartment(details.getDepartment());
        Queue saved = queueRepository.save(queue);
        if (auditLogsService != null) auditLogsService.record("QUEUE_UPDATED", "Queue entry updated", "Queue", saved.getQueueId(), null);
        return saved;
    }

    public void deleteQueue(UUID id) {
        if (!queueRepository.existsById(id)) {
            throw new RuntimeException("Queue not found with id: " + id);
        }
        queueRepository.deleteById(id);
        if (auditLogsService != null) auditLogsService.record("QUEUE_DELETED", "Queue entry deleted", "Queue", id, null);
    }
}