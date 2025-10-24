package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.Arrays;

/**
 * User-facing configuration for the bundled runtime build.
 */
public abstract class BundledRuntimeExtension {

    public abstract Property<Boolean> getAutoDetectModules();

    /** Name of the launcher script (without extension). Default: "run". */
    public abstract Property<String> getLauncherName();

    /** Root modules to include in the jlink image. Keep minimal. */
    public abstract ListProperty<String> getModules();

    /** Extra jlink options, e.g. --strip-debug, --no-header-files, --compress 2. */
    public abstract ListProperty<String> getJlinkOptions();

    /** Detect Spring Boot and prefer bootJar if available. */
    public abstract Property<Boolean> getDetectSpringBoot();

    /** Add -XX:+ExitOnOutOfMemoryError to launcher. Recommended on containers. */
    public abstract Property<Boolean> getExitOnOome();

    /** Additionally create a ZIP at build/distributions. */
    public abstract Property<Boolean> getZipOutput();

    @Inject
    public BundledRuntimeExtension(ObjectFactory objects) {
        getLauncherName().convention("run");
        getDetectSpringBoot().convention(true);
        getExitOnOome().convention(true);
        getZipOutput().convention(true);

        getAutoDetectModules().convention(true);

        getModules().convention(Arrays.asList(
                "java.base", "java.sql", "java.xml",
                "java.logging", "java.naming", "java.management",
                "jdk.unsupported"
        ));
        getJlinkOptions().convention(Arrays.asList(
                "--strip-debug", "--no-header-files", "--no-man-pages", "--compress", "2"
        ));
    }
}
