package com.tombrus.xref;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

public class YamlTest {
    @Test
    public void yamlTest0() {
        String org = "name: Deploy Theme\n" +
                "on:\n" +
                "  push:\t\n" +
                "    branches:\t\n" +
                "      - master\t\n" +
                "jobs:\n" +
                "  deploy:\n" +
                "    runs-on: ubuntu-18.04\n" +
                "    steps:\n" +
                "      - uses: actions/checkout@master\n" +
                "      - uses: TryGhost/action-deploy-theme@v1.2.0\n" +
                "        with:\n" +
                "          api-url: ${{ secrets.GHOST_ADMIN_API_URL }}\n" +
                "          api-key: ${{ secrets.GHOST_ADMIN_API_KEY }}\n" +
                "          theme-name: \"casper-master\"";
        Map<String, ?> yaml = U.yamlFromString(org);
    }

    @Test
    public void yamlTest1() {
        String         org  = "name: aap\non: burp\n";
        Map<String, ?> yaml = U.yamlFromString(org);
        String         s    = U.stringFromYaml(yaml);

        assertEquals(org, s);
        assertEquals("burp", yaml.get("on"));
    }

    @Test
    public void yamlTest2() {
        String str = "blablabla\nblablabla\nblablabla\nblablabla\nblablabla\nblablabla\nblablabla\nblablabla\nblablabla\n";
        String com = U.compress(str);
        String dec = U.decompress(com);

        assertNotEquals(str, com);
        assertEquals(str, dec);
    }

    @Test
    public void yamlTest3() {
        String repo = "my/repo";
        List<WorkFlowFile> org = IntStream.range(1, 5)
                .mapToObj(i -> new WorkFlowFile("aap" + i + ".yaml", U.yamlFromString("name: aap" + i + "\non: " + i + "burp\n"), repo))
                .collect(Collectors.toList());

        String             ser = U.serialize(org);
        List<WorkFlowFile> des = U.deserialize(ser, repo);

        assertEquals(org, des);
    }
}
