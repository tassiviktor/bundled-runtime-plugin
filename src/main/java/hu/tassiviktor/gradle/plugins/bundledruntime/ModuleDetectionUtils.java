package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


final class ModuleDetectionUtils {

    private ModuleDetectionUtils() {
    }

    /**
     * Extracts nested JARs from a "fat" jar (e.g. Spring Boot) into a temp directory,
     * and returns them as real files that can be passed to jdeps.
     */
    static List<File> extractNestedJars(File appJar, File tempDir, Logger logger) {
        List<File> nestedJars = new ArrayList<>();

        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw new GradleException("Failed to create temp dir for nested JARs: " + tempDir);
        }

        try (JarFile jarFile = new JarFile(appJar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            int counter = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                // Spring Boot: BOOT-INF/lib/*.jar, de általánosan minden *.jar entry-t nézünk.
                if (!entry.getName().endsWith(".jar")) {
                    continue;
                }

                // Nevezzük át úgy, hogy ne ütközzön: <counter>_<basename>.jar
                String simpleName = Paths.get(entry.getName()).getFileName().toString();
                File target = new File(tempDir, (counter++) + "_" + simpleName);

                try (InputStream in = jarFile.getInputStream(entry)) {
                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                logger.info("[autoDetectModules] extracted nested jar: {}", target);
                nestedJars.add(target);
            }
        } catch (IOException e) {
            throw new GradleException("Failed to extract nested JARs from " + appJar, e);
        }

        return nestedJars;
    }

    static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path)
                    .sorted((a, b) -> b.compareTo(a)) // fájlok először, majd a könyvtárak
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best-effort cleanup
                        }
                    });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}