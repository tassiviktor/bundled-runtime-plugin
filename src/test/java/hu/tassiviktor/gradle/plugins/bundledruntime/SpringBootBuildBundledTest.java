package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that builds and runs a minimal Spring Boot app with the plugin.
 *
 * <p>Note: Downloads Spring Boot artifacts; it can be slower on first run.</p>
 */
class SpringBootBuildBundledTest {

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

        var run = TestProjectUtil.runBundledLauncher(projectDir);
        assertEquals(0, run.exitCode, "Process should exit 0. Output:\n" + run.output);
        assertTrue(run.output.contains("OK_BOOT"), "Output should contain OK_BOOT. Output:\n" + run.output);
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
                "test-projects/boot/settings.gradle.tpl", vars);

        TestProjectUtil.writeFromResource(dir, "build.gradle",
                "test-projects/boot/build.gradle.tpl", vars);

        TestProjectUtil.writeFromResource(dir,
                "src/main/java/com/example/DemoApplication.java",
                "test-projects/boot/src/main/java/com/example/DemoApplication.java", Map.of());

        return dir;
    }
}
