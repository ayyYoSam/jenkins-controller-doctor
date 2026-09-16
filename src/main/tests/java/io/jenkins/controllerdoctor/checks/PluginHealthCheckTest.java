package io.jenkins.controllerdoctor.checks;

import io.jenkins.controllerdoctor.checks.plugins.PluginHealthCheck;
import io.jenkins.controllerdoctor.model.PluginInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PluginHealthCheckTest {

    @Test
    void shouldExposeMetadata() {
        PluginHealthCheck check = new PluginHealthCheck();

        assertNotNull(check.id());
        assertNotNull(check.name());
    }

    @Test
    void shouldBuildPluginModel() {
        PluginInfo plugin = PluginInfo.builder()
                .shortName("git")
                .longName("Git")
                .version("5.8.0")
                .enabled(true)
                .active(true)
                .build();

        assertEquals(
                "git",
                plugin.shortName()
        );

        assertTrue(plugin.enabled());
    }
}
