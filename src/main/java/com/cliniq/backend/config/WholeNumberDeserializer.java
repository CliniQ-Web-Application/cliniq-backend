package com.cliniq.backend.config;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Preserve the API's integer-token contract rather than coercing strings or decimals. */
public class WholeNumberDeserializer extends ValueDeserializer<Number> {
    @Override
    public Number deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return context.reportInputMismatch(Number.class, "Expected a JSON integer");
        }
        return parser.getNumberValue();
    }
}
