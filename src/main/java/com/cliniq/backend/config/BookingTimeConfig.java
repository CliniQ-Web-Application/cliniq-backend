package com.cliniq.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class BookingTimeConfig {
    @Bean
    public Clock bookingClock(@Value("${cliniq.booking.zone-id:Africa/Gaborone}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
