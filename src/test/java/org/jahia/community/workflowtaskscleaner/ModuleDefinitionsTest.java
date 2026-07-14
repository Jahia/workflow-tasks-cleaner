package org.jahia.community.workflowtaskscleaner;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S29 (G15 / D7) — DOC-CORRECTION: the module defines NO custom CND node types.
 *
 * <p><b>Stage-4 correction:</b> Stage 4 (G15) claimed no {@code .cnd} file exists at all. In fact a
 * {@code META-INF/definitions.cnd} IS shipped, but it declares ONLY namespace prefixes
 * ({@code jnt}, {@code jmix}) and defines ZERO node types ({@code [ ... ]} blocks). So the substance
 * of D7 holds — {@code jnt:workflowTask} (used by {@code WorkflowTaskCommand#fixTask}) is a core
 * Jahia type, not defined here; module state lives in the jBPM DB tables + core JCR. This test pins
 * the real content so a future edit adding a custom node type is caught.</p>
 */
public class ModuleDefinitionsTest {

    private static final String RESOURCE = "META-INF/definitions.cnd";

    private static String readCnd() throws Exception {
        try (InputStream in = ModuleDefinitionsTest.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    @Test
    public void definitionsCnd_declaresOnlyNamespacePrefixes_andNoNodeTypes() throws Exception {
        String cnd = readCnd();
        assertThat(cnd).as("definitions.cnd is shipped").isNotNull();

        for (String rawLine : cnd.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            // Only namespace declarations of the form: <prefix = 'uri'>
            assertThat(line)
                    .as("only namespace prefix declarations are allowed, found: %s", line)
                    .matches("<\\s*\\w+\\s*=\\s*'[^']*'\\s*>");
        }
    }

    @Test
    public void definitionsCnd_definesNoNodeTypeBlocks() throws Exception {
        String cnd = readCnd();
        assertThat(cnd).isNotNull();
        // A CND node-type definition starts with a '[name]' token; there must be none.
        assertThat(cnd).doesNotContain("[");
    }
}
