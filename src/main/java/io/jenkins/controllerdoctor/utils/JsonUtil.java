package io.jenkins.controllerdoctor.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jenkins.controllerdoctor.client.JenkinsClientException;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Centralized JSON utility for Jenkins Controller Doctor.
 *
 * <p>Keeping JSON operations in one class prevents Jackson configuration
 * from being duplicated across the HTTP client, model parsing and report
 * generation code.</p>
 */
public final class JsonUtil {

    private final ObjectMapper objectMapper;

    /**
     * Creates a JSON utility using the application's default Jackson
     * configuration.
     */
    public JsonUtil() {
        this(createDefaultMapper());
    }

    /**
     * Creates a JSON utility using a supplied mapper.
     *
     * @param objectMapper Jackson mapper
     */
    public JsonUtil(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
    }

    /**
     * Parses JSON into a Jackson tree.
     *
     * @param json JSON document
     * @return parsed JSON tree
     * @throws JenkinsClientException when parsing fails
     */
    public JsonNode readTree(String json)
            throws JenkinsClientException {

        if (json == null || json.isBlank()) {
            throw new JenkinsClientException(
                    JenkinsClientException.Reason.PARSING,
                    "Jenkins returned an empty JSON response"
            );
        }

        try {
            return objectMapper.readTree(json);

        } catch (JsonProcessingException exception) {
            throw new JenkinsClientException(
                    JenkinsClientException.Reason.PARSING,
                    "Jenkins returned invalid JSON",
                    exception
            );
        }
    }

    /**
     * Parses JSON into a strongly typed Java object.
     *
     * @param json JSON document
     * @param type target type
     * @param <T> target type
     * @return parsed object
     * @throws JenkinsClientException when parsing fails
     */
    public <T> T read(
            String json,
            Class<T> type
    ) throws JenkinsClientException {

        Objects.requireNonNull(type, "type must not be null");

        if (json == null || json.isBlank()) {
            throw new JenkinsClientException(
                    JenkinsClientException.Reason.PARSING,
                    "Jenkins returned an empty JSON response"
            );
        }

        try {
            return objectMapper.readValue(json, type);

        } catch (JsonProcessingException exception) {
            throw new JenkinsClientException(
                    JenkinsClientException.Reason.PARSING,
                    "Unable to parse Jenkins JSON response",
                    exception
            );
        }
    }

    /**
     * Serializes an object to compact JSON.
     *
     * @param value object to serialize
     * @return JSON string
     */
    public String write(Object value) {
        Objects.requireNonNull(value, "value must not be null");

        try {
            return objectMapper.writeValueAsString(value);

        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Unable to serialize object as JSON",
                    exception
            );
        }
    }

    /**
     * Serializes an object to human-readable JSON.
     *
     * @param value object to serialize
     * @return pretty JSON string
     */
    public String writePretty(Object value) {
        Objects.requireNonNull(value, "value must not be null");

        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(value);

        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Unable to serialize object as JSON",
                    exception
            );
        }
    }

    /**
     * Creates an empty JSON object.
     *
     * @return object node
     */
    public ObjectNode objectNode() {
        return objectMapper.createObjectNode();
    }

    /**
     * Creates an empty JSON array.
     *
     * @return array node
     */
    public ArrayNode arrayNode() {
        return objectMapper.createArrayNode();
    }

    /**
     * Returns the configured Jackson mapper.
     *
     * <p>The mapper is shared so all serialization follows the same
     * configuration.</p>
     *
     * @return object mapper
     */
    public ObjectMapper mapper() {
        return objectMapper;
    }

    /**
     * Returns a text field from a JSON object.
     *
     * @param object JSON object
     * @param field field name
     * @return field value or {@code null}
     */
    public String text(
            JsonNode object,
            String field
    ) {
        if (object == null || field == null) {
            return null;
        }

        JsonNode value = object.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    /**
     * Returns a boolean field from a JSON object.
     *
     * @param object JSON object
     * @param field field name
     * @param defaultValue value returned when the field is absent
     * @return boolean value
     */
    public boolean booleanValue(
            JsonNode object,
            String field,
            boolean defaultValue
    ) {
        if (object == null || field == null) {
            return defaultValue;
        }

        JsonNode value = object.get(field);

        if (value == null || value.isNull()) {
            return defaultValue;
        }

        return value.asBoolean(defaultValue);
    }

    /**
     * Returns an integer field from a JSON object.
     *
     * @param object JSON object
     * @param field field name
     * @param defaultValue value returned when the field is absent
     * @return integer value
     */
    public int intValue(
            JsonNode object,
            String field,
            int defaultValue
    ) {
        if (object == null || field == null) {
            return defaultValue;
        }

        JsonNode value = object.get(field);

        if (value == null || value.isNull()) {
            return defaultValue;
        }

        return value.asInt(defaultValue);
    }

    /**
     * Returns a long field from a JSON object.
     *
     * @param object JSON object
     * @param field field name
     * @param defaultValue value returned when the field is absent
     * @return long value
     */
    public long longValue(
            JsonNode object,
            String field,
            long defaultValue
    ) {
        if (object == null || field == null) {
            return defaultValue;
        }

        JsonNode value = object.get(field);

        if (value == null || value.isNull()) {
            return defaultValue;
        }

        return value.asLong(defaultValue);
    }

    /**
     * Returns the configured Jackson mapper with the project's defaults.
     */
    private static ObjectMapper createDefaultMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );

        mapper.configure(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                false
        );

        return mapper;
    }
}