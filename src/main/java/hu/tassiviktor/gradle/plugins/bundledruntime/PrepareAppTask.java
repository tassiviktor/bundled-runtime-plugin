package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.*;
import org.gradle.jvm.tasks.Jar;

import java.io.File;

/**
 * Builds the application artifacts into a unified folder:
 *
 *  <dest>/app/app.jar
 *  <dest>/app/lib/*   (only for non-Boot projects)
 *
 * Spring Boot:
 *   uses bootJar (fat jar).
 * Non-Boot:
 *   uses jar + runtimeClasspath dependencies into /app/lib.
 *
 * Note: For non-Boot jars, ensure your jar manifest has Main-Class set,
 * otherwise launching with -jar will fail.
 */
public abstract class PrepareAppTask extends DefaultTask {

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDir();

    @TaskAction
    public void run() {
        File dest = getDestinationDir().getAsFile().get();
        if (dest.exists()) deleteRecursively(dest);
        dest.mkdirs();

        boolean spring = getProject().getPlugins().hasPlugin("org.springframework.boot");
        if (spring) {
            Task bootJar = getProject().getTasks().findByName("bootJar");
            if (bootJar == null) {
                throw new GradleException("Spring Boot detected but 'bootJar' task not found.");
            }
            bootJar.getActions(); // realize
            File fat = ((Jar) bootJar).getArchiveFile().get().getAsFile();
            getProject().copy(spec -> {
                spec.from(fat);
                spec.into(new File(dest, "app"));
                spec.rename(name -> "app.jar");
            });
        } else {
            Jar jar = (Jar) getProject().getTasks().findByName("jar");
            if (jar == null) throw new GradleException("'jar' task not found. Apply 'java' plugin.");
            File jarFile = jar.getArchiveFile().get().getAsFile();

            File appDir = new File(dest, "app");
            File libDir = new File(appDir, "lib");
            appDir.mkdirs();
            libDir.mkdirs();

            getProject().copy(spec -> {
                spec.from(jarFile);
                spec.into(appDir);
                spec.rename(name -> "app.jar");
            });

            var rc = getProject().getConfigurations().findByName("runtimeClasspath");
            if (rc == null) throw new GradleException("Configuration 'runtimeClasspath' not found.");
            var files = rc.resolve();
            if (!files.isEmpty()) {
                getProject().copy(spec -> {
                    spec.from(files);
                    spec.into(libDir);
                });
            }
        }

        getLogger().lifecycle("[prepareBundledApp] done -> {}", dest);
    }

    private static void deleteRecursively(File f) {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            for (File c : f.listFiles()) deleteRecursively(c);
        }
        if (!f.delete()) {
            throw new GradleException("Failed to delete: " + f.getAbsolutePath());
        }
    }
}
