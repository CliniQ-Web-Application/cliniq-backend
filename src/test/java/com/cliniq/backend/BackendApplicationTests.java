package com.cliniq.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full-context smoke test. It needs real deployment configuration (JWT issuer,
 * datasource, Supabase keys), so it is skipped when those environment variables
 * are absent — offline unit tests never start Spring Boot.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "JWT_ISSUER_URI", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = ".+")
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
