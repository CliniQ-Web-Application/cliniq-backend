
package com.cliniq.backend.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SupabaseAdminService {

    private final RestClient client;

    public SupabaseAdminService(
            @Value("${SUPABASE_URL}") String supabaseUrl,
            @Value("${SUPABASE_ROLE_SERVICE_KEY}") String serviceRoleKey) {

        this.client = RestClient.builder().baseUrl(supabaseUrl).defaultHeader("apikey", serviceRoleKey)
                .defaultHeader(
                        "Authorization",
                        "Bearer " + serviceRoleKey
                )
                .build();
    }

    public String createUser(String email, String password) {
        Map<?, ?> result = client.post()
                .uri("/auth/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", password,
                        "email_confirm", true
                ))
                .retrieve()
                .body(Map.class);

        if (result == null || result.get("id") == null) {throw new IllegalStateException( "Supabase did not return a user UUID." ); }

        return result.get("id").toString();
    }

    /**
     * Deactivates a Supabase Auth account without deleting it. Banning the user
     * keeps audit history intact while immediately revoking their sessions.
     */
    public void deactivateUser(UUID authUserId) {
        client.put()
                .uri("/auth/v1/admin/users/{id}", authUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "ban_duration", "876000h",
                        "app_metadata", Map.of("cliniq_deactivated", true)
                ))
                .retrieve()
                .toBodilessEntity();
    }
}

