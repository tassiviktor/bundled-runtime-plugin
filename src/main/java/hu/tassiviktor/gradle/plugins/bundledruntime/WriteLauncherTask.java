package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a platform-specific launcher into {@code <bundled>/bin}.
 * <p>
 * The launcher contents are loaded from classpath resources instead of being hardcoded.
 * Two templates are expected on the classpath:
 * <ul>
 *   <li>{@code /launchers/launcher-unix.sh}</li>
 *   <li>{@code /launchers/launcher-windows.bat}</li>
 * </ul>
 * The template must contain the placeholder {@code ${FLAGS_PLACEHOLDER}}, which
 * will be replaced at build time with the JVM flags determined by task inputs.
 *
 * <p>Resulting files:</p>
 * <ul>
 *   <li>Unix: {@code <bundled>/bin/<launcherName>} (marked executable)</li>
 *   <li>Windows: {@code <bundled>/bin/<launcherName>.bat}</li>
 * </ul>
 *
 * <p>Launchers execute:
 * <pre>
 *   &lt;runtime&gt;/bin/java -jar &lt;app&gt;/app.jar [args...]
 * </pre>
 * with optional JVM flags (e.g. {@code -XX:+ExitOnOutOfMemoryError}).</p>
 */
public abstract class WriteLauncherTask extends DefaultTask {

    private static final String PLACEHOLDER = "${FLAGS_PLACEHOLDER}";
    private static final String RES_UNIX = "launchers/launcher-unix.sh";
    private static final String RES_WIN = "launchers/launcher-windows.bat";

    /** The launcher base name (without extension). */
    @Input
    public abstract Property<String> getLauncherName();

    /** Whether to add {@code -XX:+ExitOnOutOfMemoryError} to the JVM flags. */
    @Input
    public abstract Property<Boolean> getExitOnOome();

    /** The bundled root directory that contains {@code bin/}, {@code runtime/}, {@code app/}. */
    @OutputDirectory
    public abstract DirectoryProperty getBundledRoot();

    /**
     * Generates the launcher file by reading a platform-specific template from the classpath,
     * filling in dynamic flags, and writing it to {@code <bundled>/bin}.
     */
    @TaskAction
    public void run() {
        final Path root = getBundledRoot().getAsFile().get().toPath();
        final Path binDir = root.resolve("bin");

        try {
            Files.createDirectories(binDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create bin dir: " + binDir, e);
        }

        final String jvmFlags = getExitOnOome().getOrElse(Boolean.FALSE)
                ? "-XX:+ExitOnOutOfMemoryError"
                : "";

        final boolean windows = Utils.isWindows();
        final String resource = windows ? RES_WIN : RES_UNIX;
        final String template = readResourceAsString(resource);

        if (template == null) {
            throw new RuntimeException("Launcher template not found on classpath: " + resource);
        }

        final String filled = template.replace(PLACEHOLDER, jvmFlags);
        final Path target = windows
                ? binDir.resolve(getLauncherName().get() + ".bat")
                : binDir.resolve(getLauncherName().get());

        writeFile(target, filled, windows);

        // mark executable on Unix
        if (!windows) {
            File f = target.toFile();
            if (!f.setExecutable(true)) {
                getLogger().warn("Could not mark launcher as executable: {}", f.getAbsolutePath());
            }
        }

        getLogger().lifecycle("[writeBundledLauncher] done -> {}", binDir.toAbsolutePath());
    }

    /**
     * Loads a classpath resource as UTF-8 text.
     *
     * @param resourcePath path relative to the classpath root (e.g. {@code launchers/launcher-unix.sh})
     * @return the file contents, or {@code null} if not found
     */
    private static String readResourceAsString(String resourcePath) {
        ClassLoader cl = WriteLauncherTask.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(1024, in.available()));
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                bos.write(buf, 0, r);
            }
            return bos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read resource: " + resourcePath, e);
        }
    }

    /**
     * Writes text to disk. On Windows we normalize line endings to CRLF; otherwise LF.
     */
    private static void writeFile(Path target, String content, boolean windows) {
        try {
            String normalized = windows
                    ? content.replace("\r\n", "\n").replace("\n", "\r\n")
                    : content.replace("\r\n", "\n");
            Files.writeString(target, normalized, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write file: " + target, e);
        }
    }
}
