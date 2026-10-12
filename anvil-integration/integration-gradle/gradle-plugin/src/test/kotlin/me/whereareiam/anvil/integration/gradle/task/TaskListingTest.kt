package me.whereareiam.anvil.integration.gradle.task

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class TaskListingTest {
    @TempDir
    lateinit var directory: Path

    private val testedVersion = requireNotNull(System.getProperty("anvil.test.version"))

    @Test
    fun `lists the scenario and account tasks in the anvil group and hides tooling preparation`() {
        directory.resolve("settings.gradle.kts").writeText("rootProject.name = \"listing-fixture\"")
        directory.resolve("build.gradle.kts").writeText("plugins { id(\"me.whereareiam.anvil\") }")

        val output = GradleRunner.create()
            .withProjectDir(directory.toFile())
            .withPluginClasspath()
            .withArguments("tasks", "-PanvilVersion=$testedVersion", "--stacktrace")
            .build()
            .output
        val group = output.substringAfter("Anvil tasks\n-----------\n").substringBefore("\n\n")

        for (task in listOf("anvilLogin", "anvilLogout", "anvilRun", "anvilScenarios"))
            assertTrue(group.lines().any { it.startsWith("$task - ") }, output)
        assertFalse(output.contains("anvilTooling"), output)
    }
}
