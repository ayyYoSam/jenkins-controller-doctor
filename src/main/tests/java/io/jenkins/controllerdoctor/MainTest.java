package io.jenkins.controllerdoctor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Basic tests for the application entry point.
 */
class MainTest {

    @Test
    void shouldExposeMainMethod() {
        assertDoesNotThrow(() -> Main.class.getMethod(
                "main",
                String[].class
        ));
    }
}