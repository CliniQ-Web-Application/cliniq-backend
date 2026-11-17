package com.cliniq.backend.qr;

import com.cliniq.backend.controller.QRCodeController;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.Priority;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Patient;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.StaffRepository;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QRCodeControllerTest {
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final QRCodeController controller = new QRCodeController(appointments, staff, "http://localhost:3000/");

    private Jwt jwt(UUID subject) {
        return Jwt.withTokenValue("test").header("alg", "test").subject(subject.toString()).build();
    }

    @Test
    void qrEncodesCheckInUrlNotJson() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Patient patient = new Patient();
        patient.setPatientID(UUID.randomUUID());
        patient.setAuthUserId(userId);
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setAppointmentStatus(AppointmentStatus.BOOKED);
        appointment.setPriority(Priority.NORMAL);
        appointment.setPatient(patient);
        when(appointments.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(staff.findByAuthUserId(userId)).thenReturn(Optional.empty());

        ResponseEntity<byte[]> response = controller.qrCode(jwt(userId), appointmentId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/png", response.getHeaders().getContentType().toString());
        assertNotNull(response.getBody());

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.getBody()));
        assertNotNull(image);
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = rgb.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        Result decoded = new MultiFormatReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(rgb))));
        assertEquals("http://localhost:3000/check-in?id=" + appointmentId, decoded.getText());
        assertFalse(decoded.getText().startsWith("{"), "QR payload must be a URL, not JSON");
    }

    @Test
    void qrRejectsCallersWhoAreNeitherOwnerNorStaff() {
        UUID appointmentId = UUID.randomUUID();
        Patient patient = new Patient();
        patient.setPatientID(UUID.randomUUID());
        patient.setAuthUserId(UUID.randomUUID());
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setPatient(patient);
        when(appointments.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(staff.findByAuthUserId(any())).thenReturn(Optional.empty());

        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(org.springframework.web.server.ResponseStatusException.class,
                        () -> controller.qrCode(jwt(UUID.randomUUID()), appointmentId)).getStatusCode());
    }

    @Test
    void qrReturns404ForUnknownAppointment() {
        UUID id = UUID.randomUUID();
        when(appointments.findById(id)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(org.springframework.web.server.ResponseStatusException.class,
                        () -> controller.qrCode(jwt(UUID.randomUUID()), id)).getStatusCode());
    }
}
