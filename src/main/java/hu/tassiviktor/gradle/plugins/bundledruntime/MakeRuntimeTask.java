package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Gradle task that runs {@code jlink} to produce a minimized custom runtime image.
 * <p>
 * Output layout:
 * <pre>
 *   &lt;destinationDir&gt;/runtime
 * </pre>
 * <p>
 * Notes:
 * <ul>
 *   <li>When auto-detection is enabled, the task uses {@code jdeps --print-module-deps}
 *       on {@code app.jar} (+ {@code app/lib/*.jar} when present) to compute the minimal module set.</li>
 *   <li>{@code jlink} dislikes existing output directories, so work is done in a temporary folder,
 *       then atomically (or best-effort) swapped into place.</li>
 * </ul>
 */
public abstract class MakeRuntimeTask extends DefaultTask {

    /** Explicit list of modules to include when auto-detection is disabled. */
    @Input public abstract ListProperty<String> getModules();

    /** Additional {@code jlink} options (passed through as-is). */
    @Input public abstract ListProperty<String> getJlinkOptions();

    /** If {@code true}, compute modules with {@code jdeps}; otherwise use {@link #getModules()}. */
    @Input public abstract Property<Boolean> getAutoDetectModules();

    /**
     * Hint that the application is a Spring Boot application. If not set,
     * the task will try to detect it by peeking into {@code app.jar}.
     */
    @Input public abstract Property<Boolean> getSpringBootProject();

    /** Points to {@code build/bundled/app} — must contain {@code app.jar} (and optionally {@code lib/}). */
    @InputDirectory
    public abstract DirectoryProperty getAppRoot();

    /** Destination directory that will contain the {@code runtime/} folder. */
    @OutputDirectory
    public abstract DirectoryProperty getDestinationDir();

    /**
     * Task entrypoint.
     */
    @TaskAction
    public void run() {
        Utils.ensureJava17Plus();

        // --- Resolve paths ---
        File out = getDestinationDir().getAsFile().get();   // .../build/bundled/runtime
        File parent = out.getParentFile();                  // .../build/bundled
        if (!parent.exists() && !parent.mkdirs()) {
            throw new GradleException("Cannot create parent directory: " + parent);
        }

        // jlink does not like pre-existing output dirs; use a temp directory first.
        File tmp = new File(parent, out.getName() + ".tmp-" + UUID.randomUUID());
        if (tmp.exists() && !safeDeleteRecursively(tmp)) {
            throw new GradleException("Cannot clean previous temp dir: " + tmp);
        }

        // --- Modules (auto-detect vs manual) ---
        final boolean autodetect = Boolean.TRUE.equals(getAutoDetectModules().get());
        final List<String> modulesToUse = autodetect
                ? Stream.concat(autoDetectModules().stream(), getModules().get().stream())
                .distinct()
                .toList()
                : getModules().get();

        // --- Run jlink ---
        File jlink = Utils.jlinkExecutable(getProject());
        List<String> jlinkArgs = new ArrayList<>();
        jlinkArgs.addAll(getJlinkOptions().get());
        jlinkArgs.add("--add-modules");
        jlinkArgs.add(String.join(",", modulesToUse));
        jlinkArgs.add("--output");
        jlinkArgs.add(tmp.getAbsolutePath()); // jlink writes into the temp directory

        ExecOutcome jlinkOutcome = execCapture(jlink.getAbsolutePath(), jlinkArgs);
        if (jlinkOutcome.exit != 0) {
            safeDeleteRecursively(tmp);
            throw new GradleException("jlink failed (exit=" + jlinkOutcome.exit + ")\nSTDOUT:\n" +
                    jlinkOutcome.stdout + "\nSTDERR:\n" + jlinkOutcome.stderr);
        }

        // --- Swap temp -> final (best effort atomic replace) ---
        if (out.exists() && !safeDeleteRecursively(out)) {
            // Rare but possible (locked by AV etc.): fail fast and keep temp for inspection
            safeDeleteRecursively(tmp);
            throw new GradleException("Cannot replace existing runtime dir: " + out);
        }
        // Try simple rename first; on Windows/AV this may fail → fallback to copy.
        if (!tmp.renameTo(out)) {
            getProject().copy(c -> { c.from(tmp); c.into(out); });
            // Clean up temp after successful copy
            safeDeleteRecursively(tmp);
        }

        getLogger().lifecycle("[makeBundledRuntime] done -> {}", out);
    }

    /**
     * Safe recursive delete that does not throw unless truly unavoidable.
     * @return {@code true} if the path no longer exists afterwards.
     */
    private static boolean safeDeleteRecursively(File f) {
        if (f == null || !f.exists()) return true;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File c : kids) {
                    if (!safeDeleteRecursively(c)) return false;
                }
            }
        }
        return f.delete();
    }

    /**
     * Compute a minimal set of modules using {@code jdeps --print-module-deps} on {@code app.jar}.
     * Also adds a couple of pragmatic defaults (e.g. {@code jdk.crypto.ec}, and {@code java.desktop}
     * for Spring Boot apps).
     */
    private List<String> autoDetectModules() {
        File appRoot = getAppRoot().getAsFile().get();
        File appJar = new File(appRoot, "app.jar");
        if (!appJar.exists()) {
            throw new GradleException("autoDetectModules: app.jar not found at " + appJar.getAbsolutePath());
        }

        // app/lib/*.jar -> classpath
        String classpath = "";
        File libDir = new File(appRoot, "lib");
        if (libDir.isDirectory()) {
            File[] jars = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jars != null && jars.length > 0) {
                classpath = Arrays.stream(jars)
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining(File.pathSeparator));
            }
        }

        File nestedTempDir = new File(appRoot, ".jdeps-nested");
        ModuleDetectionUtils.deleteRecursively(nestedTempDir.toPath());

        try {
            List<File> nestedJars =
                    ModuleDetectionUtils.extractNestedJars(appJar, nestedTempDir, getLogger());

            // *** nested jarok csak classpath-on, nem rootként ***
            if (!nestedJars.isEmpty()) {
                String nestedCp = nestedJars.stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining(File.pathSeparator));

                if (!classpath.isEmpty()) {
                    classpath = classpath + File.pathSeparator + nestedCp;
                } else {
                    classpath = nestedCp;
                }
            }

            File jdeps = Utils.jdepsExecutable(getProject());
            List<String> args = new ArrayList<>();

            args.add("--ignore-missing-deps");
            args.add("--multi-release");
            args.add("21");

            if (!classpath.isEmpty()) {
                args.add("-cp");
                args.add(classpath);
            }

            args.add("--print-module-deps");

            args.add(appJar.getAbsolutePath());

            ExecOutcome jdepsOutcome = execCapture(jdeps.getAbsolutePath(), args);
            if (jdepsOutcome.exit != 0) {
                throw new GradleException("jdeps failed (exit=" + jdepsOutcome.exit + ")\nSTDOUT:\n" +
                        jdepsOutcome.stdout + "\nSTDERR:\n" + jdepsOutcome.stderr);
            }

            Set<String> modules = new LinkedHashSet<>();
            String line = jdepsOutcome.stdout.trim();
            if (!line.isEmpty()) {
                Arrays.stream(line.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(modules::add);
            }

            modules.add("jdk.crypto.ec");

            boolean isBoot = Boolean.TRUE.equals(getSpringBootProject().get());
            if (!isBoot && appJar.isFile()) {
                try (JarFile jf = new JarFile(appJar)) {
                    isBoot =
                            jf.getEntry("org/springframework/boot/SpringApplication.class") != null ||
                                    jf.getEntry("org/springframework/boot/loader/launch/Launcher.class") != null ||
                                    jf.getEntry("org/springframework/boot/loader/JarLauncher.class") != null;
                } catch (IOException ignored) {
                }
            }
            if (isBoot) {
                modules.add("java.desktop");
            }

            getLogger().lifecycle("[autoDetectModules] {}", modules);
            return new ArrayList<>(modules);
        } finally {
            ModuleDetectionUtils.deleteRecursively(nestedTempDir.toPath());
        }
    }



    /**
     * Execute an external tool and capture exit code, STDOUT and STDERR.
     */
    private ExecOutcome execCapture(String executable, List<String> args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exit = getProject().exec(spec -> {
            spec.setExecutable(executable);
            spec.args(args);
            spec.setStandardOutput(stdout);
            spec.setErrorOutput(stderr);
            spec.setIgnoreExitValue(true);
        }).getExitValue();

        return new ExecOutcome(exit, stdout.toString(), stderr.toString());
    }

    /** Tiny value object for process results. */
    private static final class ExecOutcome {
        final int exit;
        final String stdout;
        final String stderr;

        ExecOutcome(int exit, String stdout, String stderr) {
            this.exit = exit;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
