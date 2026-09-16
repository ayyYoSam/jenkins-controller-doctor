package io.jenkins.controllerdoctor.checks;

import io.jenkins.controllerdoctor.checks.queue.QueueHealthCheck;
import io.jenkins.controllerdoctor.model.QueueItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueueHealthCheckTest {

    @Test
    void shouldExposeMetadata() {
        QueueHealthCheck check = new QueueHealthCheck();

        assertNotNull(check.id());
        assertNotNull(check.name());
    }

    @Test
    void shouldBuildQueueItem() {
        QueueItem item = QueueItem.builder()
                .id(1)
                .taskName("build-api")
                .blocked(false)
                .buildable(true)
                .stuck(false)
                .build();

        assertEquals(
                "build-api",
                item.taskName()
        );

        assertTrue(item.buildable());
    }
}