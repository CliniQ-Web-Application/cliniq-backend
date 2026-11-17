package com.cliniq.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies both the local-dev and deployed frontend origins are configured. */
class CorsConfigTest {

    @Test
    void defaultOriginsIncludeLocalhostAndDeployedFrontend() throws Exception {
        Field field = CorsConfig.class.getDeclaredField("allowedOrigins");
        Value value = field.getAnnotation(Value.class);
        assert value != null;
        String expression = value.value();
        assertTrue(expression.contains("http://localhost:3000"));
        assertTrue(expression.contains("https://cliniq-frontend-five.vercel.app"));
    }

    @Test
    void propertyDefaultListsBothOrigins() throws Exception {
        String properties;
        try (var input = getClass().getResourceAsStream("/application.properties")) {
            assert input != null;
            properties = new String(input.readAllBytes());
        }
        String line = Arrays.stream(properties.split("\\R"))
                .filter(text -> text.startsWith("cliniq.cors.allowed-origins="))
                .findFirst().orElse(null);
        assert line != null;
        assertTrue(line.contains("http://localhost:3000"));
        assertTrue(line.contains("https://cliniq-frontend-five.vercel.app"));
    }
}
