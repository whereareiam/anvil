import me.whereareiam.anvil.buildlogic.integration.IntellijPlatformModule
import me.whereareiam.anvil.buildlogic.module.catalog
import me.whereareiam.anvil.buildlogic.module.library
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
	java
	// Applied by module-intellij; listed for its type-safe accessors.
	id("org.jetbrains.intellij.platform")
	id("module-intellij")
}

// Tests of an IntelliJ module run in the baseline platform with its test framework. Contract-only modules
// without tests apply module-intellij alone.
val module = the<IntellijPlatformModule>()
val verificationIde = catalog.findVersion("intellij-verify")
	.orElseThrow { GradleException("The libs catalog has no 'intellij-verify' version") }
	.requiredVersion
val verificationIdePath = providers.gradleProperty("anvil.intellijVerificationPath")

dependencies {
	intellijPlatform {
		testFramework(TestFrameworkType.Platform)
	}

	testImplementation(catalog.library("junit4"))
	testRuntimeOnly(catalog.library("junit-vintage"))
}

// Runs the same tests in the newest verified IDE, or in a local installation passed as a Gradle property.
intellijPlatformTesting.testIde.register("testCurrentIde") {
	type.set(IntelliJPlatformType.IntellijIdeaUltimate)
	version.set(verificationIde)
	if (verificationIdePath.isPresent)
		localPath.set(file(verificationIdePath.get()))
	// Test against the selected runtime's framework while compiling against the baseline SDK.
	testFramework(TestFrameworkType.Platform, task.map { it.productInfo.buildNumber })
	plugins {
		bundledPlugins(module.bundledPlugins)
		// The flat test loader cannot isolate duplicate obfuscated product classes in this optional plugin.
		disablePlugin("com.intellij.modules.ultimate")
	}
	task {
		useJUnitPlatform()
		classpath += sourceSets.test.get().output
	}
}
