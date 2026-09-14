package io.jenkins.controllerdoctor.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.jenkins.controllerdoctor.model.JenkinsInfo;
import io.jenkins.controllerdoctor.model.NodeInfo;
import io.jenkins.controllerdoctor.model.PluginInfo;
import io.jenkins.controllerdoctor.model.QueueItem;
import io.jenkins.controllerdoctor.utils.HttpUtil;
import io.jenkins.controllerdoctor.utils.JsonUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP-based Jenkins client implementation.
 *
 * <p>This client communicates exclusively with the Jenkins Remote API
 * and does not modify controller state.</p>
 */
public final class JenkinsHttpClient implements JenkinsClient {

    private static final String JENKINS_API =
            "api/json";

    private static final String JENKINS_INFO_API =
            "api/json?tree="
                    + "displayName,"
                    + "fullName,"
                    + "description,"
                    + "url,"
                    + "version,"
                    + "useSecurity";

    private static final String PLUGINS_API =
            "pluginManager/api/json?tree="
                    + "plugins["
                    + "shortName,"
                    + "longName,"
                    + "version,"
                    + "requiredCore,"
                    + "enabled,"
                    + "active,"
                    + "bundled,"
                    + "hasUpdate,"
                    + "dependencies[shortName,version,optional]"
                    + "]";

    private static final String NODES_API =
            "computer/api/json?tree="
                    + "computer["
                    + "displayName,"
                    + "description,"
                    + "offline,"
                    + "temporarilyOffline,"
                    + "offlineCauseReason,"
                    + "numExecutors,"
                    + "executors[currentExecutable]"
                    + "]";

    private static final String QUEUE_API =
            "queue/api/json?tree="
                    + "items["
                    + "id,"
                    + "task[name],"
                    + "why,"
                    + "blocked,"
                    + "buildable,"
                    + "stuck,"
                    + "inQueueSince,"
                    + "stuckReason"
                    + "]";

    private final JenkinsConnection connection;
    private final HttpUtil httpUtil;
    private final JsonUtil jsonUtil;

    /**
     * Creates a Jenkins HTTP client using the default HTTP and JSON utilities.
     *
     * @param connection Jenkins connection configuration
     */
    public JenkinsHttpClient(JenkinsConnection connection) {
        this(
                connection,
                new HttpUtil(),
                new JsonUtil()
        );
    }

    /**
     * Creates a Jenkins HTTP client with an injectable HTTP utility.
     *
     * <p>This constructor is useful for testing and controlled integrations.</p>
     *
     * @param connection Jenkins connection configuration
     * @param httpUtil HTTP utility
     */
    public JenkinsHttpClient(
            JenkinsConnection connection,
            HttpUtil httpUtil
    ) {
        this(
                connection,
                httpUtil,
                new JsonUtil()
        );
    }

    /**
     * Creates a Jenkins HTTP client with injectable HTTP and JSON utilities.
     *
     * @param connection Jenkins connection configuration
     * @param httpUtil HTTP utility
     * @param jsonUtil JSON utility
     */
    public JenkinsHttpClient(
            JenkinsConnection connection,
            HttpUtil httpUtil,
            JsonUtil jsonUtil
    ) {
        this.connection = Objects.requireNonNull(
                connection,
                "connection must not be null"
        );

        this.httpUtil = Objects.requireNonNull(
                httpUtil,
                "httpUtil must not be null"
        );

        this.jsonUtil = Objects.requireNonNull(
                jsonUtil,
                "jsonUtil must not be null"
        );
    }

    /**
     * Returns the connection configuration.
     *
     * @return Jenkins connection
     */
    public JenkinsConnection connection() {
        return connection;
    }

    @Override
    public JenkinsInfo getJenkinsInfo()
            throws JenkinsClientException {

        HttpUtil.Response response = httpUtil.get(
                connection,
                JENKINS_INFO_API
        );

        JsonNode root = jsonUtil.readTree(response.body());

        JenkinsInfo.Builder builder = JenkinsInfo.builder()
                .displayName(jsonUtil.text(root, "displayName"))
                .fullName(jsonUtil.text(root, "fullName"))
                .description(jsonUtil.text(root, "description"))
                .url(jsonUtil.text(root, "url"))
                .version(resolveJenkinsVersion(root, response))
                .secure(
                        "https".equalsIgnoreCase(
                                connection.baseUri().getScheme()
                        )
                )
                .useSecurity(
                        jsonUtil.booleanValue(
                                root,
                                "useSecurity",
                                false
                        )
                );

        String serverVersion =
                response.jenkinsVersion().orElse(null);

        if (serverVersion != null && !serverVersion.isBlank()) {
            builder.metadata(
                    "serverHeaderVersion",
                    serverVersion
            );
        }

        builder.metadata(
                "securityEnabled",
                jsonUtil.booleanValue(
                        root,
                        "useSecurity",
                        false
                )
        );

        builder.metadata(
                "csrfProtectionEnabled",
                null
        );

        builder.metadata(
                "anonymousReadAccess",
                null
        );

        builder.metadata(
                "anonymousOverallAccess",
                null
        );

        return builder.build();
    }

