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
 * Integration test that builds and runs a plain Java app with the plugin.
 */
class PlainJavaBuildBundledTest {

    @Test
    void buildsAndRunsPlainJavaBundle() throws Exception {
        Path projectDir = createPlainJavaProject();

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
        assertTrue(run.output.contains("OK"), "Output should contain OK. Output:\n" + run.output);
    }

    private Path createPlainJavaProject() throws Exception {
        String pluginRoot = TestProjectUtil.getPluginPath();
        Path dir = Files.createTempDirectory("plain-java-proj");

        Map<String, String> vars = Map.of("PLUGIN_ROOT", pluginRoot);

        TestProjectUtil.writeFromResource(dir, "settings.gradle",
                "test-projects/plain/settings.gradle.tpl", vars);

        TestProjectUtil.writeFromResource(dir, "build.gradle",
                "test-projects/plain/build.gradle.tpl", Map.of());

        TestProjectUtil.writeFromResource(dir,
                "src/main/java/com/example/Main.java",
                "test-projects/plain/src/main/java/com/example/Main.java",
                Map.of());

        return dir;
    }
}
