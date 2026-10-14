package com.cliniq.backend.service;

import com.cliniq.backend.model.Queue;
import com.cliniq.backend.repository.QueueRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QueueService {
    private final QueueRepository queueRepository;

    public QueueService(QueueRepository queueRepository) {
        this.queueRepository = queueRepository;
    }

    public List<Queue> getAllQueues() {
        return queueRepository.findAll();
    }

    public Queue getQueueById(UUID id) {
        return queueRepository.findById(id).orElseThrow(() -> new RuntimeException("Queue not found with id: " + id));
    }

    public Queue createQueue(Queue queue) {
        return queueRepository.save(queue);
    }

    public Queue updateQueue(UUID id, Queue details) {
        Queue queue = getQueueById(id);
        queue.setPosition(details.getPosition());
        queue.setAvailabilityStatus(details.getAvailabilityStatus());
        queue.setTimeStamp(details.getTimeStamp());
        queue.setAppointment(details.getAppointment());
        queue.setDepartment(details.getDepartment());
        return queueRepository.save(queue);
    }

    public void deleteQueue(UUID id) {
        if (!queueRepository.existsById(id)) {
            throw new RuntimeException("Queue not found with id: " + id);
        }
        queueRepository.deleteById(id);
    }
}