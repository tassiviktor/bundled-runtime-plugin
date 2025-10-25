package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.TaskContainer;

/**
 * Gradle plugin that builds a self-contained distribution of a Java (or Spring Boot) app.
 *
 * Exposes two primary tasks for users:
 *
 * <ul>
 *   <li><b>buildBundled</b> – Compile & package the app, detect required modules/dependencies,
 *       create a minimized runtime image via jlink, and write platform launchers.</li>
 *   <li><b>zipBundled</b> – Package the assembled distribution into a ZIP archive.</li>
 * </ul>
 *
 * Internal steps (kept as separate tasks for clarity and incremental correctness):
 * <ul>
 *   <li>prepareBundledApp – Collects/assembles app artifacts into build/bundled/app.</li>
 *   <li>makeBundledRuntime – Runs jlink to create the minimal runtime under build/bundled/runtime.</li>
 *   <li>writeBundledLauncher – Generates platform launcher scripts in build/bundled/bin.</li>
 * </ul>
 *
 * Design notes:
 * - The internal tasks are left ungrouped (hidden) to keep the public surface small (two main tasks).
 * - Plugin presence is detected (Java / Spring Boot) to wire correct compilation dependencies.
 * - Task ordering is enforced via dependsOn chains; users typically run only the two public tasks.
 */
public class BundledRuntimePlugin implements Plugin<Project> {

    // Public task names (the only ones users should call)
    private static final String TASK_BUILD_BUNDLED = "buildBundled";
    private static final String TASK_ZIP_BUNDLED   = "zipBundled";

    // Internal task names (intentionally ungrouped)
    private static final String TASK_PREPARE = "prepareBundledApp";
    private static final String TASK_JLINK   = "makeBundledRuntime";
    private static final String TASK_LAUNCH  = "writeBundledLauncher";

    // Common group for public tasks
    private static final String GROUP_DISTRIBUTION = "distribution";

    @Override
    public void apply(Project project) {
        final TaskContainer tasks = project.getTasks();

        // Extension and layout roots
        final BundledRuntimeExtension ext = project.getExtensions().create("bundledRuntime", BundledRuntimeExtension.class);

        final Provider<Directory> bundledDir = project.getLayout().getBuildDirectory().dir("bundled");
        final Provider<Directory> runtimeDir = bundledDir.map(d -> d.dir("runtime"));
        final Provider<Directory> appDir     = bundledDir.map(d -> d.dir("app"));

        // Internal: prepareBundledApp
        final TaskProvider<PrepareAppTask> prepare = tasks.register(TASK_PREPARE, PrepareAppTask.class, t -> {
            // No task group -> hidden in `gradle tasks` output; orchestrated via public tasks.
            DirectoryProperty dest = t.getDestinationDir();
            dest.set(bundledDir);
            t.setDescription("Collects/assembles application artifacts into build/bundled/app.");
            // Dependencies to 'jar' / 'bootJar' are added below based on applied plugins.
        });

        // If Java plugin is present, make sure we build the plain JAR first (plain Java projects).
        project.getPluginManager().withPlugin("java", p ->
                prepare.configure(t -> t.dependsOn(tasks.named("jar")))
        );

        // If Spring Boot is present, also ensure 'bootJar' is produced (Boot fat-jar projects).
        project.getPluginManager().withPlugin("org.springframework.boot", p ->
                prepare.configure(t -> t.dependsOn(tasks.named("bootJar")))
        );

        // Internal: makeBundledRuntime (jlink)
        final TaskProvider<MakeRuntimeTask> jlink = tasks.register(TASK_JLINK, MakeRuntimeTask.class, t -> {
            t.getModules().set(ext.getModules());
            t.getJlinkOptions().set(ext.getJlinkOptions());
            t.getDestinationDir().set(runtimeDir);

            t.getSpringBootProject().set(project.getPlugins().hasPlugin("org.springframework.boot"));

            t.getAutoDetectModules().set(ext.getAutoDetectModules());
            t.getAppRoot().set(appDir);

            t.dependsOn(prepare);
            t.setDescription("Creates a minimized runtime image with jlink under build/bundled/runtime.");
            // Hidden (no group); orchestrated by public tasks.
        });

        // Internal: writeBundledLauncher
        final TaskProvider<WriteLauncherTask> launcher = tasks.register(TASK_LAUNCH, WriteLauncherTask.class, t -> {
            t.getLauncherName().set(ext.getLauncherName());
            t.getExitOnOome().set(ext.getExitOnOome());
            t.getBundledRoot().set(bundledDir);

            t.dependsOn(jlink);
            t.setDescription("Generates platform launcher scripts in build/bundled/bin.");
            // Hidden (no group); orchestrated by public tasks.
        });

        // Public: buildBundled (assemble distribution tree)
        tasks.register(TASK_BUILD_BUNDLED, task -> {
            task.setGroup(GROUP_DISTRIBUTION);
            task.setDescription("Builds app, detects modules/dependencies, creates jlink runtime, and writes launchers.");
            task.dependsOn(launcher); // launcher -> jlink -> prepare chain
        });

        // Public: zipBundled (ZIP of the assembled distribution)
        tasks.register(TASK_ZIP_BUNDLED, Zip.class, z -> {
            z.setGroup(GROUP_DISTRIBUTION);
            z.setDescription("Packages the bundled distribution into a ZIP archive.");
            z.dependsOn(tasks.named(TASK_BUILD_BUNDLED));

            z.from(bundledDir);
            z.getInputs().dir(bundledDir);

            z.getArchiveBaseName().set(project.getName() + "-bundled");
            z.getArchiveVersion().set(String.valueOf(project.getVersion()));
            z.getDestinationDirectory().set(
                    project.getLayout().getBuildDirectory().dir("distributions")
            );
        });
    }
}
