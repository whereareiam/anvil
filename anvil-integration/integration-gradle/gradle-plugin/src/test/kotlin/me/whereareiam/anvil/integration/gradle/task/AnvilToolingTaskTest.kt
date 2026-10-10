package me.whereareiam.anvil.integration.gradle.task

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class AnvilToolingTaskTest {
    @TempDir
    lateinit var projectDirectory: Path

    private val testedVersion = requireNotNull(System.getProperty("anvil.test.version"))
    private val repository = Path.of(requireNotNull(System.getProperty("anvil.test.repository"))).toUri()

    @Test
    fun `exports compiled scenarios artifacts and independent runner runtime without credentials`() {
        writeProject()
        val result = runner().build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileAnvilJava")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar")?.outcome)
        val manifest = readManifest()
		assertEquals(1, manifest.path("schemaVersion").asInt())
		assertTrue(File(manifest.path("toolingJavaExecutable").asText()).isAbsolute)
        assertEquals(listOf("example.DemoScenario"), manifest.path("definitions").map(JsonNode::asText))

        val classpath = manifest.path("classpath").map(JsonNode::asText)
        assertTrue(classpath.all { File(it).isAbsolute })
        assertTrue(classpath.any { it.endsWith("tooling-runner-$testedVersion.jar") })
        assertTrue(classpath.any { it.endsWith("tooling-launcher-$testedVersion.jar") })
        assertTrue(classpath.any { it.endsWith("api-$testedVersion.jar") })
        assertTrue(classpath.any { it.endsWith("classes/java/anvil") })
        assertTrue(classpath.none { it.contains("gradleTestKit") })

        val properties = manifest.path("properties")
        assertEquals("true", properties.path("anvil.eula.accepted").asText())
        assertEquals("fixture-protocol", properties.path("anvil.protocolLibrary").asText())
        assertEquals("4", properties.path("anvil.parallelism").asText())
        assertEquals("2048", properties.path("anvil.startupMemoryMegabytes").asText())
        assertEquals("2", properties.path("anvil.downloadParallelism").asText())
        assertEquals("4", properties.path("anvil.processors").asText())
        assertEquals("low", properties.path("anvil.processPriority").asText())
        assertEquals("true", properties.path("anvil.offline").asText())
        assertEquals("false", properties.path("anvil.console.colors").asText())
        assertEquals("PT9S", properties.path("anvil.stopTimeout").asText())
        assertEquals("PT3M", properties.path("anvil.startupTimeout").asText())
        assertEquals(projectDirectory.resolve("build/libs/tooling-fixture.jar").toString(),
            properties.path("anvil.artifact.plugin").asText())
        assertFalse(properties.has("anvil.auth.token"))
        assertFalse(manifest.toString().contains("secret-token-fixture"))

		val command = mutableListOf(manifest.path("toolingJavaExecutable").asText())
        properties.properties().forEach { command += "-D${it.key}=${it.value.asText()}" }
        command += listOf("-cp", classpath.joinToString(File.pathSeparator),
            "me.whereareiam.anvil.tooling.launcher.AnvilCli", "--list")
        val output = projectDirectory.resolve("tooling-probe.log").toFile()
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output).start()
        val finished = process.waitFor(20, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        assertTrue(finished, "Independent tooling process did not finish")
        assertEquals(0, process.exitValue(), output.readText())
        assertTrue(output.readText().contains("independent-tooling-ready"))
    }

    @Test
    fun `reuses configuration cache and updates exported paths after DSL changes`() {
        writeProject()
        runner().build()
        val repeat = runner().build()
        assertTrue(repeat.output.contains("Reusing configuration cache"), repeat.output)
        assertEquals(TaskOutcome.UP_TO_DATE, repeat.task(":anvilTooling")?.outcome)

        projectDirectory.resolve("build.gradle.kts").toFile().appendText(
            "\nanvil { engine { workDirectory.set(layout.buildDirectory.dir(\"changed-work\")) } }\n")
        val changed = runner().build()
        assertEquals(TaskOutcome.SUCCESS, changed.task(":anvilTooling")?.outcome)
        assertEquals(projectDirectory.resolve("build/changed-work").toString(),
            readManifest().path("properties").path("anvil.workDir").asText())
        assertTrue(readManifest().path("classpath").any { it.asText().contains("tooling-builtin-") })
    }

    @Test
    fun `writes preparation to caller supplied absolute output path`() {
        writeProject()
        val output = projectDirectory.resolve("custom output/prepared.json")
        runner("--output-file=$output").build()
        val manifest = ObjectMapper().readTree(output.toFile())
        assertEquals(listOf("example.DemoScenario"), manifest.path("definitions").map(JsonNode::asText))
        assertFalse(projectDirectory.resolve("build/anvil/tooling.json").toFile().exists())
    }

    @Test
    fun `resolves project and module artifacts to their own jar without runtime dependencies`() {
        writeProject()
        write("settings.gradle.kts", "rootProject.name = \"tooling-fixture\"\ninclude(\"plugin\")")
        write("plugin/build.gradle.kts", """
            plugins { `java-library` }
            repositories { mavenCentral() }
            dependencies { implementation("com.google.code.gson:gson:2.11.0") }
        """.trimIndent())
        write("plugin/src/main/java/example/plugin/Marker.java", "package example.plugin; public final class Marker { }")
        projectDirectory.resolve("build.gradle.kts").toFile().appendText("""

            anvil {
                artifact("producer", project(":plugin"))
                artifact("module", "com.google.code.gson:gson:2.11.0")
            }
        """.trimIndent())

        runner().build()

        val properties = readManifest().path("properties")
        assertEquals(projectDirectory.resolve("plugin/build/libs/plugin.jar").toString(),
            properties.path("anvil.artifact.producer").asText())
        assertTrue(properties.path("anvil.artifact.module").asText().endsWith("gson-2.11.0.jar"),
            properties.path("anvil.artifact.module").asText())
    }

    private fun writeProject() {
        write("settings.gradle.kts", "rootProject.name = \"tooling-fixture\"")
        write("build.gradle.kts", """
            plugins { id("me.whereareiam.anvil") }
            repositories { maven { url = uri("$repository") }; mavenCentral() }
            anvil {
                acceptEula()
                engine {
                    protocolLibrary("fixture-protocol")
                    parallelism.set(4)
                    startupMemoryMegabytes.set(2048)
                    downloadParallelism.set(2)
                    processors.set(4)
                    processPriority.set("LOW")
                }
                artifact("plugin", tasks.named("jar"))
            }
        """.trimIndent())
        write("src/anvil/java/example/DemoScenario.java", """
            package example;
            import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
            import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
            public final class DemoScenario implements AnvilScenarioDefinition {
                public AnvilScenario define() {
                    if (!"fixture-protocol".equals(System.getProperty("anvil.protocolLibrary")))
                        throw new AssertionError("Engine properties were not forwarded");
                    return AnvilScenario.builder().name("independent-tooling-ready").entrypoint("server").build();
                }
            }
        """.trimIndent())
    }

    private fun write(path: String, content: String) {
        projectDirectory.resolve(path).also { it.parent.createDirectories() }.writeText(content)
    }

    private fun readManifest(): JsonNode =
        ObjectMapper().readTree(projectDirectory.resolve("build/anvil/tooling.json").toFile())

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDirectory.toFile())
        .withPluginClasspath()
        .withArguments("anvilTooling", *arguments, "-PanvilVersion=$testedVersion", "-Danvil.offline=true", "-Danvil.stopTimeout=PT9S", "-Danvil.startupTimeout=PT3M",
            "-Danvil.auth.token=secret-token-fixture", "-Danvil.console.colors=false", "--configuration-cache", "--stacktrace")
}
