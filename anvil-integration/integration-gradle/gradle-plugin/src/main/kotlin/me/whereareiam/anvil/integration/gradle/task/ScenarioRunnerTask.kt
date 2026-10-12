package me.whereareiam.anvil.integration.gradle.task

import me.whereareiam.anvil.integration.gradle.artifact.provider.ArtifactJvmArgumentProvider
import me.whereareiam.anvil.tooling.gradle.ScenarioDefinitionScanner
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Runs the scenario runner over the project's scenario definitions in a separate JVM.
 *
 * [ScenarioListTask] lists the definitions and [ScenarioRunTask] starts one of them in the foreground.
 */
@DisableCachingByDefault(because = "The task intentionally starts long-running external server processes")
abstract class ScenarioRunnerTask : DefaultTask() {
    /**
     * Complete project runtime shared with the declared tooling project.
     */
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    /** Project-owned compiled classes scanned for scenario definitions. */
    @get:Classpath
    abstract val scenarioClasses: ConfigurableFileCollection

    /** Project-owned resources containing optional generated definition entries. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scenarioResources: ConfigurableFileCollection

    /**
     * Explicit engine and scenario properties, including resolved named artifacts.
     */
    @get:Input
    abstract val engineProperties: MapProperty<String, String>

    /**
     * Artifact outputs whose producing tasks must finish before scenario execution.
     */
    @get:Nested
    abstract val artifacts: ListProperty<ArtifactJvmArgumentProvider>

    /**
     * Project Java toolchain used by the independent foreground JVM.
     */
    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @get:Inject
    protected abstract val processes: ExecOperations

    /**
     * Runs the scenario runner with the given command-line arguments.
     */
    protected fun invokeRunner(arguments: List<String>) {
        val index = temporaryDir.toPath().resolve("scenario-index")
        val definitions = ScenarioDefinitionScanner.scan(scenarioClasses, scenarioResources)

        ScenarioDefinitionScanner.writeIndex(index, definitions)
        processes.javaexec {
            executable = javaLauncher.get().executablePath.asFile.absolutePath
            classpath(runtimeClasspath)
            classpath(index.toFile())
            mainClass.set("me.whereareiam.anvil.tooling.launcher.AnvilCli")
            args(arguments)
            systemProperties(engineProperties.get())
            jvmArgumentProviders.addAll(artifacts.get())
            standardInput = System.`in`
            standardOutput = System.out
            errorOutput = System.err
        }.assertNormalExitValue()
    }
}
