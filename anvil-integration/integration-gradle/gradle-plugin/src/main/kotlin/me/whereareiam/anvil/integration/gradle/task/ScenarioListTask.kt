package me.whereareiam.anvil.integration.gradle.task

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Lists the scenarios discovered in the `anvil` source set without starting them.
 */
@DisableCachingByDefault(because = "The listing is printed to the console, not written to an output")
abstract class ScenarioListTask : ScenarioRunnerTask() {
    /**
     * Prints the discovered scenarios.
     */
    @TaskAction
    fun listScenarios() {
        invokeRunner(listOf("--list"))
    }
}
