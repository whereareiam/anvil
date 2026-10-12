package me.whereareiam.anvil.integration.gradle.task

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Starts one scenario in the foreground, selected with `--scenario=<name>` or `--definition=<class>`.
 */
@DisableCachingByDefault(because = "The task intentionally starts long-running external server processes")
abstract class ScenarioRunTask : ScenarioRunnerTask() {
    /**
     * Scenario selected at the command line.
     */
    @get:Input
    @get:Optional
    abstract val scenario: Property<String>

    /**
     * Definition class selected at the command line.
     */
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

    /**
     * Selects a definition class with --definition=class.name.
     */
    @Option(option = "definition", description = "Scenario definition class")
    fun selectDefinition(value: String) {
        definition.set(value)
    }

    /**
     * Starts the selected scenario and keeps it running in the foreground.
     */
    @TaskAction
    fun runScenario() {
        if (scenario.isPresent == definition.isPresent)
            error("Select exactly one of --scenario=<name> or --definition=<class>")

        val selection = scenario.orNull?.let { "--scenario=$it" } ?: "--definition=${definition.get()}"
        invokeRunner(listOf(selection))
    }
}
