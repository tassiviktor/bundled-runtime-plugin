package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

/**
 * User-facing configuration for building a bundled runtime distribution.
 *
 * <p><b>Typical usage (Kotlin DSL)</b>:
 * <pre>{@code
 * bundledRuntime {
 *   launcherName.set("run")
 *   autoDetectModules.set(true)
 *   exitOnOome.set(true)
 *   modules.set(listOf("java.base", "java.sql", "jdk.unsupported"))
 *   jlinkOptions.set(listOf("--strip-debug", "--no-header-files", "--no-man-pages", "--compress", "2"))
 * }
 * }</pre>
 *
 * <p><b>Notes</b>:
 * <ul>
 *   <li>Spring Boot detection is automatic via the presence of the {@code org.springframework.boot} plugin,
 *       no extra flag is needed here.</li>
 *   <li>ZIP packaging is exposed as a separate public task ({@code zipBundled}); it is not governed by an extension flag.</li>
 * </ul>
 */
public abstract class BundledRuntimeExtension {

    // ---- Defaults kept in one place for easy maintenance ----------------------------------------

    /** Default launcher script name (without extension). */
    public static final String DEFAULT_LAUNCHER_NAME = "run";

    /** Default set of root modules for the jlink image (keep minimal!). */
    public static final List<String> DEFAULT_MODULES = Arrays.asList(
            "java.base", "java.sql", "java.xml",
            "java.logging", "java.naming", "java.management",
            "jdk.unsupported"
    );

    /** Default jlink options for a compact image. */
    public static final List<String> DEFAULT_JLINK_OPTIONS = Arrays.asList(
            "--strip-debug", "--no-header-files", "--no-man-pages", "--compress", "2"
    );

    // ---- User-configurable properties -----------------------------------------------------------

    /**
     * When {@code true}, the plugin will attempt to auto-detect required modules
     * from the application artifacts to complement {@link #getModules()}.
     * <p>Default: {@code true}</p>
     */
    public abstract Property<Boolean> getAutoDetectModules();

    /**
     * Name of the generated launcher script (without platform-specific extension).
     * <p>Default: {@value #DEFAULT_LAUNCHER_NAME}</p>
     */
    public abstract Property<String> getLauncherName();

    /**
     * Root modules to include in the jlink image. Keep this list as small as possible;
     * prefer {@link #getAutoDetectModules()} to fill in extras automatically.
     * <p>Default: {@link #DEFAULT_MODULES}</p>
     */
    public abstract ListProperty<String> getModules();

    /**
     * Additional jlink options (e.g. {@code --strip-debug}, {@code --no-header-files}, {@code --compress 2}).
     * <p>Default: {@link #DEFAULT_JLINK_OPTIONS}</p>
     */
    public abstract ListProperty<String> getJlinkOptions();

    /**
     * When {@code true}, the launcher adds {@code -XX:+ExitOnOutOfMemoryError}.
     * Recommended for containerized deployments where fast fail is preferred.
     * <p>Default: {@code true}</p>
     */
    public abstract Property<Boolean> getExitOnOome();

    /**
     * Enable Spring AOT on JVM (adds -Dspring.aot.enabled=true and wires processAot -> bootJar).
     * */
    public abstract Property<Boolean> getAotJvm();

    // ---- Construction & conventions -------------------------------------------------------------

    /**
     * Creates the extension and wires sensible defaults.
     */
    @Inject
    public BundledRuntimeExtension(ObjectFactory objects) {

        // Booleans
        getAutoDetectModules().convention(true);
        getExitOnOome().convention(true);
        getAotJvm().convention(false);

        // Strings
        getLauncherName().convention(DEFAULT_LAUNCHER_NAME);

        // Lists
        getModules().convention(DEFAULT_MODULES);
        getJlinkOptions().convention(DEFAULT_JLINK_OPTIONS);

    }

    // ---- Fluent helpers (optional sugar for build scripts) --------------------------------------

    /**
     * Convenience setter to replace {@link #getModules()} with the given values.
     * Useful from Groovy DSL: {@code modules "java.base", "jdk.unsupported"}.
     */
    public void modules(String... modules) {
        getModules().set(Arrays.asList(modules));
    }

    /**
     * Convenience setter to replace {@link #getJlinkOptions()} with the given values.
     * Useful from Groovy DSL: {@code jlinkOptions "--strip-debug", "--compress", "2"}.
     */
    public void jlinkOptions(String... options) {
        getJlinkOptions().set(Arrays.asList(options));
    }
}
