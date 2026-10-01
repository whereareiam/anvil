package me.whereareiam.anvil.integration.gradle.task

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class ForegroundScenarioTaskTest {
    @TempDir
    lateinit var directory: Path

    private val testedVersion = requireNotNull(System.getProperty("anvil.test.version"))
    private val repository = Path.of(requireNotNull(System.getProperty("anvil.test.repository"))).toUri()

    @Test
    fun `real foreground child preserves default listing and explicit engine properties without changing the daemon`() {
        write("settings.gradle.kts", "rootProject.name = \"foreground-fixture\"")
        write("build.gradle.kts", """
            plugins { id("me.whereareiam.anvil") }
            repositories { maven { url = uri("$repository") }; mavenCentral() }
            anvil { }
            tasks.register("verifyDaemon") {
                mustRunAfter("anvilScenario")
                doLast {
                    check(System.getProperty("fixture.selection") == null) {
                        "Scenario properties must remain in the child JVM"
                    }
                }
            }
        """.trimIndent())
        write("src/anvil/java/example/Catalog.java", """
            package example;
            import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
            import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
            public final class Catalog implements AnvilScenarioDefinition {
                public AnvilScenario define() {
                    return AnvilScenario.builder()
                        .name(System.getProperty("fixture.selection", "catalog-default"))
                        .entrypoint("server").build();
                }
            }
        """.trimIndent())
        val defaultRun = runner().build()
        assertTrue(defaultRun.output.contains("catalog-default"), defaultRun.output)

        directory.resolve("build.gradle.kts").toFile().appendText("\n\n" + """

            anvil { engine { properties.put("fixture.selection", "explicit-catalog") } }
        """.trimIndent())
        val explicit = runner().build()
        assertTrue(explicit.output.contains("explicit-catalog"), explicit.output)
        val cached = runner().build()
        assertTrue(cached.output.contains("Reusing configuration cache"), cached.output)
        assertTrue(cached.output.contains("explicit-catalog"), cached.output)
    }

    @Test
    fun `lists only anvil source set definitions without compiling ordinary tests`() {
        write("settings.gradle.kts", "rootProject.name = \"foreground-fixture\"")
        write("build.gradle.kts", """
            plugins { id("me.whereareiam.anvil") }
            repositories { maven { url = uri("$repository") }; mavenCentral() }
        """.trimIndent())
        write("src/anvil/java/example/Catalog.java", definition("Catalog", "anvil-scenario"))
        write("src/test/java/example/TestOnly.java", definition("TestOnly", "test-only-scenario"))

        val result = selection("--list").build()

        assertTrue(result.output.contains("anvil-scenario"), result.output)
        assertFalse(result.output.contains("test-only-scenario"), result.output)
        assertNull(result.task(":compileTestJava"), result.output)
    }

    @Test
    fun `rejects ambiguous or missing scenario selections before starting a runner`() {
        write("settings.gradle.kts", "rootProject.name = \"foreground-fixture\"")
        write("build.gradle.kts", """
            plugins { id("me.whereareiam.anvil") }
            repositories { maven { url = uri("$repository") }; mavenCentral() }
        """.trimIndent())

        val missing = selection().buildAndFail()
        assertTrue(missing.output.contains("Select exactly one of --scenario=<name> or --definition=<class>"), missing.output)

        val both = selection("--scenario=one", "--definition=example.Two").buildAndFail()
        assertTrue(both.output.contains("Select exactly one of --scenario=<name> or --definition=<class>"), both.output)

        val listing = selection("--list", "--scenario=one").buildAndFail()
        assertTrue(listing.output.contains("Select --list or exactly one of"), listing.output)
    }

    private fun definition(name: String, scenario: String) = """
        package example;
        import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
        import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
        public final class $name implements AnvilScenarioDefinition {
            public AnvilScenario define() {
                return AnvilScenario.builder().name("$scenario").entrypoint("server").build();
            }
        }
    """.trimIndent()

    private fun selection(vararg options: String): GradleRunner = GradleRunner.create()
        .withProjectDir(directory.toFile())
        .withPluginClasspath()
        .withArguments("anvilScenario", *options, "-PanvilVersion=$testedVersion", "--stacktrace")

    private fun runner(): GradleRunner = GradleRunner.create()
        .withProjectDir(directory.toFile())
        .withPluginClasspath()
        .withArguments("anvilScenario", "--list", "verifyDaemon", "-PanvilVersion=$testedVersion", "--configuration-cache", "--stacktrace")

    private fun write(file: String, content: String) {
        directory.resolve(file).also { it.parent.createDirectories() }.writeText(content)
    }
}
