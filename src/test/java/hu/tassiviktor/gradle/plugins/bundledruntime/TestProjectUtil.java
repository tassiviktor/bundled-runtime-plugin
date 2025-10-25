package hu.tassiviktor.gradle.plugins.bundledruntime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Small utility helpers for test projects.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Write files either from a string or from a classpath resource with simple ${} substitution.</li>
 *   <li>Resolve the plugin project root by walking up from the compiled test classes.</li>
 *   <li>Run the generated bundled launcher (Unix {@code run} or Windows {@code run.bat}).</li>
 * </ul>
 */
final class TestProjectUtil {

    private TestProjectUtil() { /* no instances */ }

    /**
     * Writes textual content to a file under {@code root/relPath}, creating parent directories as needed.
     */
    static Path write(Path root, String relPath, CharSequence content) throws IOException {
        Path target = root.resolve(relPath);
        Files.createDirectories(Objects.requireNonNull(target.getParent(), "parent must exist"));
        return Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Writes a file from a classpath resource and applies a simple ${key} -&gt; value substitution.
     * Fails fast if the resource is not found.
     */
    static Path writeFromResource(Path root, String relPath, String resourcePath, Map<String, String> vars) throws IOException {
        String raw = readClasspath(resourcePath);
        String resolved = substitute(raw, vars);
        return write(root, relPath, resolved);
    }

    private static String readClasspath(String resourcePath) throws IOException {
        try (InputStream in = TestProjectUtil.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found on classpath: " + resourcePath);
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return br.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    private static String substitute(String text, Map<String, String> vars) {
        if (vars == null || vars.isEmpty()) return text;
        String result = text;
        for (var e : vars.entrySet()) {
            result = result.replace("${" + e.getKey() + "}", e.getValue());
        }
        return result;
    }

    /**
     * Returns the absolute path of the plugin project root by walking up from the compiled test class location.
     * The method is defensive about missing code source and validates the resolved directory.
     */
    static String getPluginPath() {
        try {
            var codeSource = TestProjectUtil.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                throw new IllegalStateException("Cannot determine code source location for TestProjectUtil.class");
            }
            Path classesDir = Paths.get(codeSource.getLocation().toURI()).toAbsolutePath();

            // Typical Gradle layout: build/classes/java/test → project root (go up 4)
            Path pluginRoot = classesDir.getParent()   // java
                    .getParent()                      // classes
                    .getParent()                      // build
                    .getParent()                      // project root
                    .normalize();

            if (pluginRoot == null || !Files.exists(pluginRoot)) {
                throw new IllegalStateException("Resolved plugin root does not exist: " + pluginRoot);
            }
            return pluginRoot.toString().replace("\\", "/");
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to resolve plugin root path", e);
        }
    }

    /**
     * Finds the generated launcher under {@code build/bundled/bin} and executes it, returning the exit code and output.
     */
    static ProcessResult runBundledLauncher(Path projectDir) throws IOException, InterruptedException {
        Path base = projectDir.resolve("build/bundled");
        Path bin = base.resolve("bin");
        Path run = bin.resolve("run");
        Path runBat = bin.resolve("run.bat");

        Path launcher = Files.exists(run) ? run : runBat;
        if (!Files.exists(launcher)) {
            throw new IOException("Launcher not found under " + bin);
        }

        Process proc = new ProcessBuilder()
                .command(launcher.toAbsolutePath().toString())
                .directory(base.toFile())
                .redirectErrorStream(true)
                .start();

        String out;
        try (var is = proc.getInputStream()) {
            out = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exit = proc.waitFor();
        return new ProcessResult(exit, out);
    }

    /** Simple value object for process results. */
    static final class ProcessResult {
        final int exitCode;
        final String output;

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
