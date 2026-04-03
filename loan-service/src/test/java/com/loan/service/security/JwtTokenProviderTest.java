package com.loan.service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret",
                "ThisIsASecretKeyForJwtTokenThatIsAtLeast32Characters");
        ReflectionTestUtils.setField(provider, "expiration", 86400000L);
    }

    @Nested
    @DisplayName("generateToken")
    class GenerateToken {



        @Test
        @DisplayName("Generates a non-null, non-empty token")
        void shouldGenerateNonEmptyToken() {
            String token = provider.generateToken("test@example.com");

            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("Generated token has 3 parts (header.payload.signature)")
        void shouldHaveThreeParts() {
            String token = provider.generateToken("test@example.com");

            String[] parts = token.split("\\.");
            assertThat(parts).hasSize(3);
        }

        @Test
        @DisplayName("Different emails produce different tokens")
        void shouldProduceDifferentTokensForDifferentEmails() {
            String token1 = provider.generateToken("user1@example.com");
            String token2 = provider.generateToken("user2@example.com");

            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("getEmail")
    class GetEmail {

        @Test
        @DisplayName("Extracts correct email from valid token")
        void shouldExtractEmail() {
            String email = "ashutosh@example.com";
            String token = provider.generateToken(email);

            String extracted = provider.getEmail(token);

            assertThat(extracted).isEqualTo(email);
        }

        @Test
        @DisplayName("Roundtrip: generate → extract preserves email")
        void shouldPreserveEmailOnRoundtrip() {
            String[] emails = {
                    "user@example.com",
                    "admin@test.org",
                    "a.b+c@domain.co.in"
            };

            for (String email : emails) {
                String token = provider.generateToken(email);
                assertThat(provider.getEmail(token)).isEqualTo(email);
            }
        }

        @Test
        @DisplayName("Throws exception for tampered token")
        void shouldThrowForTamperedToken() {
            String token = provider.generateToken("test@example.com");
            String tampered = token + "tampered";

            assertThatThrownBy(() -> provider.getEmail(tampered))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Throws exception for completely invalid token")
        void shouldThrowForInvalidToken() {
            assertThatThrownBy(() -> provider.getEmail("not-a-jwt-token"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Throws exception for empty string")
        void shouldThrowForEmptyToken() {
            assertThatThrownBy(() -> provider.getEmail(""))
                    .isInstanceOf(Exception.class);
        }
    }
}