    @Override
    public List<PluginInfo> getPlugins()
            throws JenkinsClientException {

        HttpUtil.Response response = httpUtil.get(
                connection,
                PLUGINS_API
        );

        JsonNode root = jsonUtil.readTree(response.body());

        JsonNode pluginsNode = root.get("plugins");

        if (pluginsNode == null || !pluginsNode.isArray()) {
            return Collections.emptyList();
        }

        List<PluginInfo> plugins = new ArrayList<>();

        for (JsonNode pluginNode : pluginsNode) {
            plugins.add(parsePlugin(pluginNode));
        }

        return Collections.unmodifiableList(plugins);
    }

    @Override
    public List<NodeInfo> getNodes()
            throws JenkinsClientException {

        HttpUtil.Response response = httpUtil.get(
                connection,
                NODES_API
        );

        JsonNode root = jsonUtil.readTree(response.body());

        JsonNode nodesNode = root.get("computer");

        if (nodesNode == null || !nodesNode.isArray()) {
            return Collections.emptyList();
        }

        List<NodeInfo> nodes = new ArrayList<>();

        for (JsonNode node : nodesNode) {
            nodes.add(parseNode(node));
        }

        return Collections.unmodifiableList(nodes);
    }

    @Override
    public List<QueueItem> getQueueItems()
            throws JenkinsClientException {

        HttpUtil.Response response = httpUtil.get(
                connection,
                QUEUE_API
        );

        JsonNode root = jsonUtil.readTree(response.body());

        JsonNode itemsNode = root.get("items");

        if (itemsNode == null || !itemsNode.isArray()) {
            return Collections.emptyList();
        }

        List<QueueItem> items = new ArrayList<>();

        for (JsonNode itemNode : itemsNode) {
            items.add(parseQueueItem(itemNode));
        }

        return Collections.unmodifiableList(items);
    }

    @Override
    public void testConnection()
            throws JenkinsClientException {

        httpUtil.get(
                connection,
                JENKINS_API
        );
    }

    private PluginInfo parsePlugin(JsonNode node) {
        PluginInfo.Builder builder = PluginInfo.builder()
                .shortName(
                        jsonUtil.text(
                                node,
                                "shortName"
                        )
                )
                .longName(
                        jsonUtil.text(
                                node,
                                "longName"
                        )
                )
                .version(
                        jsonUtil.text(
                                node,
                                "version"
                        )
                )
                .requiredCore(
                        jsonUtil.text(
                                node,
                                "requiredCore"
                        )
                )
                .enabled(
                        jsonUtil.booleanValue(
                                node,
                                "enabled",
                                true
                        )
                )
                .active(
                        jsonUtil.booleanValue(
                                node,
                                "active",
                                false
                        )
                )
                .bundled(
                        jsonUtil.booleanValue(
                                node,
                                "bundled",
                                false
                        )
                )
                .hasUpdate(
                        jsonUtil.booleanValue(
                                node,
                                "hasUpdate",
                                false
                        )
                )
                .latestVersion(
                        jsonUtil.text(
                                node,
                                "latestVersion"
                        )
                );

        JsonNode dependenciesNode =
                node.get("dependencies");

        if (dependenciesNode != null
                && dependenciesNode.isArray()) {

            for (JsonNode dependencyNode : dependenciesNode) {

                String shortName =
                        jsonUtil.text(
                                dependencyNode,
                                "shortName"
                        );

                String version =
                        jsonUtil.text(
                                dependencyNode,
                                "version"
                        );

                boolean optional =
                        jsonUtil.booleanValue(
                                dependencyNode,
                                "optional",
                                false
                        );

                if (shortName == null
                        || shortName.isBlank()) {
                    continue;
                }

                String dependency = shortName;

                if (version != null
                        && !version.isBlank()) {
                    dependency += ":" + version;
                }

                if (optional) {
                    dependency += ";resolution:=optional";
                }

                builder.dependency(dependency);
            }
        }

        return builder.build();
    }

