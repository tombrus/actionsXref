package com.tombrus.xref;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Git {
    private final Path         dir;
    private final List<String> log;

    public Git(Path dir, List<String> log) {
        this.dir = dir;
        this.log = log;
    }

    public void run(String... args) throws IOException, InterruptedException {
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
