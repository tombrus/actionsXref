package com.tombrus.xref;

import static java.nio.charset.StandardCharsets.*;
import static java.time.LocalDateTime.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.esotericsoftware.yamlbeans.YamlConfig;
import com.esotericsoftware.yamlbeans.YamlConfig.WriteClassName;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.esotericsoftware.yamlbeans.YamlWriter;

@SuppressWarnings("unused")
public class U {
    public static final  String GITHUB_BASE_URL         = "https://github.com/%s";
    public static final  String GITHUB_ONE_WORKFLOW_URL = "https://github.com/%s/tree/master/.github/workflows/%s";
    public static final  String GITHUB_REPO_URL         = "https://github.com/%s.git";
    public static final  String GITHUB_ACTIONS_URL      = "https://github.com/%s/actions";
    public static final  String GITHUB_API_URL          = "https://api.github.com/repositories?since=%d";
    //
    public static final  int    MAX_REPO_ID             = 300_000_000;
    public static final  int    TRANCHE_SIZE            = 100;
    static final         String WORKFLOW_SUB_PATH       = ".github/workflows";
    private static final String SERIALIZATION_SEP       = "~";

    public static boolean inRange(int minInclusive, int x, int maxExclusive) {
        return minInclusive <= x && x < maxExclusive;
    }

    public static String getContents(HttpURLConnection con) throws IOException {
        StringBuilder sb       = new StringBuilder();
        String        encoding = con.getContentEncoding();
        try (InputStream in = con.getInputStream()) {
            try (Scanner sc = new Scanner(in, encoding == null ? "UTF-8" : encoding)) {
                while (sc.hasNext()) {
                    sb.append(sc.nextLine());
                }
            }
        }
        return sb.toString();
    }

    public static LocalDateTime readableDateTime(long reset) {
        return ofEpochSecond(reset, 0, ZoneOffset.UTC);
    }

    public static void deleteDir(Path toDelete) {
        try {
            Files.walk(toDelete)
                    .sorted(Comparator.reverseOrder())
                    .forEach(U::deleteOrError);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteOrError(Path p) {
        try {
            Files.delete(p);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public static void copyOrError(Path p, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.copy(p, target);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public static String stringFromYaml(Object o) {
        try (Writer sw = new StringWriter()) {
            YamlConfig c = new YamlConfig();
            c.writeConfig.setWriteClassname(WriteClassName.NEVER);
            YamlWriter yamlWriter = new YamlWriter(sw, c);
            yamlWriter.write(o);
            yamlWriter.close();
            return sw.toString();
        } catch (Throwable t) {
            throw new Error("could not serialize to yaml", t);
        }
    }

    public static Map<String, ?> yamlFromString(String s) {
        try {
            YamlReader yamlReader = new YamlReader(new StringReader(s.replace('\t', ' ')));
            Object     object     = yamlReader.read();
            yamlReader.close();
            if (!(object instanceof Map)) {
                throw new Error("YamlReader did not produce a Map<>" + s);
            }
            //noinspection unchecked
            return (Map<String, ?>) object;
        } catch (Throwable t) {
            throw new Error("could not deserialize from yaml", t);
        }
    }

    public static String serialize(List<WorkFlowFile> workFlows) {
        StringBuilder b = new StringBuilder();
        b.append(workFlows.size()).append(SERIALIZATION_SEP);
        workFlows.forEach(wf -> {
            b.append(wf.fileName).append(SERIALIZATION_SEP);
            b.append(compress(U.stringFromYaml(wf.yaml))).append(SERIALIZATION_SEP);
        });
        return b.toString();
    }

    public static List<WorkFlowFile> deserialize(String s, String repo) {
        List<WorkFlowFile> workFlows = new ArrayList<>();
        String[]           parts     = s.split(SERIALIZATION_SEP);
        int                expected  = Integer.parseUnsignedInt(parts[0]);
        for (int i = 0; i < expected * 2; i += 2) {
            String fileName = parts[i + 1];
            String yamlText = decompress(parts[i + 2]);
            try {
                workFlows.add(new WorkFlowFile(fileName, yamlFromString(yamlText), repo));
            } catch (Error e) {
                System.err.print("error parsing yaml from " + repo + " / " + fileName);
                for (Throwable t = e; t != null; t = t.getCause()) {
                    System.err.print(": " + t.getMessage());
                }
                System.err.println(yamlText);
            }
        }
        return workFlows;
    }

    public static String compress(String str) {
        if (str != null && str.length() != 0) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
                    //System.err.println(">>>>>"+ Arrays.toString(str.getBytes(UTF_8)));
                    gzip.write(str.getBytes(UTF_8));
                }
                //System.err.println(">>>>>" + Arrays.toString(bos.toByteArray()));
                str = Base64.getEncoder().encodeToString(bos.toByteArray());
                //System.err.println(">>>>>" + str);
            } catch (IOException e) {
                throw new Error("could not compress", e);
            }
        }
        return str;
    }

    public static String decompress(String str) {
        if (str != null && str.length() != 0) {
            //            System.err.println("<<<<<" + str);
            //            System.err.println("<<<<<" + Arrays.toString(Base64.getDecoder().decode(str)));
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(str)))) {
                StringBuilder b   = new StringBuilder();
                byte[]        buf = new byte[102400];
                int           n;
                while ((n = gis.read(buf)) != -1) {
                    b.append(new String(buf, 0, n, UTF_8));
                }
                str = b.toString();
                //                System.err.println("<<<<<" + str);
            } catch (IOException e) {
                throw new Error("could not decompress", e);
            }
        }
        return str;
    }
}
