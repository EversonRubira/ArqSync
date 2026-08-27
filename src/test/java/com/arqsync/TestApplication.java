package com.arqsync;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot configuration used by {@code @DataJpaTest}/{@code @SpringBootTest}
 * in com.arqsync.* subpackages — there is no real {@code @SpringBootApplication} yet
 * since the CLI component (SPEC-cli.md) hasn't been implemented. Placed in the
 * {@code com.arqsync} root test package so Spring Boot's upward config search finds
 * it from any subpackage. Test-only; not part of the production module.
 */
@SpringBootApplication
class TestApplication {
}
