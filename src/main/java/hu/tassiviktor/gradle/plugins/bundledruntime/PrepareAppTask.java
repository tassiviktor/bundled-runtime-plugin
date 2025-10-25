package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Prepares application artifacts into a unified layout under a destination directory.
 *
 * <p>Resulting structure:</p>
 * <pre>
 *   &lt;dest&gt;/app/app.jar
 *   &lt;dest&gt;/app/lib/*   (only for non-Spring Boot projects)
 * </pre>
 *
 * <p>Behavior:</p>
 * <ul>
 *   <li><b>Spring Boot projects</b>: uses the {@code bootJar} task output (fat jar) as {@code app.jar}.</li>
 *   <li><b>Non-Boot projects</b>: uses the {@code jar} task output as {@code app.jar} and copies
 *       {@code runtimeClasspath} dependencies into {@code /app/lib}.</li>
 * </ul>
 *
 * <p><b>Note:</b> For non-Boot jars, ensure the manifest specifies {@code Main-Class};
 * otherwise launching with {@code -jar} will fail.</p>
 */
public abstract class PrepareAppTask extends DefaultTask {

    /** Destination root directory that will contain the {@code app/} subtree. */
    @OutputDirectory
    public abstract DirectoryProperty getDestinationDir();

    /**
     * Executes the preparation:
     * <ol>
     *   <li>Clears the destination directory.</li>
     *   <li>Creates {@code app/} (and {@code app/lib/} when needed).</li>
     *   <li>Copies the main jar to {@code app/app.jar}.</li>
     *   <li>For non-Boot projects, copies {@code runtimeClasspath} dependencies to {@code app/lib/}.</li>
     * </ol>
     */
    @TaskAction
    public void run() {
        final Path dest = getDestinationDir().getAsFile().get().toPath();

        // 1) Clean destination
        cleanDestination(dest);

        // 2) Decide flow based on Spring Boot plugin presence
        if (isSpringBootProject()) {
            handleSpringBoot(dest);
        } else {
            handlePlainJava(dest);
        }

        getLogger().lifecycle("[prepareBundledApp] done -> {}", dest.toAbsolutePath());
    }

    // -------- Implementation details --------

    private void cleanDestination(Path dest) {
        getProject().delete(dest.toFile());
        try {
            Files.createDirectories(dest);
        } catch (Exception e) {
            throw new GradleException("Failed to create destination directory: " + dest, e);
        }
    }

    private boolean isSpringBootProject() {
        return getProject().getPlugins().hasPlugin("org.springframework.boot");
    }

    private void handleSpringBoot(Path dest) {
        Task bootJarTask = getProject().getTasks().findByName("bootJar");
        if (bootJarTask == null) {
            throw new GradleException("Spring Boot detected but 'bootJar' task not found.");
        }

        // Ensure it's realized and get the archive file
        bootJarTask.getActions();
        File fatJar = ((Jar) bootJarTask).getArchiveFile().get().getAsFile();

        Path appDir = dest.resolve("app");
        createDir(appDir);

        // copy fat jar as app.jar
        copyRenamed(fatJar, appDir.toFile(), "app.jar");
    }

    private void handlePlainJava(Path dest) {
        Task jarTask = getProject().getTasks().findByName("jar");
        if (jarTask == null) {
            throw new GradleException("'jar' task not found. Apply the 'java' plugin.");
        }
        File mainJar = ((Jar) jarTask).getArchiveFile().get().getAsFile();

        Path appDir = dest.resolve("app");
        Path libDir = appDir.resolve("lib");
        createDir(appDir);
        createDir(libDir);

        // copy main jar as app.jar
        copyRenamed(mainJar, appDir.toFile(), "app.jar");

        var runtimeCp = getProject().getConfigurations().findByName("runtimeClasspath");
        if (runtimeCp == null) {
            throw new GradleException("Configuration 'runtimeClasspath' not found.");
        }

        Set<File> deps = runtimeCp.resolve();
        if (!deps.isEmpty()) {
            getProject().copy(spec -> {
                spec.from(deps);
                spec.into(libDir.toFile());
            });
        }
    }

    private void createDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new GradleException("Failed to create directory: " + dir, e);
        }
    }

    private void copyRenamed(File from, File intoDir, String targetName) {
        getProject().copy(spec -> {
            spec.from(from);
            spec.into(intoDir);
            spec.rename(name -> targetName);
        });
    }
}
