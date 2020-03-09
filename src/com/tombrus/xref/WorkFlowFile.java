package com.tombrus.xref;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;

public class WorkFlowFile implements Comparable<WorkFlowFile> {
    public final String         repo;
    public final String         fileName;
    public final Map<String, ?> yaml;

    public WorkFlowFile(Path path, String repo) {
        try {
            this.repo = repo;
            this.fileName = path.getFileName().toString();
            String yaml   = String.join("\n", Files.readAllLines(path)).replace('\t', ' ');
            Object object = new YamlReader(yaml).read();
            if (!(object instanceof Map)) {
                throw new Error("yaml file does not contain a Map<>: " + path);
            }
            //noinspection unchecked
            this.yaml = (Map<String, ?>) object;
        } catch (FileNotFoundException e) {
            throw new Error("yaml file not found: " + path, e);
        } catch (YamlException e) {
            throw new Error("yaml file unparseable: " + path, e);
        } catch (IOException e) {
            throw new Error("yaml file unreadable: " + path, e);
        }
    }

    public WorkFlowFile(String fileName, Map<String, ?> yaml, String repo) {
        this.repo = repo;
        this.yaml = yaml;
        this.fileName = fileName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WorkFlowFile that = (WorkFlowFile) o;
        return Objects.equals(fileName, that.fileName) &&
                Objects.equals(yaml, that.yaml);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, yaml);
    }

    @Override
    public int compareTo(WorkFlowFile o) {
        return !repo.equals(o.repo) ? repo.compareTo(o.repo) : fileName.compareTo(o.fileName);
    }
}
