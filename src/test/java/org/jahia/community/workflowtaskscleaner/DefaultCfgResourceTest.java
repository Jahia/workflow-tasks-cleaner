package org.jahia.community.workflowtaskscleaner;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S28 (G14 / F1, F9, U11) — the shipped default {@code .cfg} is non-overridable and seeds 2:30 AM.
 *
 * <p>Guards the Jahia module-extender convention (project CLAUDE.md): the file MUST start with the
 * exact marker line, otherwise Jahia overwrites the deployed karaf/etc file on every start and user
 * cron edits are lost.</p>
 */
public class DefaultCfgResourceTest {

    private static final String RESOURCE = "META-INF/configurations/org.jahia.community.workflowtaskscleaner.cfg";
    private static final String MARKER = "# default configuration - won't be overridden";

    private static String readResource() throws Exception {
        try (InputStream in = DefaultCfgResourceTest.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            assertThat(in).as("resource %s must be on the classpath", RESOURCE).isNotNull();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    @Test
    public void defaultCfg_firstLineIsTheNonOverridableMarker() throws Exception {
        String content = readResource();
        String firstLine = content.split("\n", 2)[0];
        assertThat(firstLine).isEqualTo(MARKER);
    }

    @Test
    public void defaultCfg_seedsTheDefaultCron() throws Exception {
        assertThat(readResource()).contains("cronExpression=0 30 2 * * ?");
    }
}
