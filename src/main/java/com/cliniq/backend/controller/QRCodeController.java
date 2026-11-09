package com.cliniq.backend.controller;

import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.StaffRepository;
import com.cliniq.backend.service.AccessPolicy;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public class QRCodeController {
    private static final int SIZE = 300;

    private final AppointmentRepository appointments;
    private final StaffRepository staff;
    private final String appBaseUrl;

    public QRCodeController(AppointmentRepository appointments, StaffRepository staff,
                            @Value("${cliniq.app-base-url:http://localhost:3000}") String appBaseUrl) {
        this.appointments = appointments;
        this.staff = staff;
        this.appBaseUrl = appBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping("/{id}/qr")
    public ResponseEntity<byte[]> qrCode(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = AccessPolicy.subject(jwt);
        Appointment appointment = appointments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        boolean owner = appointment.getPatient() != null && userId.equals(appointment.getPatient().getAuthUserId());
        boolean activeStaff = staff.findByAuthUserId(userId).filter(profile -> Boolean.TRUE.equals(profile.getActive())).isPresent();
        if (!owner && !activeStaff) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view the QR code for your own appointment");
        }
        byte[] png = encode(appBaseUrl + "/check-in?id=" + appointment.getAppointmentId());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header("Cache-Control", "no-store").body(png);
    }

    private byte[] encode(String content) {
        BitMatrix matrix;
        try {
            matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE, Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
        } catch (WriterException e) {
            throw new IllegalStateException("Could not encode the appointment identifier", e);
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
