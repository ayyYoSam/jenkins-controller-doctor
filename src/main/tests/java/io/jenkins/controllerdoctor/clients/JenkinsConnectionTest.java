package io.jenkins.controllerdoctor.client;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class JenkinsConnectionTest {

    @Test
    void shouldBuildConnectionWithDefaults() {
        JenkinsConnection connection = JenkinsConnection.builder()
                .baseUrl("http://localhost:8080/")
                .build();

        assertEquals(
                URI.create("http://localhost:8080"),
                connection.baseUri()
        );

        assertEquals(
                Duration.ofSeconds(10),
                connection.connectTimeout()
        );

        assertEquals(
                Duration.ofSeconds(30),
                connection.requestTimeout()
        );

        assertFalse(connection.hasAuthentication());
    }

    @Test
    void shouldConfigureAuthentication() {
        JenkinsConnection connection = JenkinsConnection.builder()
                .baseUrl("https://jenkins.example.com")
                .username("admin")
                .apiToken("secret-token")
                .build();

        assertEquals("admin", connection.username());
        assertEquals("secret-token", connection.apiToken());
        assertTrue(connection.hasAuthentication());
    }

    @Test
    void shouldNormalizeTrailingSlash() {
        JenkinsConnection connection = JenkinsConnection.builder()
                .baseUrl("https://jenkins.example.com///")
                .build();

        assertEquals(
                URI.create("https://jenkins.example.com"),
                connection.baseUri()
        );
    }

    @Test
    void shouldRejectUnsupportedScheme() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JenkinsConnection.builder()
                        .baseUrl("ftp://jenkins.example.com")
                        .build()
        );
    }

    @Test
    void shouldRejectEmbeddedCredentials() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JenkinsConnection.builder()
                        .baseUrl("https://admin:secret@jenkins.example.com")
                        .build()
        );
    }

    @Test
    void shouldRejectIncompleteAuthentication() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JenkinsConnection.builder()
                        .baseUrl("https://jenkins.example.com")
                        .username("admin")
                        .build()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> JenkinsConnection.builder()
                        .baseUrl("https://jenkins.example.com")
                        .apiToken("secret")
                        .build()
        );
    }

    @Test
    void shouldRejectInvalidTimeouts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JenkinsConnection.builder()
                        .baseUrl("https://jenkins.example.com")
                        .connectTimeout(Duration.ZERO)
                        .build()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> JenkinsConnection.builder()
                        .baseUrl("https://jenkins.example.com")
                        .requestTimeout(Duration.ofSeconds(-1))
                        .build()
        );
    }

    @Test
    void shouldHideApiTokenFromToString() {
        JenkinsConnection connection = JenkinsConnection.builder()
                .baseUrl("https://jenkins.example.com")
                .username("admin")
                .apiToken("super-secret-token")
                .build();

        String value = connection.toString();

        assertFalse(value.contains("super-secret-token"));
        assertTrue(value.contains("admin"));
    }
}