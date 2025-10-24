package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.io.File;

/** Small helpers for toolchain and process checks. */
final class Utils {
    private Utils() {}

    static void ensureJava17Plus() {
        String spec = System.getProperty("java.specification.version");
        try {
            double v = Double.parseDouble(spec);
            if (v < 17.0) {
                throw new GradleException("JDK 17+ is required to run jlink. Detected: " + spec);
            }
        } catch (NumberFormatException ignored) { /* best-effort only */ }
    }

    static File jlinkExecutable(Project project) {
        JavaToolchainService svc = project.getExtensions().getByType(JavaToolchainService.class);

        JavaPluginExtension javaExt = project.getExtensions().findByType(JavaPluginExtension.class);
        JavaLanguageVersion lang = (javaExt != null && javaExt.getToolchain().getLanguageVersion().isPresent())
                ? javaExt.getToolchain().getLanguageVersion().get()
                : JavaLanguageVersion.of(17);

        File javaHome = svc.launcherFor(spec -> spec.getLanguageVersion().set(lang))
                .get().getMetadata().getInstallationPath().getAsFile();

        File bin = new File(javaHome, "bin");
        String exe = isWindows() ? "jlink.exe" : "jlink";
        File jlink = new File(bin, exe);
        if (!jlink.exists()) {
            throw new GradleException("jlink not found at: " + jlink.getAbsolutePath() +
                    " (Make sure a full JDK is installed, not a JRE).");
        }
        return jlink;
    }

    static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }
    // Utils.java
    static File jdepsExecutable(Project project) {
        var svc = project.getExtensions().getByType(JavaToolchainService.class);
        var javaExt = project.getExtensions().findByType(JavaPluginExtension.class);
        var lang = (javaExt != null && javaExt.getToolchain().getLanguageVersion().isPresent())
                ? javaExt.getToolchain().getLanguageVersion().get()
                : JavaLanguageVersion.of(17);

        File javaHome = svc.launcherFor(spec -> spec.getLanguageVersion().set(lang))
                .get().getMetadata().getInstallationPath().getAsFile();
        File bin = new File(javaHome, "bin");
        String exe = isWindows() ? "jdeps.exe" : "jdeps";
        File jdeps = new File(bin, exe);
        if (!jdeps.exists()) {
            throw new GradleException("jdeps not found at: " + jdeps.getAbsolutePath());
        }
        return jdeps;
    }

}
