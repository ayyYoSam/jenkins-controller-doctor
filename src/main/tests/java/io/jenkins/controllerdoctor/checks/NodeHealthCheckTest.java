package io.jenkins.controllerdoctor.checks;

import io.jenkins.controllerdoctor.checks.nodes.NodeHealthCheck;
import io.jenkins.controllerdoctor.model.NodeInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeHealthCheckTest {

    @Test
    void shouldExposeMetadata() {
        NodeHealthCheck check = new NodeHealthCheck();

        assertNotNull(check.id());
        assertNotNull(check.name());
    }

    @Test
    void shouldBuildNodeModel() {
        NodeInfo node = NodeInfo.builder()
                .displayName("Built-In Node")
                .offline(false)
                .numExecutors(2)
                .busyExecutors(1)
                .idleExecutors(1)
                .build();

        assertEquals(
                2,
                node.numExecutors()
        );

        assertFalse(node.offline());
    }
}