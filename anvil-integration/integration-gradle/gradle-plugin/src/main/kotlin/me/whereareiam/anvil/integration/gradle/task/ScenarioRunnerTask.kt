package me.whereareiam.anvil.integration.gradle.task

import me.whereareiam.anvil.integration.gradle.artifact.provider.ArtifactJvmArgumentProvider
import me.whereareiam.anvil.tooling.gradle.ScenarioDefinitionScanner
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import javax.inject.Inject
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault


/**
 * Lists discovered scenarios or runs one foreground scenario.
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
     * Scenario selected at the command line.
     */
    @get:Input
    @get:Optional
    abstract val scenario: Property<String>

    /** Whether to list discovered scenarios instead of starting one. */
    @get:Input
    @get:Optional
    abstract val list: Property<Boolean>

    /** Optional definition class selected at the command line. */
    @get:Input
    @get:Optional
    abstract val definition: Property<String>

    /**
     * Selects a scenario with --scenario=name.
     */
    @Option(option = "scenario", description = "Named scenario to start")
    fun selectScenario(value: String) {
        scenario.set(value)
    }

    /** Lists discovered scenarios with --list. */
    @Option(option = "list", description = "Lists discovered scenarios")
    fun selectList(value: Boolean) {
        list.set(value)
    }

    /** Selects a definition class with --definition=class.name. */
    @Option(option = "definition", description = "Scenario definition class")
    fun selectDefinition(value: String) {
        definition.set(value)
    }

    /**
     * Lists or starts scenarios using the project runtime classpath.
     */
    @TaskAction
    fun runScenario() {
        if (list.orNull == true) {
            if (scenario.isPresent || definition.isPresent)
                error("Select --list or exactly one of --scenario=<name> or --definition=<class>")
            invokeRunner(listOf("--list"))
            return
        }

        if (scenario.isPresent == definition.isPresent)
            error("Select exactly one of --scenario=<name> or --definition=<class>")

        val arguments = mutableListOf<String>()
        scenario.orNull?.let { arguments += "--scenario=$it" }
        definition.orNull?.let { arguments += "--definition=$it" }
        invokeRunner(arguments)
    }

    private fun invokeRunner(arguments: List<String>) {
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
