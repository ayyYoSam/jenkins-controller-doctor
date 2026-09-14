package io.jenkins.controllerdoctor;

import io.jenkins.controllerdoctor.cli.CommandLine;

public final class Main {

    private Main() {
        // Utility class.
    }

    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine();

        int exitCode = new picocli.CommandLine(commandLine)
                .execute(args);

        System.exit(exitCode);
    }
}