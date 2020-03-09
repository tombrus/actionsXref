package com.tombrus.xref;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class GitTest {
    @Test
    void downloadPartly() throws IOException {
        downloadTest("James121212/GBA4iOS", null, 0);
        downloadTest("JonathanMurre/webthing-arduino", "pythonapp.yml", 1);
        downloadTest("Kadantte/HandBrake", "linux.yml", 4);
        downloadTest("L1MeN9Yu/Senna", "CI.yml", 1);
        downloadTest("LanderMalta/popcorn-app-1", null, 0);
        downloadTest("Larrymagic13/angular-cli", "lock-closed.yml", 1);
        downloadTest("Lavanyabollini/json-server", "main.yaml", 1);
        downloadTest("LennaCooper/javascript-fix-the-scope-lab-bootcamp-prep-000", null, 0);
        downloadTest("ModelingValueGroup/generic-info", "check.yaml", 1);
    }

    private void downloadTest(String repo, String oneFile, int numFiles) {
        System.err.println();
        System.err.println("#### " + repo);
        List<String>       log           = new ArrayList<>();
        List<WorkFlowFile> workFlowFiles = GitProcedures.downloadWorkFlows(repo, log);

        System.err.println("## Files: " + workFlowFiles.stream().map(wf -> wf.fileName).collect(Collectors.joining(", ")));
        //        System.err.println("Log:");
        //        log.forEach(l -> System.err.println("    " + l));

        assertEquals(numFiles, (long) workFlowFiles.size());
        Optional<WorkFlowFile> oneYamlOpt = workFlowFiles.stream().filter(wf -> wf.fileName.equals(oneFile)).findFirst();
        if (oneFile == null) {
            assertTrue(oneYamlOpt.isEmpty());
        } else {
            assertTrue(oneYamlOpt.isPresent());
            WorkFlowFile oneYaml = oneYamlOpt.get();
            assertEquals(oneFile, oneYaml.fileName);
            System.err.println("## on   : " + oneYaml.yaml.get("on"));

            //            System.err.println("Yaml:");
            //            System.err.println(U.asYaml(oneYaml.yaml));
        }
    }
}