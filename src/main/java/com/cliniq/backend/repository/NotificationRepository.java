package com.cliniq.backend.repository;

import com.cliniq.backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByPatient_AuthUserIdOrderByCreatedAtDesc(UUID authUserId);

    List<Notification> findByPatientIsNullAndAppointment_Staff_AuthUserIdOrderByCreatedAtDesc(UUID authUserId);
}