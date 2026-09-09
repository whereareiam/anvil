import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("unit")
}

description = "External backend, capabilities, and agent operations compiled against public APIs only"

dependencies {
    compileOnly(anvil.agent.server.api)
    compileOnly(anvil.api)
    compileOnly(anvil.capability.agent.api)
    compileOnly(anvil.capability.protocol.api)
    compileOnly(anvil.movement.api)
    compileOnly(anvil.protocol.api)
    compileOnly(anvil.session.api)
}

val fixtureVariant = Attribute.of("me.whereareiam.anvil.testkit.variant", String::class.java)
val protocolService = "META-INF/services/me.whereareiam.anvil.protocol.api.provider.ProtocolProvider"

configurations.runtimeElements {
    attributes.attribute(fixtureVariant, "normal")
}

listOf("broken", "observation").forEach { variant ->
    val variantJar = tasks.register<Jar>("${variant}Jar") {
        description = "Packages the external extension with the $variant protocol provider"
        archiveClassifier.set(variant)
        from(sourceSets.main.get().output) {
            exclude(protocolService)
        }
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
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
        }
        outgoing.artifact(variantJar)
    }

    tasks.assemble {
        dependsOn(variantJar)
    }
}
