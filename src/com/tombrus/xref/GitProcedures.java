package com.tombrus.xref;

import static java.nio.file.StandardOpenOption.*;
import static java.util.Collections.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GitProcedures {
    private static final String YAML_FILENAME_PATTERN = ".*\\.ya?ml";

    public static List<WorkFlowFile> downloadWorkFlows(String repo, List<String> log) {
        try {
            Path tmpDir = Files.createTempDirectory("github-download");
            try {
                Git git = new Git(tmpDir, log);
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

    public static void pushChanges() {
        List<String> log = new ArrayList<>();
        try {
            Git git = new Git(Paths.get(","), log);
            git.run("status");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            log.forEach(l -> System.err.println(">>> " + l));
        }
    }

    private static boolean logContains(List<String> log, @SuppressWarnings("SameParameterValue") String pattern) {
        return log.stream().anyMatch(l -> l.matches(pattern));
    }
}
