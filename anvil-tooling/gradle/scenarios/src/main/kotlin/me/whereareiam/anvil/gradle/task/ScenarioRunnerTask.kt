package me.whereareiam.anvil.gradle.task

import me.whereareiam.anvil.gradle.internal.AnvilPluginNames
import me.whereareiam.anvil.runner.AnvilRunner
import me.whereareiam.anvil.runner.model.AnvilRunnerConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import org.gradle.work.DisableCachingByDefault

/** Lists registered scenarios or runs one foreground scenario or interactive scenario group. */
@DisableCachingByDefault(because = "The task intentionally starts long-running external server processes")
abstract class ScenarioRunnerTask : DefaultTask() {
    /** Runtime classpath containing application scenario providers. */
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    /** Configured provider class names. */
    @get:Input
    abstract val providers: ListProperty<String>

    /** Explicit EULA acceptance propagated to the runner. */
    @get:Input
    abstract val eulaAccepted: Property<Boolean>

    /** Shared immutable cache and authentication root. */
    @get:Internal
    abstract val cacheDirectory: DirectoryProperty

    /** Disposable workspace root. */
    @get:Internal
    abstract val workDirectory: DirectoryProperty

    /** Selected protocol-provider identifier. */
    @get:Input
    @get:Optional
    abstract val protocolId: Property<String>

    /** Server and worker Java executables keyed by feature version. */
    @get:Input
    abstract val javaExecutables: MapProperty<Int, String>

    /** Resolved project or Maven artifacts referenced by scenarios. */
    @get:Classpath
    abstract val artifactFiles: ConfigurableFileCollection

    /** Stable artifact names mapped to their resolved local paths. */
    @get:Input
    abstract val artifactPaths: MapProperty<String, String>

    /** Scenario selected at the command line. */
    @get:Input
    @get:Optional
    abstract val scenario: Property<String>

    /** Whether to list registered scenarios and groups instead of starting one. */
    @get:Input
    @get:Optional
    abstract val list: Property<Boolean>

    /** Group selected at the command line. */
    @get:Input
    @get:Optional
    abstract val scenarioGroup: Property<String>

    /** Optional provider override when multiple providers are registered. */
    @get:Input
    @get:Optional
    abstract val provider: Property<String>

    /** Selects a scenario with --scenario=name. */
    @Option(option = AnvilPluginNames.SCENARIO_OPTION, description = "Named scenario to start")
    fun selectScenario(value: String) {
        scenario.set(value)
    }

    /** Lists registered scenarios and groups with --list. */
    @Option(option = AnvilPluginNames.LIST_OPTION, description = "Lists registered scenarios and groups")
    fun selectList(value: Boolean) {
        list.set(value)
    }

    /** Selects a scenario group with --group=name. */
    @Option(option = AnvilPluginNames.GROUP_OPTION, description = "Named interactive scenario group to start")
    fun selectGroup(value: String) {
        scenarioGroup.set(value)
    }

    /** Selects one registered provider with --provider=class.name. */
    @Option(option = AnvilPluginNames.PROVIDER_OPTION, description = "Scenario provider class")
    fun selectProvider(value: String) {
        provider.set(value)
    }

    /** Lists or starts scenarios using the project runtime classpath. */
    @TaskAction
    fun runScenario() {
        if (list.orNull == true) {
            if (scenario.isPresent || scenarioGroup.isPresent)
                error("Select --list or exactly one of --scenario=<name> or --group=<name>")
            val selectedProviders = provider.orNull?.let(::listOf) ?: providers.get()
            if (selectedProviders.isEmpty())
                error("Configure at least one anvil.scenarioProviders entry")
            selectedProviders.forEach { selectedProvider ->
                invokeRunner(listOf("--provider=$selectedProvider", "--list"))
            }
            return
        }

        if (scenario.isPresent == scenarioGroup.isPresent)
            error("Select exactly one of --scenario=<name> or --group=<name>")

        val selectedProvider = provider.orNull
            ?: providers.orNull?.singleOrNull()
            ?: error("Configure exactly one anvil.scenarioProviders entry or pass --provider")
        val arguments = mutableListOf("--provider=$selectedProvider")
        scenario.orNull?.let { arguments += "--scenario=$it" }
        scenarioGroup.orNull?.let { arguments += "--group=$it" }
        invokeRunner(arguments)
    }

    internal fun invokeRunner(arguments: List<String>) {
        val thread = Thread.currentThread()
        val previousLoader = thread.contextClassLoader
        URLClassLoader(
            runtimeClasspath.files.map { it.toURI().toURL() }.toTypedArray(),
            previousLoader,
        ).use { loader ->
            try {
                thread.contextClassLoader = loader
                AnvilRunner(
                    InputStreamReader(System.`in`, StandardCharsets.UTF_8),
                    PrintWriter(System.out, true),
                ).run(arguments.toTypedArray(), runnerConfiguration())
            } finally {
                thread.contextClassLoader = previousLoader
            }
        }
    }

    private fun runnerConfiguration(): AnvilRunnerConfiguration {
        val builder = AnvilRunnerConfiguration.builder()
            .eulaAccepted(eulaAccepted.get())
            .cacheDirectory(cacheDirectory.get().asFile.toPath())
            .workDirectory(workDirectory.get().asFile.toPath())

        protocolId.orNull?.let(builder::protocolId)
        javaExecutables.get().forEach { (version, executable) ->
            builder.javaExecutable(version, Path.of(executable))
        }
        artifactPaths.get().forEach { (name, path) ->
            builder.artifact(name, Path.of(path))
        }
        return builder.build()
    }
}
