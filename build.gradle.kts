import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import me.whereareiam.toolkit.versioning.extension.ToolkitVersioningExtension

plugins {
    base
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.versioning)
    alias(libs.plugins.toolkit.publish.maven) apply false
}

val projectVersion = extensions.getByType<ToolkitVersioningExtension>().apply {
    defaultVersion.set(providers.gradleProperty("anvilVersion"))
}.resolvedVersion().get()

allprojects {
	group = "me.whereareiam.anvil"
	version = projectVersion
}

val productProjects = subprojects
val fixtureRepository = layout.buildDirectory.dir("gradle-fixtures/repository")
val prepareFixtures = tasks.register("prepareGradleFixtureRepository") {
	group = "verification"
	description = "Publishes framework artifacts for independent Gradle consumer tests."
}
val fixtureBuild = gradle.includedBuilds.singleOrNull { it.name == "anvil-test-fixtures" }

tasks.named("build") {
	dependsOn(productProjects.map { project ->
		project.tasks.matching { task -> task.name == "build" }
	})
	fixtureBuild?.let { dependsOn(it.task(":build")) }
}

listOf("publish", "publishToMavenLocal").forEach { operation ->
	tasks.register(operation) {
		group = "publishing"
		dependsOn(productProjects.map { project ->
			project.tasks.matching { task -> task.name == operation }
		})
	}
}

allprojects {
	pluginManager.withPlugin("maven-publish") {
		extensions.configure<PublishingExtension> {
			repositories.maven {
				name = "anvilTest"
				url = fixtureRepository.get().asFile.toURI()
			}
		}

		val publications = tasks.withType<PublishToMavenRepository>()
			.matching { task -> task.name.endsWith("ToAnvilTestRepository") }
		prepareFixtures.configure { dependsOn(publications) }
		publications.configureEach {
			mustRunAfter(rootProject.tasks.named("clean"))
		}
	}
}
