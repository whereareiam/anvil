import me.whereareiam.anvil.buildlogic.jvm.compileForInServer
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
	id("jvm")
}

description = "External protocol library, capabilities, and agent operations compiled against public APIs only"

// The agent operations load inside Minecraft servers, so they compile for the in-server Java release in
// their own source set, which checkAgentClassRelease verifies. The host code stays on the build's release and
// shares the operations' wire contract.
val agent = sourceSets.create("agent")
compileForInServer(agent)

sourceSets.main {
	compileClasspath += agent.output
}

dependencies {
	compileOnly(anvil.agent.api)
	compileOnly(anvil.api)
	compileOnly(anvil.capability.agent.api)
	compileOnly(anvil.capability.protocol.api)
	compileOnly(anvil.movement.api)
	compileOnly(anvil.protocol.api)
	compileOnly(anvil.session.api)

	"agentCompileOnly"(anvil.agent.server.api)
	"agentCompileOnly"(libs.annotations)
}

val fixtureVariant = Attribute.of("me.whereareiam.anvil.testkit.variant", String::class.java)
val protocolService = "META-INF/services/me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider"

// One JAR serves both sides: the host loads its providers and installs the same JAR as a server extension.
tasks.jar {
	from(agent.output)
}

configurations.runtimeElements {
	attributes.attribute(fixtureVariant, "normal")
}

listOf("broken", "observation").forEach { variant ->
	val variantJar = tasks.register<Jar>("${variant}Jar") {
		description = "Packages the external extension with the $variant protocol library"
		archiveClassifier.set(variant)
		from(sourceSets.main.get().output) {
			exclude(protocolService)
		}
		from(agent.output)
		from("src/$variant/resources")
	}

	configurations.create("${variant}RuntimeElements") {
		isCanBeConsumed = true
		isCanBeResolved = false
		attributes {
			attribute(fixtureVariant, variant)
			attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
			attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
			attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
			attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
			attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaRelease.main)
		}
		outgoing.artifact(variantJar)
	}

	tasks.assemble {
		dependsOn(variantJar)
	}
}
