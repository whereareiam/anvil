package me.whereareiam.anvil.buildlogic

import me.whereareiam.anvil.buildlogic.LibraryRepository.LibraryClass
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

/**
 * Builds a consumer project whose `demo-mcprotocol` side folder holds the segments `V1_18_2` and `V1_20_6`,
 * compiled against generated library releases from a file repository inside the project.
 */
class SegmentConventionsFunctionalTest {
	@TempDir
	lateinit var directory: File

	@Test
	fun `segments compile per release, describe themselves, publish uniquely and check linkage`() {
		val project = TestProject(directory)
		writeProject(project)

		val build = project.run("build", "publishAllPublicationsToFunctionalRepository")
		assertEquals(TaskOutcome.SUCCESS, build.task(":demo-mcprotocol:V1_18_2:checkSegmentLinkage")?.outcome, build.output)
		assertEquals(
			"compiled against 1.18.2-1; 5 library references\nlater releases: 1.19.4-1\n",
			project.file("demo-mcprotocol/V1_18_2/build/reports/segment/linkage.txt").readText(),
		)
		assertTrue(project.file("demo-mcprotocol/V1_20_6/build/reports/segment/linkage.txt").readText().contains("later releases: none"))

		val early = project.dependencies(":demo-mcprotocol:V1_18_2", "compileClasspath")
		assertTrue(early.contains("com.example.legacy:protocol:1.18.2-1"), early)
		assertFalse(early.contains("com.example.modern:protocol"), early)
		val modern = project.dependencies(":demo-mcprotocol:V1_20_6", "compileClasspath")
		assertTrue(modern.contains("com.example.modern:protocol:1.20.6-1"), modern)
		assertFalse(modern.contains("com.example.legacy:protocol"), modern)

		val runtime = project.dependencies(":consumer", "runtimeClasspath")
		assertTrue(runtime.contains(":demo-mcprotocol:V1_18_2'") && runtime.contains(":demo-mcprotocol:V1_20_6'"), runtime)
		assertFalse(runtime.contains("com.example"), runtime)

		val jar = "demo-mcprotocol/V1_18_2/build/libs/demo-mcprotocol-V1_18_2-1.0.0.jar"
		assertEquals("library=mcprotocol\nowner=demo-mcprotocol\nsince=1.18.2\n", project.jarText(jar, "META-INF/anvil/segment.properties"))
		assertEquals(
			listOf(
				"class com.example.auth.GameProfile public",
				"method com.example.auth.GameProfile getName()Ljava/lang/String; public instance class",
				"class com.example.legacy.MinecraftProtocol public class",
				"method com.example.legacy.MinecraftProtocol <init>(Ljava/lang/String;)V public instance class",
				"method com.example.legacy.MinecraftProtocol getProfile()Lcom/example/auth/GameProfile; public instance class",
			),
			project.jarText(jar, "META-INF/anvil/segment/linkage.txt").lines().filter(String::isNotEmpty),
		)
		val published = project.file("repository-out/me/whereareiam/anvil/demo-mcprotocol")
		assertTrue(File(published, "V1_18_2/1.0.0/V1_18_2-1.0.0.pom").isFile, published.walkTopDown().joinToString())
		assertTrue(File(published, "V1_20_6/1.0.0/V1_20_6-1.0.0.pom").isFile, published.walkTopDown().joinToString())

		val reused = project.run("build", "publishAllPublicationsToFunctionalRepository")
		assertTrue(reused.output.contains("Configuration cache entry reused"), reused.output)

		// ChatPacket(String) exists in 1.18.2-1 but not in 1.19.4-1, which this segment still serves.
		segmentSource(project, "1_18_2", "com.example.legacy", """
			public Object chat(String message) {
				return new com.example.legacy.ChatPacket(message);
			}
		""")
		val broken = project.fail(":demo-mcprotocol:V1_18_2:checkSegmentLinkage")
		assertTrue(broken.output.contains(
			"1.19.4-1 (Minecraft 1.19.4): missing method com.example.legacy.ChatPacket <init>(Ljava/lang/String;)V"
		), broken.output)
		segmentSource(project, "1_18_2", "com.example.legacy", "")

		// Workers load the locked closure, so a closure without the auth JAR breaks the segment's own release.
		val releases = project.file("anvil-protocol/protocol-mcprotocol/mcprotocol-releases.toml")
		val locked = releases.readText()
		releases.writeText(locked.replaceFirst(closureTable("com.example:auth:1.0"), "").trimEnd() + "\n")
		val unlocked = project.fail(":demo-mcprotocol:V1_18_2:checkSegmentLinkage")
		assertTrue(unlocked.output.contains("closure that release data locks for its own release 1.18.2-1"), unlocked.output)
		assertTrue(unlocked.output.contains("1.18.2-1 (Minecraft 1.18.2): missing class com.example.auth.GameProfile"), unlocked.output)
		releases.writeText(locked)

		project.write("demo-mcprotocol/V1_20_6/src/main/java/demo/mcprotocol/Shared.java", "package demo.mcprotocol;\n\npublic final class Shared {\n}\n")
		val misplaced = project.fail(":demo-mcprotocol:V1_20_6:compileJava")
		assertTrue(misplaced.output.contains("Segment classes must live in a package ending in .v1_20_6: demo.mcprotocol.Shared"), misplaced.output)
		project.file("demo-mcprotocol/V1_20_6/src/main/java/demo/mcprotocol/Shared.java").delete()

		// Wiring in the side folder takes adapters from the worker, never from a service lookup of its own.
		val wiring = "demo-mcprotocol/src/main/java/demo/mcprotocol/DemoWiring.java"
		project.write(wiring, """
			package demo.mcprotocol;

			public final class DemoWiring {
				public Object packets() {
					return java.util.ServiceLoader.load(demo.api.packet.DemoPackets.class).findFirst();
				}
			}
		""")
		project.write("demo-mcprotocol/build.gradle.kts", "plugins {\n\tid(\"segmented\")\n}\n\ndependencies {\n\tcompileOnly(project(\":demo-api\"))\n}\n")
		val lookup = project.fail(":demo-mcprotocol:check")
		assertTrue(lookup.output.contains("must obtain adapters through PlayerBindingContext.adapter(Class), not java.util.ServiceLoader: "
			+ "demo.mcprotocol.DemoWiring"), lookup.output)
		project.file(wiring).delete()

		project.write("demo-mcprotocol/build.gradle.kts", "plugins {\n\tid(\"jvm\")\n}\n")
		val unexported = project.fail("help")
		assertTrue(unexported.output.contains(
			":demo-mcprotocol holds segments, which reach a worker only through their library side folder; it must apply id(\"segmented\")"
		), unexported.output)
		project.write("demo-mcprotocol/build.gradle.kts", "plugins {\n\tid(\"segmented\")\n}\n")

		// Worker tests of the library receive each release's locked closure, in release data order, from a file
		// without a timestamp, which stays the same while the closures do. The file is ASCII, as the test JVM's
		// Properties.load(InputStream) reads it.
		project.run(":anvil-protocol:protocol-mcprotocol:mcprotocol-common:writeReleaseClosures")
		val closures = project.file("anvil-protocol/protocol-mcprotocol/mcprotocol-common/build/release-closures/mcprotocol.properties")
		val written = Properties().apply { closures.inputStream().use(::load) }
		assertTrue(closures.readLines().none { it.startsWith("#") }, closures.readText())
		assertTrue(closures.readBytes().all { it >= 0 }, closures.readText())
		assertEquals(setOf("1.18.2-1", "1.19.4-1", "1.20.6-1"), written.stringPropertyNames())
		assertEquals(
			listOf("protocol-1.20.6-1.jar", "auth-2.0.jar"),
			written.getProperty("1.20.6-1").split(File.pathSeparator).map { File(it).name },
		)

		project.write("demo-mcprotocol/legacy/build.gradle.kts", "")
		project.write("settings.gradle.kts", project.file("settings.gradle.kts").readText() + "include(\":demo-mcprotocol:legacy\")\n")
		val misnamed = project.fail("help")
		assertTrue(misnamed.output.contains("may contain only segments"), misnamed.output)
	}

