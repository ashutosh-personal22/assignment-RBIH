package com.loan.service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider provider;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Valid Token")
    class ValidToken {

        @Test
        @DisplayName("Sets authentication when valid Bearer token is present")
        void shouldSetAuthenticationForValidToken() throws ServletException, IOException {
            request.addHeader("Authorization", "Bearer valid-token");
            when(provider.getEmail("valid-token")).thenReturn("test@example.com");

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isEqualTo("test@example.com");
            assertThat(auth.getCredentials()).isNull();
            assertThat(auth.getAuthorities()).isEmpty();

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("No Token")
    class NoToken {

        @Test
        @DisplayName("Continues filter chain without setting auth when no header")
        void shouldContinueWithoutAuthWhenNoHeader() throws ServletException, IOException {
            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Continues without auth when Authorization header has no Bearer prefix")
        void shouldContinueWhenNoBearerPrefix() throws ServletException, IOException {
            request.addHeader("Authorization", "Basic some-credentials");

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Invalid Token")
    class InvalidToken {

        @Test
        @DisplayName("Continues filter chain without auth when token is invalid")
        void shouldContinueWithoutAuthWhenTokenInvalid() throws ServletException, IOException {
            request.addHeader("Authorization", "Bearer invalid-token");
            when(provider.getEmail("invalid-token")).thenThrow(new RuntimeException("Invalid JWT"));

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Continues filter chain when token is expired")
        void shouldContinueWhenTokenExpired() throws ServletException, IOException {
            request.addHeader("Authorization", "Bearer expired-token");
            when(provider.getEmail("expired-token"))
                    .thenThrow(new RuntimeException("Token expired"));

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Handles empty Bearer token gracefully")
        void shouldHandleEmptyBearerToken() throws ServletException, IOException {
            request.addHeader("Authorization", "Bearer ");
            when(provider.getEmail("")).thenThrow(new RuntimeException("Empty token"));

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Filter always calls chain.doFilter regardless of auth result")
        void shouldAlwaysCallFilterChain() throws ServletException, IOException {
            // No header
            filter.doFilterInternal(request, response, filterChain);
            verify(filterChain, times(1)).doFilter(request, response);

            // With valid header
            reset(filterChain);
            request.addHeader("Authorization", "Bearer token");
            when(provider.getEmail("token")).thenReturn("user@test.com");
            filter.doFilterInternal(request, response, filterChain);
            verify(filterChain, times(1)).doFilter(request, response);
        }
    }
}