package io.jenkins.controllerdoctor.checks;

import io.jenkins.controllerdoctor.checks.core.JenkinsVersionCheck;
import io.jenkins.controllerdoctor.model.JenkinsInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JenkinsVersionCheckTest {

    @Test
    void shouldExposeMetadata() {
        JenkinsVersionCheck check = new JenkinsVersionCheck();

        assertEquals(
                "jenkins.version",
                check.id()
        );

        assertNotNull(check.name());
    }

    @Test
    void shouldAcceptModernVersionModel() {
        JenkinsInfo info = JenkinsInfo.builder()
                .displayName("Jenkins")
                .version("2.516.1")
                .build();

        assertEquals(
                "2.516.1",
                info.version()
        );
    }
}