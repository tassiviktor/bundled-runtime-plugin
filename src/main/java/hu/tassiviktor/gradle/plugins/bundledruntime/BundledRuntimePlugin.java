package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Registers tasks:
 *  - prepareBundledApp
 *  - makeBundledRuntime
 *  - writeBundledLauncher
 *  - (optional) zipBundled
 *  - buildBundled (lifecycle)
 */
public class BundledRuntimePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        BundledRuntimeExtension ext = project.getExtensions()
                .create("bundledRuntime", BundledRuntimeExtension.class);

        Provider<Directory> bundledDir = project.getLayout().getBuildDirectory().dir("bundled");
        Provider<Directory> runtimeDir  = bundledDir.map(d -> d.dir("runtime"));
        Provider<Directory> appDir      = bundledDir.map(d -> d.dir("app"));

        TaskProvider<PrepareAppTask> prepare = project.getTasks()
                .register("prepareBundledApp", PrepareAppTask.class, t -> {
                    DirectoryProperty dest = t.getDestinationDir();
                    dest.set(bundledDir);
                    t.setGroup("distribution");
                    t.setDescription("Builds app artifact(s) into build/bundled/app.");
                    // dependencies wired at execution time by task action (bootJar/jar resolve)
                });

        TaskProvider<MakeRuntimeTask> jlink = project.getTasks()
                .register("makeBundledRuntime", MakeRuntimeTask.class, t -> {
                    t.getModules().set(ext.getModules());
                    t.getJlinkOptions().set(ext.getJlinkOptions());
                    t.getDestinationDir().set(runtimeDir);
                    t.dependsOn(prepare);
                    t.setGroup("distribution");
                    t.setDescription("Creates a minimized runtime image with jlink.");
                });

        TaskProvider<WriteLauncherTask> launcher = project.getTasks()
                .register("writeBundledLauncher", WriteLauncherTask.class, t -> {
                    t.getLauncherName().set(ext.getLauncherName());
                    t.getExitOnOome().set(ext.getExitOnOome());
                    t.getBundledRoot().set(bundledDir);
                    t.dependsOn(jlink);
                    t.setGroup("distribution");
                    t.setDescription("Writes platform launcher scripts into build/bundled/bin.");
                });


        TaskProvider<Zip> zip = project.getTasks().register("zipBundled", Zip.class, z -> {
            z.from(bundledDir);
            z.getArchiveBaseName().set(project.getName() + "-bundled");
            z.getArchiveVersion().set(project.getVersion().toString());
            z.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("distributions"));
            z.setGroup("distribution");
            z.setDescription("Zips the bundled runtime + app folder.");
        });

        project.getTasks().register("buildBundled", task -> {
            task.setGroup("distribution");
            task.setDescription("Build app, jlink runtime, launcher, and zip.");
            task.dependsOn(launcher);
            task.dependsOn(zip);
        });
    }
}
