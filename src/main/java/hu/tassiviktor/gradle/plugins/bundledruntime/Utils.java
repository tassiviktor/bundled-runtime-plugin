package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.io.File;
import java.nio.file.Path;

/**
 * Small, focused helpers for Java toolchain resolution and process checks.
 * <p>
 * This utility supports:
 * <ul>
 *   <li>Ensuring the current runtime is Java 17 or newer.</li>
 *   <li>Resolving tool executables (e.g., {@code jlink}, {@code jdeps}) from the Gradle toolchain.</li>
 * </ul>
 *
 * <p>All methods are package-private by design to keep the API minimal within the plugin.</p>
 */
final class Utils {

    private static final JavaLanguageVersion DEFAULT_LANGUAGE_VERSION = JavaLanguageVersion.of(17);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private Utils() {
        // This is a utility class
    }

    /**
     * Ensures the current JVM is Java 17 or newer.
     * <p>
     * Uses {@code Runtime.version().feature()} when available (JDK 9+). If that fails,
     * falls back to parsing {@code java.specification.version}. Throws a {@link GradleException}
     * when the detected version is older than 17. Best-effort only if version parsing fails.
     */
    static void ensureJava17Plus() {
        // Primary: Runtime.version() (JDK 9+)
        try {
            int feature = Runtime.version().feature();
            if (feature < 17) {
                throw new GradleException("JDK 17+ is required to run jlink. Detected feature: " + feature);
            }
            return;
        } catch (Throwable ignored) {
            // Runtime.version() not available or unexpected failure → fall back below
        }

        // Fallback: parse "java.specification.version"
        String spec = System.getProperty("java.specification.version", "");
        try {
            double v = Double.parseDouble(spec);
            if (v < 17.0) {
                throw new GradleException("JDK 17+ is required to run jlink. Detected: " + spec);
            }
        } catch (NumberFormatException ignored) {
            // best-effort only; don't fail hard if we cannot parse
        }
    }

    /**
     * Resolves the {@code jlink} executable from the configured Java toolchain (or a JDK 17 default).
     *
     * @param project the Gradle project
     * @return the {@code jlink} executable file
     * @throws GradleException if the executable cannot be found
     */
    static File jlinkExecutable(Project project) {
        return resolveToolExecutable(project, "jlink", "jlink");
    }

    /**
     * Resolves the {@code jdeps} executable from the configured Java toolchain (or a JDK 17 default).
     *
     * @param project the Gradle project
     * @return the {@code jdeps} executable file
     * @throws GradleException if the executable cannot be found
     */
    static File jdepsExecutable(Project project) {
        return resolveToolExecutable(project, "jdeps", "jdeps");
    }

    /**
     * @return {@code true} if running on Windows
     */
    static boolean isWindows() {
        return WINDOWS;
    }

    /**
     * Resolve a tool executable (e.g., "jlink", "jdeps") from the Gradle Java toolchain.
     * Falls back to JDK 17 if no language version is explicitly configured.
     */
    private static File resolveToolExecutable(Project project, String toolBaseName, String humanReadableName) {
        JavaToolchainService service = project.getExtensions().getByType(JavaToolchainService.class);

        JavaLanguageVersion languageVersion = resolveLanguageVersion(project);
        File javaHome = service.launcherFor(spec -> spec.getLanguageVersion().set(languageVersion))
                .get()
                .getMetadata()
                .getInstallationPath()
                .getAsFile();

        String executableName = WINDOWS ? toolBaseName + ".exe" : toolBaseName;
        Path toolPath = javaHome.toPath().resolve("bin").resolve(executableName);
        File toolFile = toolPath.toFile();

        if (!toolFile.exists()) {
            String extra = "jlink".equals(toolBaseName)
                    ? " (Make sure a full JDK is installed, not a JRE)."
                    : "";
            throw new GradleException(humanReadableName + " not found at: " + toolFile.getAbsolutePath() + extra);
        }
        return toolFile;
    }

    /**
     * Determine the desired language version from the project's Java plugin/toolchain configuration,
     * defaulting to Java 17 when not set.
     */
    private static JavaLanguageVersion resolveLanguageVersion(Project project) {
        JavaPluginExtension javaExt = project.getExtensions().findByType(JavaPluginExtension.class);
        if (javaExt != null && javaExt.getToolchain().getLanguageVersion().isPresent()) {
            return javaExt.getToolchain().getLanguageVersion().get();
        }
        return DEFAULT_LANGUAGE_VERSION;
    }
}
