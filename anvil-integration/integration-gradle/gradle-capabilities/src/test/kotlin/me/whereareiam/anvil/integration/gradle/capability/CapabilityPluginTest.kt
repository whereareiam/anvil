package me.whereareiam.anvil.integration.gradle.capability

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class CapabilityPluginTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `session plugin adds capability wiring to the anvil source set`() {
        projectDirectory.resolve("settings.gradle.kts").writeText("rootProject.name = \"capability-fixture\"")
        projectDirectory.resolve("build.gradle.kts").writeText("""
            plugins {
                id("me.whereareiam.anvil.capability.session")
            }
            check(configurations.names.none { it in setOf("anvilCapabilities", "anvilUnits") })
            tasks.register("verify") {
                doLast {
                    val dependencies = configurations.getByName("anvilImplementation").allDependencies
                        .map { it.name }
                    check("builtin-session" in dependencies)
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withPluginClasspath()
            .withArguments("verify")
            .build()
        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
    }
}
