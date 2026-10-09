import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
	id("org.jetbrains.intellij.platform")
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

description = "Gradle adapter for the IntelliJ integration"

dependencies {
	implementation(projects.anvilIntegration.integrationIntellij.intellijApi)
	intellijPlatform {
		create("IC", libs.versions.intellij.idea)
		bundledPlugin("com.intellij.java")
		bundledPlugin("org.jetbrains.plugins.gradle")
		testFramework(TestFrameworkType.Platform)
	}
	testImplementation(libs.junit4)
	testRuntimeOnly(libs.junit.vintage)
}

val verificationIdePath = providers.gradleProperty("anvil.intellijVerificationPath")

intellijPlatformTesting.testIde.register("testCurrentIde") {
	type = IntelliJPlatformType.IntellijIdeaUltimate
	version = libs.versions.intellij.verify
	if (verificationIdePath.isPresent) localPath.set(file(verificationIdePath.get()))
	testFramework(TestFrameworkType.Platform, task.map { it.productInfo.buildNumber })
	plugins {
		bundledPlugins("com.intellij.java", "org.jetbrains.plugins.gradle")
		disablePlugin("com.intellij.modules.ultimate")
	}
	task {
		useJUnitPlatform()
		classpath += sourceSets.test.get().output
	}
}