	private fun writeProject(project: TestProject) {
		publishLibrary(LibraryRepository(project.file("repository")))
		project.write("settings.gradle.kts", """
			plugins {
				id("library-registry")
			}

			dependencyResolutionManagement {
				repositories {
					maven { url = uri("repository") }
					mavenCentral()
				}
			}

			rootProject.name = "segment-consumer"
			include(":demo-api", ":demo-mcprotocol", ":demo-mcprotocol:V1_18_2", ":demo-mcprotocol:V1_20_6", ":consumer")
			include(":anvil-protocol:protocol-mcprotocol:mcprotocol-common")
		""")
		project.write("anvil-protocol/protocol-mcprotocol/build.gradle.kts", "plugins {\n\tid(\"library-releases\")\n}\n")
		project.write("anvil-protocol/protocol-mcprotocol/mcprotocol-common/build.gradle.kts", """
			plugins {
				id("jvm")
				id("release-closures")
			}
		""")
		project.write("build.gradle.kts", """
			allprojects {
				group = "me.whereareiam.anvil"
				version = "1.0.0"
				pluginManager.withPlugin("maven-publish") {
					extensions.configure<PublishingExtension> {
						repositories.maven {
							name = "functional"
							url = uri(rootDir.resolve("repository-out"))
						}
					}
				}
			}
		""")
		project.write("anvil-protocol/protocol-mcprotocol/mcprotocol-releases.toml", listOf(
			release("1.18.2-1", "com.example.legacy:protocol:1.18.2-1", 758, "1.18.2", 8, "com.example:auth:1.0"),
			release("1.19.4-1", "com.example.legacy:protocol:1.19.4-1", 762, "1.19.4", 8, "com.example:auth:1.0"),
			release("1.20.6-1", "com.example.modern:protocol:1.20.6-1", 766, "1.20.6", 17, "com.example:auth:2.0", "1.20.5"),
		).joinToString("\n\n"))

		project.write("demo-api/build.gradle.kts", "plugins {\n\tid(\"jvm\")\n}\n")
		project.write("demo-api/src/main/java/demo/api/packet/DemoPackets.java", """
			package demo.api.packet;

			public interface DemoPackets {
				String profileName(String username);
			}
		""")
		project.write("demo-mcprotocol/build.gradle.kts", "plugins {\n\tid(\"segmented\")\n}\n")
		listOf("1_18_2" to "com.example.legacy", "1_20_6" to "com.example.modern").forEach { (version, library) ->
			project.write("demo-mcprotocol/V$version/build.gradle.kts", """
				plugins {
					id("segment")
				}

				dependencies {
					compileOnly(project(":demo-api"))
				}
			""")
			segmentSource(project, version, library, "")
		}
		project.write("consumer/build.gradle.kts", """
			plugins {
				id("jvm")
			}

			dependencies {
				implementation(project(":demo-mcprotocol"))
			}
		""")
	}

