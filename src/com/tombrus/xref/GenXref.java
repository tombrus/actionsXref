package com.tombrus.xref;

import static java.util.Arrays.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

public class GenXref {
    private static final String ON_XREF_MARKER     = "##on-xref##";
    private static final String USES_XREF_MARKER   = "##uses-xref##";
    private static final String RUNSON_XREF_MARKER = "##runson-xref##";

    private static final List<String> TEMPLATE = asList(
            "<!doctype html>",
            "<html>",
            "<head>",
            "<meta charset=\"utf-8\">",
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
            "<title>GitHub Actions cross ref</title>",
            "<link href=\"https://www.jqueryscript.net/css/jquerysctipttop.css\" rel=\"stylesheet\" type=\"text/css\">",
            "<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css\">",
            "<style>",
            ".ghlink {",
            "    font-family        : FontAwesome;",
            "    color              : #50a050;",
            "}",
            ".file-list, .file-list ul{",
            "    list-style-type    : none;",
            "    font-size          : 1em;",
            "    line-height        : 1.6em;",
            "    padding-left       : 18px;",
            "    border-left        : 1px dotted #aaa;",
            "}",
            ".file-list li {",
            "    position           : relative;",
            "    padding-left       : 25px;",
            "}",
            ".file-list li a {",
            "    text-decoration    : none;",
            "    color              : #444;",
            "}",
            ".file-list li a:before{",
            "    display            : block;",
            "    content            : \" \";",
            "    width              : 10px;",
            "    height             : 1px;",
            "    position           : absolute;",
            "    border-bottom      : 1px dotted #aaa;",
            "    top                : .6em;",
            "    left               : -14px;",
            "}",
            ".file-list li:before{",
            "    list-style-type    : none;",
            "    font-family        : FontAwesome;",
            "    display            : block;",
            "    content            : '\\f0f6';",
            "    position           : absolute;",
            "    top                : 0px;",
            "    left               : 0px;",
            "    width              : 20px;",
            "    height             : 20px;",
            "    font-size          : 1.3em;",
            "    color              : #555;",
            "}",
            ".file-list .folder-root{",
            "    list-style-type    : none;",
            "}",
            ".file-list .folder-root a{",
            "    text-decoration    : none;",
            "}",
            ".file-list .folder-root:before{",
            "    color              : #FFD04E;",
            "    content            : \"\\f07b\";",
            "}",
            ".file-list .folder-root.open:before{",
            "    content            : \"\\f07c\";",
            "}",
            "li.folder-root ul{",
            "    transition         : all .3s ease-in-out;",
            "    overflow           : hidden;",
            "}",
            "li.folder-root.closed>ul{",
            "   opacity             : 0;",
            "   max-height          : 0px;",
            "}",
            "li.folder-root.open>ul{",
            "   opacity             : 1;",
            "   display             : block;",
            "}",
            "body {",
            "   font-family         : 'Roboto',Arial, Helvetica, sans-serif;",
            "   background-color    : #e0e0e0;",
            "}",
            ".container {",
            "   margin              : 20px auto;",
            "   max-width           : 728px;",
            "}",
            "</style>",
            "</head>",
            "",
            "<body>",
            "",
            "<div class=\"container\">",
            "  <h1>GitHub Actions XRef:</h1>",
            "  <ul class=\"file-tree\">",
            ON_XREF_MARKER,
            USES_XREF_MARKER,
            RUNSON_XREF_MARKER,
            "  </ul>",
            "</div>",
            "<script src=\"https://code.jquery.com/jquery-1.12.4.min.js\"></script> ",
            "<script>",
            "(function($){",
            "    $.fn.filetree = function(method){",
            "       ",
            "        var settings = { // settings to expose",
            "            animationSpeed      : 'fast',            ",
            "            collapsed           : true,",
            "            console             : false",
            "        }",
            "        var methods = {",
            "            init : function(options){",
            "                // Get standard settings and merge with passed in values",
            "                var options = $.extend(settings, options); ",
            "                // Do this for every file tree found in the document",
            "                return this.each(function(){",
            "                    var $fileList = $(this);",
            "                    $fileList",
            "                        .addClass('file-list')",
            "                        .find('li')",
            "                        .has('ul') // Any li that has a list inside is a folder root",
            "                        .addClass('folder-root closed')",
            "                        .on('click', 'a[href=\"#\"]', function(e){ // Add a click override for the folder root links",
            "                            e.preventDefault();",
            "                            $(this).parent().toggleClass('closed').toggleClass('open');",
            "                            return false;",
            "                        })",
            "                    $(this).find('.initopen').addClass('open').removeClass('closed').removeClass('initopen');",
            "                });",
            "            }",
            "        }",
            "        if (typeof method === 'object' || !method){",
            "            return methods.init.apply(this, arguments);",
            "        } else {",
            "            $.on( \"error\", function(){",
            "                console.log(method + \" does not exist in the file exploerer plugin\");",
            "            } );",
            "        }  ",
            "    }",
            "}(jQuery));",
            "</script> ",
            "<script>",
            "$(document).ready(function() {",
            "    $(\".file-tree\").filetree();",
            "});",
            "</script>",
            "</body>",
            "</html>",
            "\n");

