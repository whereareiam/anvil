package me.whereareiam.anvil.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class AnvilPluginTest {
    private val testedVersion = requireNotNull(System.getProperty("anvil.test.version"))
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `combined plugin installs both workflows and reuses configuration cache`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil")
            }
            """.trimIndent()
        )

        val first = runner("tasks", "--all", "--configuration-cache").build()
        assertContains(first.output, "anvilTest")
        assertContains(first.output, "anvilScenario")
        assertContains(first.output, "anvilLogin")
        assertContains(first.output, "anvilLogout")
        assertFalse(first.output.contains("anvilScenarios"), first.output)
        assertFalse(first.output.contains("runAnvilScenario"), first.output)

        val second = runner("tasks", "--all", "--configuration-cache").build()
        assertContains(second.output, "Reusing configuration cache")
    }

    @Test
    fun `junit plugin installs only automated execution`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil.junit")
            }
            """.trimIndent()
        )

        val result = runner("tasks", "--all").build()
        assertContains(result.output, "anvilTest")
        assertFalse(result.output.contains("anvilScenario"), result.output)
    }

    @Test
    fun `scenarios plugin installs only foreground execution`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil.scenarios")
            }
            """.trimIndent()
        )

        val result = runner("tasks", "--all").build()
        assertContains(result.output, "anvilScenario")
        assertFalse(result.output.contains("anvilTest"), result.output)
    }

    @Test
    fun `consumer compile classpath excludes runtime infrastructure`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil.scenarios")
            }
            """.trimIndent()
        )

        val result = runner("dependencies", "--configuration", "anvilCompileClasspath").build()
        assertContains(result.output, "me.whereareiam.anvil:api:$testedVersion")
        assertFalse(result.output.contains("protocol-mcprotocol"), result.output)
        assertFalse(result.output.contains("mcprotocollib"), result.output)
        assertFalse(result.output.contains("platform-paper"), result.output)
        assertFalse(result.output.contains("platform-bukkit-agent"), result.output)
    }

    @Test
    fun `combined plugin includes built-in capabilities but no platform`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil")
            }
            """.trimIndent()
        )

        val result = runner("dependencies", "--configuration", "anvilCapabilities").build()
        assertContains(result.output, "me.whereareiam.anvil:default:$testedVersion")

        val platforms = runner("dependencies", "--configuration", "anvilPlatforms").build()
        assertContains(platforms.output, "No dependencies")
    }

    @Test
    fun `capability unit plugin selects its wiring`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil.scenarios")
                id("me.whereareiam.anvil.capability.inventory")
            }
            """.trimIndent()
        )

        val result = runner("dependencies", "--configuration", "anvilCapabilities").build()
        assertContains(result.output, "me.whereareiam.anvil:builtin-inventory:$testedVersion")
    }

    @Test
    fun `platform declarations install providers and agents`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil")
                id("me.whereareiam.anvil.platform.paper")
                id("me.whereareiam.anvil.platform.velocity")
            }
            """.trimIndent()
        )

        val result = runner("dependencies", "--configuration", "anvilPlatforms").build()
        assertContains(result.output, "me.whereareiam.anvil:platform-paper-provider:$testedVersion")
        assertContains(result.output, "me.whereareiam.anvil:platform-bukkit-agent:$testedVersion")
        assertContains(result.output, "me.whereareiam.anvil:platform-velocity-provider:$testedVersion")
        assertContains(result.output, "me.whereareiam.anvil:platform-velocity-agent:$testedVersion")
    }

    @Test
    fun `individual and external capabilities are selected explicitly`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil.scenarios")
                id("me.whereareiam.anvil.capability.session")
                id("me.whereareiam.anvil.capability.server")
            }

            dependencies {
                anvilCapabilities("example:external-capability:1.2.3")
            }
            """.trimIndent()
        )

        val result = runner("dependencies", "--configuration", "anvilCapabilities").build()
        assertContains(result.output, "me.whereareiam.anvil:builtin-session:$testedVersion")
        assertContains(result.output, "me.whereareiam.anvil:builtin-server:$testedVersion")
        assertContains(result.output, "example:external-capability:1.2.3")
        assertFalse(result.output.contains("me.whereareiam.anvil:default:"), result.output)
    }

    @Test
    fun `short eula acceptance configures the runtime`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil.scenarios")
            }

            anvil {
                acceptEula()
            }

            tasks.register("printEula") {
                doLast {
                    println("anvil-eula=" + anvil.eulaAccepted.get())
                }
            }
            """.trimIndent()
        )

        val result = runner("printEula").build()
        assertContains(result.output, "anvil-eula=true")
    }

    @Test
    fun `discovers scenarios from the dedicated source set`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil.scenarios")
            }

            anvil {
                scenarioProviders.add("example.Scenarios")
            }

            ${testClasspathConfiguration()}
            """.trimIndent()
        )
        writeJava(
            "example/Scenarios.java",
            """
            package example;

            import me.whereareiam.anvil.api.model.process.Distribution;
            import me.whereareiam.anvil.api.model.process.MinecraftServer;
            import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
            import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
            import me.whereareiam.anvil.api.scenario.AnvilScenarioProvider;
            import me.whereareiam.anvil.api.scenario.ScenarioRegistry;

            public final class Scenarios implements AnvilScenarioProvider {
                @Override
                public void register(ScenarioRegistry registry) {
                    MinecraftServer server = MinecraftServer.builder()
                        .name("server")
                        .platform("test")
                        .distribution(Distribution.remote("1.21.11", "1"))
                        .build();
                    registry.scenario(AnvilScenario.builder()
                        .name("manual-demo")
                        .entrypoint(server.getName())
                        .server(server)
                        .manual(true)
                        .build());
                    registry.group(ScenarioGroup.builder()
                        .name("demos")
                        .scenario("manual-demo")
                        .build());
                }
            }
            """.trimIndent()
        )

        val result = runner("anvilScenario", "--list", "--configuration-cache").build()
        assertContains(result.output, "manual-demo (manual)")
        assertContains(result.output, "demos -> [manual-demo]")
        assertEquals(TaskOutcome.SUCCESS, result.task(":anvilScenario")?.outcome)
    }

    @Test
    fun `validates command line selection before launch`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil.scenarios")
            }

            anvil {
                scenarioProviders.add("unused.Provider")
            }

            ${testClasspathConfiguration()}
            """.trimIndent()
        )

        val result = runner("anvilScenario", "--scenario=one", "--group=both").buildAndFail()
        assertContains(result.output, "Select exactly one of --scenario=<name> or --group=<name>")
    }

    @Test
    fun `project artifact is built before eula preflight rejects the scenario`() {
        projectDirectory.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"fixture\"\ninclude(\":server\")\n"
        )
        projectDirectory.resolve("server").createDirectories()
        projectDirectory.resolve("server/build.gradle.kts").writeText("plugins { java }\n")
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil.junit")
            }

            anvil {
                artifact("server", project(":server"))
            }

            ${testClasspathConfiguration()}
            """.trimIndent()
        )
        writeEulaScenario()

        val result = runner("anvilTest", "--configuration-cache").buildAndFail()
        assertContains(result.output, "Mojang EULA acceptance is required")
        assertEquals(TaskOutcome.SUCCESS, result.task(":server:jar")?.outcome)
    }

    @Test
    fun `maven artifact resolves before scenario preflight`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                id("me.whereareiam.anvil.junit")
            }

            repositories {
                mavenCentral()
            }

            anvil {
                artifact("server", "org.jetbrains:annotations:26.0.2-1")
            }

            ${testClasspathConfiguration()}
            """.trimIndent()
        )
        writeEulaScenario()

        val result = runner("anvilTest", "--configuration-cache").buildAndFail()
        assertContains(result.output, "Mojang EULA acceptance is required")
    }

    private fun writeEulaScenario() {
        writeJava("example/EulaProtocolProvider.java", """
            package example;
            import java.nio.file.Path;
            import me.whereareiam.anvil.protocol.api.provider.ProtocolProvider;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactResolver;
            import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
            public final class EulaProtocolProvider implements ProtocolProvider {
                public String id() { return "eula-fixture"; }
                public ProtocolBackend create(Path cache, ArtifactResolver artifacts) {
                    throw new AssertionError("EULA validation must happen before backend creation");
                }
            }
        """.trimIndent())
        projectDirectory.resolve("src/anvil/resources/META-INF/services/me.whereareiam.anvil.protocol.api.provider.ProtocolProvider")
            .also { it.parent.createDirectories() }.writeText("example.EulaProtocolProvider\n")
        writeJava(
            "example/EulaScenario.java",
            """
            package example;

            import me.whereareiam.anvil.api.model.process.Distribution;
            import me.whereareiam.anvil.api.model.process.MinecraftServer;
            import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
            import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
            import me.whereareiam.anvil.api.type.Platforms;

            public final class EulaScenario implements AnvilScenarioDefinition {
                @Override
                public AnvilScenario define() {
                    MinecraftServer server = MinecraftServer.builder()
                        .name("server")
                        .platform(Platforms.PAPER)
                        .minecraftVersion("1.21.11")
                        .distribution(Distribution.artifact("server"))
                        .build();
                    return AnvilScenario.builder()
                        .name("eula")
                        .entrypoint(server.getName())
                        .server(server)
                        .build();
                }
            }
            """.trimIndent()
        )
        writeJava(
            "example/EulaTest.java",
            """
            package example;

            import me.whereareiam.anvil.junit.AnvilTest;
            import org.junit.jupiter.api.Test;

            final class EulaTest {
                @Test
                @AnvilTest(EulaScenario.class)
                void rejected() {
                }
            }
            """.trimIndent()
        )
    }

    private fun testClasspathConfiguration(): String =
        """
        configurations.named("anvilFramework") {
            dependencies.clear()
        }
        configurations.named("anvilLauncher") {
            dependencies.clear()
        }

        dependencies {
            add("anvilFramework", files(${quotedPluginClasspath()}))
        }
        """.trimIndent()

    private fun writeSettings() {
        projectDirectory.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
    }

    private fun writeBuild(content: String) {
        projectDirectory.resolve("build.gradle.kts").writeText(content)
    }

    private fun writeJava(relative: String, content: String) {
        val target = projectDirectory.resolve("src/anvil/java").resolve(relative)
        target.parent.createDirectories()
        target.writeText(content)
    }

    private fun quotedPluginClasspath(): String {
        val properties = Properties()
        javaClass.classLoader.getResourceAsStream("plugin-under-test-metadata.properties").use {
            properties.load(it)
        }
        val entries = properties.getProperty("implementation-classpath")
            .split(File.pathSeparator)
            .toMutableList()
        entries += Path.of(
            Class.forName("org.junit.platform.launcher.Launcher")
                .protectionDomain.codeSource.location.toURI()
        ).toString()
        entries += Path.of(
            Class.forName("me.whereareiam.anvil.junit.AnvilTest")
                .protectionDomain.codeSource.location.toURI()
        ).toString()
        entries += Path.of(
            Class.forName("me.whereareiam.anvil.provisioning.api.artifact.ArtifactResolver")
                .protectionDomain.codeSource.location.toURI()
        ).toString()
        entries += Path.of(
            Class.forName("org.junit.jupiter.api.Test")
                .protectionDomain.codeSource.location.toURI()
        ).toString()
        entries += Path.of(
            Class.forName("org.junit.jupiter.engine.JupiterTestEngine")
                .protectionDomain.codeSource.location.toURI()
        ).toString()
        entries += Path.of(
            Class.forName("org.junit.platform.engine.ConfigurationParameters")
                .protectionDomain.codeSource.location.toURI()
        ).toString()
        entries += Path.of(
            Class.forName("org.junit.platform.commons.util.Preconditions")
                .protectionDomain.codeSource.location.toURI()
        ).toString()
        entries += Path.of(
            Class.forName("org.opentest4j.TestAbortedException")
                .protectionDomain.codeSource.location.toURI()
        ).toString()
        return entries.distinct()
            .joinToString(", ") { path -> "\"${path.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }
    }

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDirectory.toFile())
        .withArguments("--stacktrace", "-PanvilVersion=$testedVersion", *arguments)
        .withPluginClasspath()

    private fun assertContains(actual: String, expected: String) {
        assertTrue(actual.contains(expected), "Expected output to contain '$expected':\n$actual")
    }
}