    private NodeInfo parseNode(JsonNode node) {
        int executors =
                jsonUtil.intValue(
                        node,
                        "numExecutors",
                        0
                );

        JsonNode executorsNode =
                node.get("executors");

        int busyExecutors = 0;

        if (executorsNode != null
                && executorsNode.isArray()) {

            for (JsonNode executorNode : executorsNode) {

                JsonNode currentExecutable =
                        executorNode.get(
                                "currentExecutable"
                        );

                if (currentExecutable != null
                        && !currentExecutable.isNull()) {
                    busyExecutors++;
                }
            }
        }

        int idleExecutors =
                Math.max(
                        0,
                        executors - busyExecutors
                );

        NodeInfo.Builder builder = NodeInfo.builder()
                .displayName(
                        jsonUtil.text(
                                node,
                                "displayName"
                        )
                )
                .description(
                        jsonUtil.text(
                                node,
                                "description"
                        )
                )
                .offlineCause(
                        jsonUtil.text(
                                node,
                                "offlineCauseReason"
                        )
                )
                .offline(
                        jsonUtil.booleanValue(
                                node,
                                "offline",
                                false
                        )
                )
                .temporarilyOffline(
                        jsonUtil.booleanValue(
                                node,
                                "temporarilyOffline",
                                false
                        )
                )
                .numExecutors(executors)
                .busyExecutors(busyExecutors)
                .idleExecutors(executors - busyExecutors);

        extractMetadata(node).forEach(builder::metadata);

        return builder.build();
    }

    private QueueItem parseQueueItem(JsonNode node) {
        JsonNode taskNode =
                node.get("task");

        String taskName = null;

        if (taskNode != null
                && taskNode.isObject()) {

            taskName =
                    jsonUtil.text(
                            taskNode,
                            "name"
                    );
        }

        return QueueItem.builder()
                .id(
                        jsonUtil.longValue(
                                node,
                                "id",
                                -1L
                        )
                )
                .taskName(taskName)
                .why(
                        jsonUtil.text(
                                node,
                                "why"
                        )
                )
                .stuckReason(
                        jsonUtil.text(
                                node,
                                "stuckReason"
                        )
                )
                .blocked(
                        jsonUtil.booleanValue(
                                node,
                                "blocked",
                                false
                        )
                )
                .buildable(
                        jsonUtil.booleanValue(
                                node,
                                "buildable",
                                false
                        )
                )
                .stuck(
                        jsonUtil.booleanValue(
                                node,
                                "stuck",
                                false
                        )
                )
                .inQueueSince(
                        jsonUtil.longValue(
                                node,
                                "inQueueSince",
                                0L
                        )
                )
                .metadata(
                        extractMetadata(node)
                )
                .build();
    }

    private String resolveJenkinsVersion(
            JsonNode root,
            HttpUtil.Response response
    ) {
        String version =
                jsonUtil.text(
                        root,
                        "version"
                );

        if (version != null
                && !version.isBlank()) {
            return version;
        }

        return response.jenkinsVersion()
                .orElse(null);
    }

    private Map<String, Object> extractMetadata(
            JsonNode node
    ) {
        if (node == null
                || !node.isObject()) {
            return Collections.emptyMap();
        }

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields =
                node.fields();

        while (fields.hasNext()) {

            Map.Entry<String, JsonNode> field =
                    fields.next();

            String name = field.getKey();

            if ("displayName".equals(name)
                    || "description".equals(name)
                    || "offlineCauseReason".equals(name)
                    || "executors".equals(name)
                    || "task".equals(name)
                    || "why".equals(name)
                    || "stuckReason".equals(name)) {
                continue;
            }

            metadata.put(
                    name,
                    convertJsonValue(
                            field.getValue()
                    )
            );
        }

        return Collections.unmodifiableMap(
                metadata
        );
    }

    private Object convertJsonValue(
            JsonNode node
    ) {
        if (node == null
                || node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
            return node.asText();
        }

        if (node.isBoolean()) {
            return node.asBoolean();
        }

        if (node.isInt()) {
            return node.asInt();
        }

        if (node.isLong()) {
            return node.asLong();
        }

        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }

        if (node.isArray()) {
            List<Object> values =
                    new ArrayList<>();

            for (JsonNode value : node) {
                values.add(
                        convertJsonValue(value)
                );
            }

            return Collections.unmodifiableList(
                    values
            );
        }

        if (node.isObject()) {
            Map<String, Object> values =
                    new LinkedHashMap<>();

            Iterator<Map.Entry<String, JsonNode>> fields =
                    node.fields();

            while (fields.hasNext()) {

                Map.Entry<String, JsonNode> field =
                        fields.next();

                values.put(
                        field.getKey(),
                        convertJsonValue(
                                field.getValue()
                        )
                );
            }

            return Collections.unmodifiableMap(
                    values
            );
        }

        return node.toString();
    }
}