    private final Xref xref;
    private final Path out;

    public GenXref(Xref crossref_on, Path out) {
        this.xref = crossref_on;
        this.out = out;
    }

    public void generate() {
        try {
            System.err.println("~~~generating page...");
            Map<String, Set<WorkFlowFile>> on_xref     = gatherOnXref();
            Map<String, Set<WorkFlowFile>> uses_xref   = gatherUsesXref();
            Map<String, Set<WorkFlowFile>> runson_xref = gatherRunsOnXref();

            Map<String, Predicate<String>> usesSub = new HashMap<>();
            usesSub.put("1_action/", s -> s.matches("^actions/.*$"));
            usesSub.put("2_./", s -> s.matches("^\\./.*$"));
            usesSub.put("3_rest", s -> s.matches("^(?!(\\./|actions/)).*$"));

            Map<String, Predicate<String>> runsonSub = new HashMap<>();
            runsonSub.put("1_macos", s -> s.matches("^mac[oO][sS]-.*$"));
            runsonSub.put("2_ubuntu", s -> s.matches("^ubuntu-.*$"));
            runsonSub.put("3_windows", s -> s.matches("^windows-.*$"));
            runsonSub.put("4_matrix", s -> s.matches("^\\$\\{\\{ *matrix\\..*$"));
            runsonSub.put("5_rest", s -> s.matches("^(?!(\\$\\{\\{ *matrix\\.|windows-|mac[oO][sS]-|ubuntu-)).*$"));

            List<String> lines = TEMPLATE
                    .stream()
                    .flatMap(l -> l.equals(ON_XREF_MARKER) ? generateTree("on:", true, on_xref) : Stream.of(l))
                    .flatMap(l -> l.equals(USES_XREF_MARKER) ? generateTreeInTree("uses:", uses_xref, usesSub) : Stream.of(l))
                    .flatMap(l -> l.equals(RUNSON_XREF_MARKER) ? generateTreeInTree("runs-on:", runson_xref, runsonSub) : Stream.of(l))
                    .collect(Collectors.toList());

            Files.createDirectories(out.getParent());
            Files.write(out, lines);
            System.err.println("~~~generating page done: " + out.toAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<String, Set<WorkFlowFile>> gatherOnXref() {
        Map<String, Set<WorkFlowFile>> map = new HashMap<>();
        xref.getGotWfsQueue().forEach(r -> {
            List<WorkFlowFile> workFlows = r.getWorkFlows();
            workFlows.forEach(wf -> {
                Object on = wf.yaml.get("on");
                if (on instanceof List<?>) {
                    //noinspection unchecked
                    ((List<String>) on).forEach(s -> map.compute(s, (k, set) -> setMarker(wf, set)));
                } else if (on instanceof Map<?, ?>) {
                    //noinspection unchecked
                    ((Map<String, ?>) on).keySet().forEach(s -> map.compute(s, (k, set) -> setMarker(wf, set)));
                } else if (on instanceof String) {
                    map.compute((String) on, (k, set) -> setMarker(wf, set));
                }
            });
        });
        return map;
    }

    private Map<String, Set<WorkFlowFile>> gatherUsesXref() {
        Map<String, Set<WorkFlowFile>> map = new HashMap<>();
        xref.getGotWfsQueue().forEach(r -> {
            List<WorkFlowFile> workFlows = r.getWorkFlows();
            workFlows.forEach(wf -> {
                Set<String> uses = get(wf.yaml, asList("jobs", "*", "steps", "*", "uses")).map(u -> u.replaceAll("@.*", "")).collect(Collectors.toSet());
                uses.forEach(u -> map.compute(u, (k, set) -> setMarker(wf, set)));
            });
        });
        return map;
    }

    private Map<String, Set<WorkFlowFile>> gatherRunsOnXref() {
        Map<String, Set<WorkFlowFile>> map = new HashMap<>();
        xref.getGotWfsQueue().forEach(r -> {
            List<WorkFlowFile> workFlows = r.getWorkFlows();
            workFlows.forEach(wf -> {
                Set<String> uses = get(wf.yaml, asList("jobs", "*", "runs-on")).collect(Collectors.toSet());
                uses.forEach(u -> map.compute(u, (k, set) -> setMarker(wf, set)));
            });
        });
        return map;
    }

    private Stream<String> get(Object yaml, List<String> tags) {
        if (tags.isEmpty()) {
            if (yaml instanceof String) {
                return Stream.of((String) yaml);
            } else {
                return Stream.empty();
            }
        } else {
            String       tag  = tags.get(0);
            List<String> rest = tags.subList(1, tags.size());
            if (yaml instanceof List<?>) {
                if (tag.equals("*")) {
                    List<?> yamlList = (List<?>) yaml;
                    return yamlList.stream().flatMap(oo -> get(oo, rest));
                } else {
                    throw new Error("can not select from List");
                }
            } else if (yaml instanceof Map<?, ?>) {
                //noinspection unchecked
                Map<String, ?> yamlMap = (Map<String, ?>) yaml;
                return yamlMap.keySet().stream().filter(k -> tag.equals("*") || k.matches(tag)).flatMap(k -> get(yamlMap.get(k), rest));
            } else if (yaml instanceof String) {
                return Stream.empty();
            }
        }
        return Stream.empty();
    }

    private Set<WorkFlowFile> setMarker(WorkFlowFile wf, Set<WorkFlowFile> set) {
        if (set == null) {
            return new HashSet<>(Collections.singleton(wf));
        } else {
            set.add(wf);
            return set;
        }
    }

    public Stream<String> generateTreeInTree(String name, Map<String, Set<WorkFlowFile>> xref, Map<String, Predicate<String>> selectorMap) {
        Builder<String> lines = Stream.builder();
        lines.add("  <li" + initOpen(true) + "><a href=\"#\">" + name + "</a>");
        lines.add("    <ul>");
        selectorMap
                .keySet()
                .stream()
                .sorted()
                .forEach(k -> generateTree(k.replaceAll("^[^_]*_", ""), false, xref.entrySet()
                        .stream()
                        .filter(e -> selectorMap.get(k).test(e.getKey()))
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue)))
                        .forEach(lines::add));
        lines.add("    </ul>");
        lines.add("  </li>");
        return lines.build();
    }

    public Stream<String> generateTree(String name, boolean open, Map<String, Set<WorkFlowFile>> xref) {
        Builder<String> lines = Stream.builder();
        lines.add("  <li" + initOpen(open) + "><a href=\"#\">" + name + "</a>");
        lines.add("    <ul>");
        //noinspection unchecked
        xref.entrySet()
                .stream()
                .sorted(Comparator.comparingInt(o -> o.getValue().size()))
                .flatMap(e -> Stream.of(
                        "      <li><a href=\"#\">" + e.getKey() + " [" + e.getValue().size() + "]</a>" + ifRef(e.getKey()),
                        "        <ul>",
                        e.getValue()
                                .stream()
                                .sorted()
                                .flatMap(wf -> Stream.of("<li><a href=\"" + String.format(U.GITHUB_ONE_WORKFLOW_URL, wf.repo, wf.fileName) + "\"><b>" + wf.repo + "</b>/" + wf.fileName + "</a> </li>")),
                        "        </ul>",
                        "      </li>"
                ))
                .flatMap(x -> x instanceof Stream ? (Stream<String>) x : Stream.of(x.toString()))
                .forEach(lines::add);
        lines.add("    </ul>");
        lines.add("  </li>");
        return lines.build();
    }

    private String initOpen(boolean open) {
        return open ? " class=\"initopen\"" : "";
    }

    private String ifRef(String key) {
        return key.contains("/") && !key.startsWith("./") ? " <a href=\"" + String.format(U.GITHUB_BASE_URL, key) + "\"><i class=\"ghlink fas fa-link\"></i></a>" : "";
    }

}
