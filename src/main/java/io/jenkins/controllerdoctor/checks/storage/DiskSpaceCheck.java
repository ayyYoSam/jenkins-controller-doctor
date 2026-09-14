package io.jenkins.controllerdoctor.checks.storage;

import io.jenkins.controllerdoctor.checks.Check;
import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.client.JenkinsClient;
import io.jenkins.controllerdoctor.client.JenkinsClientException;
import io.jenkins.controllerdoctor.model.JenkinsInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Checks the storage information exposed by the Jenkins controller.
 *
 * <p>Disk exhaustion is one of the most important infrastructure conditions
 * to detect because a controller with insufficient storage may experience
 * failed builds, corrupted workspaces, failed updates, inability to write
 * logs, or general instability.</p>
 *
 * <p>The check uses the Jenkins computer API through the client and evaluates
 * filesystem information returned by the controller. When Jenkins does not
 * expose sufficient filesystem information, the check reports an informational
 * result instead of guessing the controller's storage state.</p>
 */
public final class DiskSpaceCheck implements Check {

    public static final String ID = "storage.disk-space";

    public static final String NAME = "Disk space";

    /**
     * Warning threshold: less than 20% free space.
     */
    public static final double WARNING_FREE_PERCENT = 20.0;

    /**
     * Critical threshold: less than 10% free space.
     */
    public static final double CRITICAL_FREE_PERCENT = 10.0;

    /**
     * Warning threshold for absolute free space.
     */
    public static final long WARNING_FREE_BYTES =
            5L * 1024L * 1024L * 1024L;

    /**
     * Critical threshold for absolute free space.
     */
    public static final long CRITICAL_FREE_BYTES =
            1L * 1024L * 1024L * 1024L;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Executes the disk-space check.
     *
     * <p>The generic JenkinsInfo model may expose storage information through
     * metadata because different Jenkins API versions expose filesystem data
     * differently. This check therefore supports common numeric metadata keys
     * without coupling the model to a specific Jenkins API response shape.</p>
     *
     * @param client Jenkins client
     * @param jenkinsInfo controller information
     * @return disk-space check result
     */
    @Override
    public CheckResult execute(
            JenkinsClient client,
            JenkinsInfo jenkinsInfo
    ) {
        Objects.requireNonNull(client, "client must not be null");

        Instant startedAt = Instant.now();

        if (jenkinsInfo == null) {
            return buildUnavailableResult(
                    startedAt,
                    "Jenkins controller information was not available."
            );
        }

        StorageInfo storageInfo = extractStorageInfo(jenkinsInfo);

        if (!storageInfo.available()) {
            return buildUnavailableResult(
                    startedAt,
                    "Jenkins did not expose sufficient filesystem "
                            + "information to evaluate controller disk space."
            );
        }

        CheckStatus status = determineStatus(storageInfo);

        Instant completedAt = Instant.now();

        CheckResult.Builder result = CheckResult.builder()
                .checkId(ID)
                .checkName(NAME)
                .status(status)
                .summary(buildSummary(storageInfo, status))
                .details(buildDetails(storageInfo))
                .metadata(
                        "totalBytes",
                        storageInfo.totalBytes()
                )
                .metadata(
                        "freeBytes",
                        storageInfo.freeBytes()
                )
                .metadata(
                        "usedBytes",
                        storageInfo.usedBytes()
                )
                .metadata(
                        "freePercent",
                        round(storageInfo.freePercent())
                )
                .metadata(
                        "usedPercent",
                        round(storageInfo.usedPercent())
                )
                .metadata(
                        "warningFreePercent",
                        WARNING_FREE_PERCENT
                )
                .metadata(
                        "criticalFreePercent",
                        CRITICAL_FREE_PERCENT
                )
                .metadata(
                        "warningFreeBytes",
                        WARNING_FREE_BYTES
                )
                .metadata(
                        "criticalFreeBytes",
                        CRITICAL_FREE_BYTES
                )
                .startedAt(startedAt)
                .completedAt(completedAt)
                .duration(
                        Duration.between(startedAt, completedAt)
                );

        String recommendation =
                buildRecommendation(storageInfo, status);

        if (recommendation != null) {
            result.recommendation(recommendation);
        }

        return result.build();
    }

    private static CheckStatus determineStatus(
            StorageInfo storageInfo
    ) {
        if (storageInfo.freePercent() <= CRITICAL_FREE_PERCENT
                || storageInfo.freeBytes() <= CRITICAL_FREE_BYTES) {
            return CheckStatus.CRITICAL;
        }

        if (storageInfo.freePercent() <= WARNING_FREE_PERCENT
                || storageInfo.freeBytes() <= WARNING_FREE_BYTES) {
            return CheckStatus.WARNING;
        }

        return CheckStatus.PASS;
    }

