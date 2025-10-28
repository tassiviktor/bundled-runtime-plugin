package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.gradle.testkit.runner.TaskOutcome.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that builds and runs a minimal Spring Boot app with the plugin.
 *
 * <p>Note: Downloads Spring Boot artifacts; it can be slower on first run.</p>
 */
class SpringBootAotBuildBundledTest {

    @Test
    void buildsAndRunsSpringBootBundle() throws Exception {
        Path projectDir = createBootProject();

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("clean", "buildBundled", "--stacktrace")
                .forwardOutput()
                .build();

        assertNotNull(result.task(":buildBundled"));
        assertEquals(SUCCESS, result.task(":buildBundled").getOutcome());

        assertAotGeneratedFiles(projectDir.toFile(), result);

        var run = TestProjectUtil.runBundledLauncher(projectDir);
        assertEquals(0, run.exitCode, "Process should exit 0. Output:\n" + run.output);
        assertTrue(run.output.contains("OK_BOOT"), "Output should contain OK_BOOT. Output:\n" + run.output);
        assertTrue(run.output.contains("AOT_ENABLED=true"),"Output should contain AOT_ENABLED. Output: \n" + run.output);
    }

    private Path createBootProject() throws Exception {
        String pluginRoot = TestProjectUtil.getPluginPath();
        Path dir = Files.createTempDirectory("boot-proj");

        // templates from resources + substitution
        Map<String, String> vars = Map.of(
                "PLUGIN_ROOT", pluginRoot,
                "BOOT_VERSION", "3.3.4",
                "DM_VERSION", "1.1.6"
        );

        TestProjectUtil.writeFromResource(dir, "settings.gradle",
                "test-projects/boot-aot/settings.gradle.tpl", vars);

        TestProjectUtil.writeFromResource(dir, "build.gradle",
                "test-projects/boot-aot/build.gradle.tpl", vars);

        TestProjectUtil.writeFromResource(dir,
                "src/main/java/com/example/DemoApplication.java",
                "test-projects/boot-aot/src/main/java/com/example/DemoApplication.java", Map.of());

        return dir;
    }

    private void assertAotGeneratedFiles(File projectDir, BuildResult res) {
        var t = res.task(":processAot");
        assertNotNull(t, "processAot should exist");
        assertTrue(
                t.getOutcome() == SUCCESS || t.getOutcome() == UP_TO_DATE || t.getOutcome() == FROM_CACHE,
                "processAot not executed: " + t.getOutcome()
        );

        File generated = new File(projectDir, "build/generated");
        assertTrue(generated.isDirectory(), "No build/generated directory found");

        boolean hasAotFiles = findAnyAotFile(generated);
        assertTrue(hasAotFiles, "AOT outputs are empty or not under build/generated/aotSources");
    }

    private static boolean findAnyAotFile(File root) {
        if (root == null || !root.exists()) return false;
        try (Stream<Path> s = Files.walk(root.toPath())) {
            return s.anyMatch(p -> p.toString().contains("aotSources") && p.toString().endsWith(".java"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan for AOT outputs", e);
        }
    }

}