	private fun publishLibrary(repository: LibraryRepository) {
		val profile = listOf(LibraryClass("com/example/auth/GameProfile", listOf("public <init>()V", "public getName()Ljava/lang/String;")))
		repository.publish("com.example:auth:1.0", profile)
		repository.publish("com.example:auth:2.0", profile)

		fun protocol(packagePath: String, chatConstructor: String) = listOf(
			LibraryClass("$packagePath/MinecraftProtocol", listOf(
				"public <init>(Ljava/lang/String;)V",
				"public getProfile()Lcom/example/auth/GameProfile;",
			)),
			LibraryClass("$packagePath/ChatPacket", listOf("public $chatConstructor")),
		)
		repository.publish("com.example.legacy:protocol:1.18.2-1", protocol("com/example/legacy", "<init>(Ljava/lang/String;)V"), listOf("com.example:auth:1.0"))
		repository.publish("com.example.legacy:protocol:1.19.4-1", protocol("com/example/legacy", "<init>(Ljava/lang/String;J)V"), listOf("com.example:auth:1.0"))
		repository.publish("com.example.modern:protocol:1.20.6-1", protocol("com/example/modern", "<init>(Ljava/lang/String;J)V"), listOf("com.example:auth:2.0"))
	}

	private fun segmentSource(project: TestProject, version: String, library: String, extra: String) {
		project.write("demo-mcprotocol/V$version/src/main/java/demo/mcprotocol/v$version/McProtocolDemoPackets.java", """
			package demo.mcprotocol.v$version;

			import demo.api.packet.DemoPackets;
			import $library.MinecraftProtocol;

			public final class McProtocolDemoPackets implements DemoPackets {
				@Override
				public String profileName(String username) {
					return new MinecraftProtocol(username).getProfile().getName();
				}
				$extra
			}
		""")
	}

	/**
	 * One release whose locked closure is its module and [dependency].
	 */
	private fun release(version: String, module: String, protocol: Int, key: String, java: Int, dependency: String, vararg older: String): String {
		val minecraft = (older.toList() + key).joinToString { "\"$it\"" }
		val header = """
			[[release]]
			version = "$version"
			module = "$module"
			protocol = $protocol
			minecraft = [$minecraft]
			verified = ["$key"]
			java = $java
			features = ["ONLINE_AUTHENTICATION"]
		""".trimIndent()
		return (listOf(header) + listOf(module, dependency).map(::closureTable)).joinToString("\n\n")
	}

	private fun closureTable(module: String): String {
		val (group, name, version) = module.split(':')
		return "[[release.artifact]]\nmodule = \"$module\"\n" +
			"url = \"https://repository.example/${group.replace('.', '/')}/$name/$version/$name-$version.jar\"\nsha256 = \"\""
	}
}