    private static String buildSummary(
            StorageInfo storageInfo,
            CheckStatus status
    ) {
        return switch (status) {
            case PASS ->
                    String.format(
                            Locale.ROOT,
                            "Controller disk space is healthy (%.1f%% free)",
                            storageInfo.freePercent()
                    );

            case WARNING ->
                    String.format(
                            Locale.ROOT,
                            "Controller disk space is low (%.1f%% free)",
                            storageInfo.freePercent()
                    );

            case CRITICAL ->
                    String.format(
                            Locale.ROOT,
                            "Controller disk space is critically low (%.1f%% free)",
                            storageInfo.freePercent()
                    );

            case INFO ->
                    "Controller disk space information available";
        };
    }

    private static String buildDetails(
            StorageInfo storageInfo
    ) {
        return String.format(
                Locale.ROOT,
                "Total: %s, used: %s (%.1f%%), free: %s (%.1f%%).",
                formatBytes(storageInfo.totalBytes()),
                formatBytes(storageInfo.usedBytes()),
                storageInfo.usedPercent(),
                formatBytes(storageInfo.freeBytes()),
                storageInfo.freePercent()
        );
    }

    private static String buildRecommendation(
            StorageInfo storageInfo,
            CheckStatus status
    ) {
        if (status == CheckStatus.PASS) {
            return null;
        }

        if (status == CheckStatus.CRITICAL) {
            return "Free disk space on the Jenkins controller immediately. "
                    + "Review old build artifacts, logs, workspaces, caches, "
                    + "and other large files before storage exhaustion occurs.";
        }

        return "Monitor controller disk usage and consider cleaning old "
                + "build artifacts, logs, workspaces, or caches.";
    }

    /**
     * Extracts storage information from the generic JenkinsInfo metadata.
     *
     * <p>Supported metadata keys include total/free byte values as well as
     * common Jenkins filesystem naming conventions.</p>
     */
    private static StorageInfo extractStorageInfo(
            JenkinsInfo jenkinsInfo
    ) {
        Object totalValue = findMetadata(
                jenkinsInfo,
                "totalBytes",
                "totalSpace",
                "totalDiskSpace",
                "filesystemTotalBytes"
        );

        Object freeValue = findMetadata(
                jenkinsInfo,
                "freeBytes",
                "freeSpace",
                "freeDiskSpace",
                "filesystemFreeBytes"
        );

        Long totalBytes = toLong(totalValue);
        Long freeBytes = toLong(freeValue);

        if (totalBytes == null
                || freeBytes == null
                || totalBytes <= 0
                || freeBytes < 0
                || freeBytes > totalBytes) {
            return StorageInfo.unavailable();
        }

        long usedBytes = totalBytes - freeBytes;

        double freePercent =
                ((double) freeBytes / (double) totalBytes) * 100.0;

        double usedPercent =
                ((double) usedBytes / (double) totalBytes) * 100.0;

        return new StorageInfo(
                true,
                totalBytes,
                freeBytes,
                usedBytes,
                freePercent,
                usedPercent
        );
    }

    private static Object findMetadata(
            JenkinsInfo jenkinsInfo,
            String... keys
    ) {
        if (jenkinsInfo.metadata() == null) {
            return null;
        }

        for (String key : keys) {
            Object value = jenkinsInfo.metadata().get(key);

            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private static CheckResult buildUnavailableResult(
            Instant startedAt,
            String details
    ) {
        Instant completedAt = Instant.now();

        return CheckResult.builder()
                .checkId(ID)
                .checkName(NAME)
                .status(CheckStatus.INFO)
                .summary("Controller disk space could not be evaluated")
                .details(details)
                .recommendation(
                        "Ensure the Jenkins client exposes filesystem "
                                + "information before relying on this check."
                )
                .metadata("available", false)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .duration(
                        Duration.between(startedAt, completedAt)
                )
                .build();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB", "PiB"};

        int unitIndex = 0;

        while (value >= 1024.0
                && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }

        return String.format(
                Locale.ROOT,
                "%.2f %s",
                value,
                units[unitIndex]
        );
    }

    /**
     * Immutable storage information used internally by this check.
     */
    private record StorageInfo(
            boolean available,
            long totalBytes,
            long freeBytes,
            long usedBytes,
            double freePercent,
            double usedPercent
    ) {

        private static StorageInfo unavailable() {
            return new StorageInfo(
                    false,
                    0,
                    0,
                    0,
                    0.0,
                    0.0
            );
        }
    }
}