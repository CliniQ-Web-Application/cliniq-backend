package com.cliniq.backend.dto;

import com.cliniq.backend.config.WholeNumberDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ScheduleBroadcastRequest(Type type, String message,
        @JsonDeserialize(using = WholeNumberDeserializer.class) Number delayMinutes) {
    public enum Type { DELAY, ABSENCE }
}
