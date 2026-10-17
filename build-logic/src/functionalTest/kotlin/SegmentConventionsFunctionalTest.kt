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
		val releases = project.file("anvil-player/player-library/library-mcprotocol/mcprotocol-releases.toml")
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
		project.write("demo-mcprotocol/build.gradle.kts", "plugins {\n\tid(\"module-adapter\")\n}\n\ndependencies {\n\tcompileOnly(project(\":demo-api\"))\n}\n")
		val lookup = project.fail(":demo-mcprotocol:check")
		assertTrue(lookup.output.contains("must obtain adapters through PlayerBindingContext.adapter(Class), not java.util.ServiceLoader: "
			+ "demo.mcprotocol.DemoWiring"), lookup.output)
		project.file(wiring).delete()

		project.write("demo-mcprotocol/build.gradle.kts", "plugins {\n\tid(\"module-java\")\n}\n")
		val unexported = project.fail("help")
		assertTrue(unexported.output.contains(
			":demo-mcprotocol holds segments, which reach a worker only through their library side folder; it must apply id(\"module-adapter\")"
		), unexported.output)
		project.write("demo-mcprotocol/build.gradle.kts", "plugins {\n\tid(\"module-adapter\")\n}\n")

		// Worker tests of the library receive each release's locked closure, in release data order, from a file
		// without a timestamp, which stays the same while the closures do. The file is ASCII, as the test JVM's
		// Properties.load(InputStream) reads it.
		project.run(":anvil-player:player-library:library-mcprotocol:mcprotocol-common:writeReleaseClosures")
		val closures = project.file("anvil-player/player-library/library-mcprotocol/mcprotocol-common/build/release-closures/mcprotocol.properties")
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

	@Test
	fun `a segment extends an earlier sibling, carries its classes but its adapter and checks their linkage`() {
		val project = TestProject(directory)
		writeProject(project)
		project.write("settings.gradle.kts", project.file("settings.gradle.kts").readText() + "include(\":demo-mcprotocol:V1_19_4\")\n")
		// The 1.18.2 segment shares an abstract base and adapts it to 1.18.2-1 with its own adapter.
		project.write("demo-mcprotocol/V1_18_2/src/main/java/demo/mcprotocol/v1_18_2/DemoPacketsBase.java", """
			package demo.mcprotocol.v1_18_2;

			import com.example.legacy.MinecraftProtocol;
			import demo.api.packet.DemoPackets;

			public abstract class DemoPacketsBase implements DemoPackets {
				@Override
				public String profileName(String username) {
					return new MinecraftProtocol(username).getProfile().getName();
				}

				public abstract Object chat(String message);
			}
		""")
		project.write("demo-mcprotocol/V1_18_2/src/main/java/demo/mcprotocol/v1_18_2/McProtocolDemoPackets.java", """
			package demo.mcprotocol.v1_18_2;

			public final class McProtocolDemoPackets extends DemoPacketsBase {
				@Override
				public Object chat(String message) {
					return new com.example.legacy.ChatPacket(message);
				}
			}
		""")
		project.write("demo-mcprotocol/V1_18_2/src/main/resources/META-INF/services/demo.api.packet.DemoPackets",
			"demo.mcprotocol.v1_18_2.McProtocolDemoPackets\n")
		// 1.19.4-1 changed only the chat packet's constructor, so its segment adapts only the chat.
		project.write("demo-mcprotocol/V1_19_4/build.gradle.kts", """
			plugins {
				id("module-adapter-segment")
			}

			segment {
				extends(":demo-mcprotocol:V1_18_2")
			}

			dependencies {
				compileOnly(project(":demo-api"))
			}
		""")
		project.write("demo-mcprotocol/V1_19_4/src/main/java/demo/mcprotocol/v1_19_4/McProtocolDemoPackets.java", """
			package demo.mcprotocol.v1_19_4;

			public final class McProtocolDemoPackets extends demo.mcprotocol.v1_18_2.DemoPacketsBase {
				@Override
				public Object chat(String message) {
					return new com.example.legacy.ChatPacket(message, 0L);
				}
			}
		""")

		val build = project.run(":demo-mcprotocol:V1_19_4:build")
		assertEquals(TaskOutcome.SUCCESS, build.task(":demo-mcprotocol:V1_19_4:checkSegmentLinkage")?.outcome, build.output)
		val jar = "demo-mcprotocol/V1_19_4/build/libs/demo-mcprotocol-V1_19_4-1.0.0.jar"
		assertEquals(
			listOf("demo/mcprotocol/v1_18_2/DemoPacketsBase.class", "demo/mcprotocol/v1_19_4/McProtocolDemoPackets.class"),
			project.jarEntries(jar).filter { it.endsWith(".class") }.sorted(),
		)
		assertEquals("library=mcprotocol\nowner=demo-mcprotocol\nsince=1.19.4\n", project.jarText(jar, "META-INF/anvil/segment.properties"))
		val linkage = project.jarText(jar, "META-INF/anvil/segment/linkage.txt").lines()
		assertTrue(linkage.contains("method com.example.legacy.MinecraftProtocol <init>(Ljava/lang/String;)V public instance class"), linkage.toString())
		assertTrue(linkage.contains("method com.example.legacy.ChatPacket <init>(Ljava/lang/String;J)V public instance class"), linkage.toString())
		assertFalse(linkage.contains("method com.example.legacy.ChatPacket <init>(Ljava/lang/String;)V public instance class"), linkage.toString())

		project.write("demo-mcprotocol/V1_18_2/build.gradle.kts", """
			plugins {
				id("module-adapter-segment")
			}

			segment {
				extends(":demo-mcprotocol:V1_19_4")
			}
		""")
		val later = project.fail(":demo-mcprotocol:V1_18_2:compileJava")
		assertTrue(later.output.contains(
			"Segment :demo-mcprotocol:V1_18_2 can extend only an earlier segment, not :demo-mcprotocol:V1_19_4"
		), later.output)
	}

	@Test
	fun `a segment replaces a base class that does not link against its release`() {
		val project = TestProject(directory)
		writeProject(project)
		val settings = project.file("settings.gradle.kts").readText()
		project.write("settings.gradle.kts", settings + "include(\":demo-mcprotocol:V1_19_4\")\n")
		// The 1.18.2 segment keeps its chat in a class of its own, which the adapter names.
		project.write("demo-mcprotocol/V1_18_2/src/main/java/demo/mcprotocol/v1_18_2/DemoChat.java", """
			package demo.mcprotocol.v1_18_2;

			public final class DemoChat {
				public Object chat(String message) {
					return new com.example.legacy.ChatPacket(message);
				}
			}
		""")
		project.write("demo-mcprotocol/V1_18_2/src/main/java/demo/mcprotocol/v1_18_2/DemoProfiles.java", """
			package demo.mcprotocol.v1_18_2;

			import com.example.legacy.MinecraftProtocol;

			public final class DemoProfiles {
				public String profileName(String username) {
					return new MinecraftProtocol(username).getProfile().getName();
				}
			}
		""")
		project.write("demo-mcprotocol/V1_18_2/src/main/resources/META-INF/services/demo.api.packet.DemoPackets",
			"demo.mcprotocol.v1_18_2.McProtocolDemoPackets\n")
		// 1.19.4-1 changed the chat packet's constructor, so its segment brings a chat of its own.
		project.write("demo-mcprotocol/V1_19_4/src/main/java/demo/mcprotocol/v1_19_4/DemoChat.java", """
			package demo.mcprotocol.v1_19_4;

			public final class DemoChat {
				public Object chat(String message) {
					return new com.example.legacy.ChatPacket(message, 0L);
				}
			}
		""")
		project.write("demo-mcprotocol/V1_19_4/src/main/java/demo/mcprotocol/v1_19_4/McProtocolDemoPackets.java", """
			package demo.mcprotocol.v1_19_4;

			import demo.api.packet.DemoPackets;
			import demo.mcprotocol.v1_18_2.DemoProfiles;

			public final class McProtocolDemoPackets implements DemoPackets {
				@Override
				public String profileName(String username) {
					new DemoChat().chat(username);
					return new DemoProfiles().profileName(username);
				}
			}
		""")

		extendingSegment(project)
		val unreplaced = project.fail(":demo-mcprotocol:V1_19_4:checkSegmentLinkage")
		assertTrue(unreplaced.output.contains("missing method com.example.legacy.ChatPacket <init>(Ljava/lang/String;)V"),
			unreplaced.output)

		extendingSegment(project, "demo.mcprotocol.v1_18_2.DemoChat")
		val build = project.run(":demo-mcprotocol:V1_19_4:build")
		assertEquals(TaskOutcome.SUCCESS, build.task(":demo-mcprotocol:V1_19_4:checkSegmentLinkage")?.outcome, build.output)
		val jar = "demo-mcprotocol/V1_19_4/build/libs/demo-mcprotocol-V1_19_4-1.0.0.jar"
		assertEquals(
			listOf(
				"demo/mcprotocol/v1_18_2/DemoProfiles.class",
				"demo/mcprotocol/v1_19_4/DemoChat.class",
				"demo/mcprotocol/v1_19_4/McProtocolDemoPackets.class",
			),
			project.jarEntries(jar).filter { it.endsWith(".class") }.sorted(),
		)
		val linkage = project.jarText(jar, "META-INF/anvil/segment/linkage.txt").lines()
		assertTrue(linkage.contains("method com.example.legacy.ChatPacket <init>(Ljava/lang/String;J)V public instance class"),
			linkage.toString())
		assertFalse(linkage.contains("method com.example.legacy.ChatPacket <init>(Ljava/lang/String;)V public instance class"),
			linkage.toString())

		extendingSegment(project, "demo.mcprotocol.v1_18_2.DemoChats")
		val missing = project.fail(":demo-mcprotocol:V1_19_4:syncSegmentBase")
		assertTrue(missing.output.contains(
			"Segment :demo-mcprotocol:V1_19_4 replaces demo.mcprotocol.v1_18_2.DemoChats, which is not a class of its "
				+ "base chain"
		), missing.output)

		extendingSegment(project, "demo.mcprotocol.v1_18_2.DemoChat$1")
		val nested = project.fail("help")
		assertTrue(nested.output.contains("can replace only a top-level class by its binary name"), nested.output)

		project.write("demo-mcprotocol/V1_18_2/src/main/java/demo/mcprotocol/v1_18_2/DemoProfiles.java", """
			package demo.mcprotocol.v1_18_2;

			import com.example.legacy.MinecraftProtocol;

			public final class DemoProfiles {
				public String profileName(String username) {
					new DemoChat().chat(username);
					return new MinecraftProtocol(username).getProfile().getName();
				}
			}
		""")
		extendingSegment(project, "demo.mcprotocol.v1_18_2.DemoChat")
		val used = project.fail(":demo-mcprotocol:V1_19_4:syncSegmentBase")
		assertTrue(used.output.contains(
			"Segment :demo-mcprotocol:V1_19_4 replaces demo.mcprotocol.v1_18_2.DemoChat, which its base class "
				+ "demo.mcprotocol.v1_18_2.DemoProfiles still uses; replace demo.mcprotocol.v1_18_2.DemoProfiles too"
		), used.output)
	}

	/**
	 * Writes the 1.19.4 segment extending the 1.18.2 one and replacing [replaced] base classes.
	 */
	private fun extendingSegment(project: TestProject, vararg replaced: String) {
		val replaces = if (replaced.isEmpty()) ""
		else replaced.joinToString(prefix = "replaces(", postfix = ")") { "\"$it\"" }
		project.write("demo-mcprotocol/V1_19_4/build.gradle.kts", """
			plugins {
				id("module-adapter-segment")
			}

			segment {
				extends(":demo-mcprotocol:V1_18_2")
				$replaces
			}

			dependencies {
				compileOnly(project(":demo-api"))
			}
		""")
	}

	private fun writeProject(project: TestProject) {
		publishLibrary(LibraryRepository(project.file("repository")))
		project.write("settings.gradle.kts", """
			plugins {
				id("build-libraries")
			}

			dependencyResolutionManagement {
				repositories {
					maven { url = uri("repository") }
					mavenCentral()
				}
			}

			rootProject.name = "segment-consumer"
			include(":demo-api", ":demo-mcprotocol", ":demo-mcprotocol:V1_18_2", ":demo-mcprotocol:V1_20_6", ":consumer")
			include(":anvil-player:player-library:library-mcprotocol:mcprotocol-common")
		""")
		project.write("anvil-player/player-library/library-mcprotocol/build.gradle.kts", "plugins {\n\tid(\"module-library\")\n}\n")
		project.write("anvil-player/player-library/library-mcprotocol/mcprotocol-common/build.gradle.kts", """
			plugins {
				id("module-java")
				id("test-library-closures")
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
		project.write("anvil-player/player-library/library-mcprotocol/mcprotocol-releases.toml", listOf(
			release("1.18.2-1", "com.example.legacy:protocol:1.18.2-1", 758, "1.18.2", 8, "com.example:auth:1.0"),
			release("1.19.4-1", "com.example.legacy:protocol:1.19.4-1", 762, "1.19.4", 8, "com.example:auth:1.0"),
			release("1.20.6-1", "com.example.modern:protocol:1.20.6-1", 766, "1.20.6", 17, "com.example:auth:2.0", "1.20.5"),
		).joinToString("\n\n"))

		project.write("demo-api/build.gradle.kts", "plugins {\n\tid(\"module-java\")\n}\n")
		project.write("demo-api/src/main/java/demo/api/packet/DemoPackets.java", """
			package demo.api.packet;

			public interface DemoPackets {
				String profileName(String username);
			}
		""")
		project.write("demo-mcprotocol/build.gradle.kts", "plugins {\n\tid(\"module-adapter\")\n}\n")
		listOf("1_18_2" to "com.example.legacy", "1_20_6" to "com.example.modern").forEach { (version, library) ->
			project.write("demo-mcprotocol/V$version/build.gradle.kts", """
				plugins {
					id("module-adapter-segment")
				}

				dependencies {
					compileOnly(project(":demo-api"))
				}
			""")
			segmentSource(project, version, library, "")
		}
		project.write("consumer/build.gradle.kts", """
			plugins {
				id("module-java")
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
