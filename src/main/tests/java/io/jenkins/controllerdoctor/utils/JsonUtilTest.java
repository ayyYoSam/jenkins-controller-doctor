package io.jenkins.controllerdoctor.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    private final JsonUtil json = new JsonUtil();

    @Test
    void shouldParseJsonTree() throws Exception {
        JsonNode node = json.readTree("""
                {
                  "name": "Jenkins",
                  "version": "2.516.1",
                  "secure": true
                }
                """);

        assertEquals("Jenkins", node.get("name").asText());
        assertEquals("2.516.1", node.get("version").asText());
        assertTrue(node.get("secure").asBoolean());
    }

    @Test
    void shouldDeserializeJson() throws Exception {
        Map<?, ?> value = json.read(
                """
                {
                  "name": "Jenkins",
                  "version": "2.516.1"
                }
                """,
                Map.class
        );

        assertEquals("Jenkins", value.get("name"));
        assertEquals("2.516.1", value.get("version"));
    }

    @Test
    void shouldSerializeObject() throws Exception {
        String output = json.write(
                Map.of(
                        "name", "Jenkins",
                        "version", "2.516.1"
                )
        );

        assertTrue(output.contains("\"name\""));
        assertTrue(output.contains("\"Jenkins\""));
        assertTrue(output.contains("\"2.516.1\""));
    }

    @Test
    void shouldSerializePrettyJson() throws Exception {
        String output = json.writePretty(
                Map.of(
                        "name", "Jenkins",
                        "version", "2.516.1"
                )
        );

        assertTrue(output.contains("\n"));
        assertTrue(output.contains("\"name\""));
    }

    @Test
    void shouldCreateObjectNode() {
        JsonNode node = json.objectNode();

        assertNotNull(node);
        assertTrue(node.isObject());
    }

    @Test
    void shouldCreateArrayNode() {
        JsonNode node = json.arrayNode();

        assertNotNull(node);
        assertTrue(node.isArray());
    }

    @Test
    void shouldExtractCommonJsonValues() {
        JsonNode node = json.objectNode();

        node.put("text", "Jenkins");
        node.put("enabled", true);
        node.put("count", 42);
        node.put("size", 123456789L);

        assertEquals(
                "Jenkins",
                json.text(node, "text")
        );

        assertTrue(
                json.booleanValue(node, "enabled", false)
        );

        assertEquals(
                42,
                json.intValue(node, "count", 0)
        );

        assertEquals(
                123456789L,
                json.longValue(node, "size", 0L)
        );
    }

    @Test
    void shouldReturnDefaultsForMissingValues() {
        JsonNode node = json.objectNode();

        assertNull(json.text(node, "missing"));

        assertFalse(
                json.booleanValue(node, "missing", false)
        );

        assertEquals(
                10,
                json.intValue(node, "missing", 10)
        );

        assertEquals(
                100L,
                json.longValue(node, "missing", 100L)
        );
    }
}