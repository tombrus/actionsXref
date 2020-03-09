package com.tombrus.xref;

import static java.nio.file.StandardOpenOption.*;
import static java.util.Collections.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GitCommandRunner {
    private static final String YAML_FILENAME_PATTERN = ".*\\.ya?ml";

    public static List<WorkFlowFile> downloadWorkFlows(String repo, List<String> log) {
        try {
            Path tmpDir = Files.createTempDirectory("github-download");
            try {
                GitCommandRunner git = new GitCommandRunner(tmpDir, log);
                git.run("init");
                git.run("remote", "add", "--no-tags", "origin", "-f", String.format(U.GITHUB_REPO_URL, repo));
                if (!logContains(log, "^ *error: Could not fetch origin")) {
                    git.run("config", "core.sparseCheckout", "true");
                    Files.write(tmpDir.resolve(".git/info/sparse-checkout"), singletonList(U.WORKFLOW_SUB_PATH), APPEND, CREATE);
                    git.run("pull", "origin", "master");

                    Path absSub = tmpDir.resolve(U.WORKFLOW_SUB_PATH);
                    if (Files.isDirectory(absSub)) {
                        return Files.walk(absSub)
                                .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().matches(YAML_FILENAME_PATTERN))
                                .map(x -> new WorkFlowFile(x, repo))
                                .collect(Collectors.toList());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                U.deleteDir(tmpDir);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private final Path         dir;
    private final List<String> log;

    public GitCommandRunner(Path dir, List<String> log) {
        this.dir = dir;
        this.log = log;
    }

    private static boolean logContains(List<String> log, @SuppressWarnings("SameParameterValue") String pattern) {
        return log.stream().anyMatch(l -> l.matches(pattern));
    }

    private void run(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();

        log.add("cmd=" + command.stream().collect(Collectors.joining("' '", "'", "'")));
        try (BufferedReader is = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = is.readLine()) != null) {
                log.add("    " + line);
            }
        }
        int exit = process.waitFor();
        log.add("exit=" + exit);
        log.add("");
    }

}
