import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
	`java-library`
	`java-test-fixtures`
	id("org.jetbrains.intellij.platform")
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

description = "Lifecycle and tooling logic for the IntelliJ integration"

dependencies {
	api(projects.anvilIntegration.integrationIntellij.intellijApi)

	implementation(projects.anvilApi)
	implementation(libs.jackson.databind)

	compileOnly(projects.anvilTooling.toolingApi)

	testFixturesApi(libs.jackson.databind)
	testFixturesCompileOnly(libs.annotations)

	testImplementation(projects.anvilTooling.toolingApi)
	testImplementation(projects.anvilTooling.toolingRunner)
	testImplementation(libs.junit4)

	testRuntimeOnly(libs.junit.vintage)

	intellijPlatform {
		create("IC", libs.versions.intellij.idea)
		bundledPlugin("com.intellij.java")
		testFramework(TestFrameworkType.Platform)
	}
}

val verificationIdePath = providers.gradleProperty("anvil.intellijVerificationPath")

intellijPlatformTesting.testIde.register("testCurrentIde") {
	type = IntelliJPlatformType.IntellijIdeaUltimate
	version = libs.versions.intellij.verify
	if (verificationIdePath.isPresent) localPath.set(file(verificationIdePath.get()))
	testFramework(TestFrameworkType.Platform, task.map { it.productInfo.buildNumber })
	plugins {
		bundledPlugins("com.intellij.java")
		disablePlugin("com.intellij.modules.ultimate")
	}
	task {
		useJUnitPlatform()
		classpath += sourceSets.test.get().output
	}
}

tasks.named("instrumentTestCode") {
	mustRunAfter(tasks.named("instrumentCode"))
}
