package io.jenkins.controllerdoctor.client;

import io.jenkins.controllerdoctor.utils.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JenkinsClientTest {

    private final JsonUtil json = new JsonUtil();

    @Test
    void shouldCreateJsonUtility() {
        assertNotNull(json);
        assertNotNull(json.mapper());
    }

    @Test
    void shouldExposeConfiguredMapper() {
        assertDoesNotThrow(json::mapper);
    }
}