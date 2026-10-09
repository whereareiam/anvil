import me.whereareiam.anvil.buildlogic.library.LibraryRepositories
import me.whereareiam.anvil.buildlogic.library.releaseClosureClasspath
import me.whereareiam.anvil.buildlogic.library.releaseModule
import me.whereareiam.anvil.buildlogic.adapter.SegmentPackageRule
import me.whereareiam.anvil.buildlogic.adapter.SegmentPlanSource
import me.whereareiam.anvil.buildlogic.adapter.WriteSegmentManifest
import me.whereareiam.anvil.buildlogic.adapter.linkage.CheckSegmentLinkage

plugins {
	java
	id("module-java")
	id("packaging-publication")
}

// A segment adapts one feature to one library release: its folder V<major>_<minor>[_<patch>] names the release
// key it starts at, and its parent folder names the library.
val plan = providers.of(SegmentPlanSource::class) {
	parameters {
		rootDirectory.set(isolated.rootProject.projectDirectory)
		segmentDirectory.set(layout.projectDirectory)
	}
}.get()

// Only the side folder puts its segments on the runtime classpath. The build-libraries settings plugin fails a
// side folder that does not apply `module-adapter`, so a segment requires that plugin.
if (gradle.extensions.findByType<LibraryRepositories>() == null)
	throw GradleException("Segment $path needs the library layout checks: add id(\"build-libraries\") to the settings plugins")

// Segments of different side folders share project names, and Gradle conflates projects with equal
// group:name, so the side folder is part of the group and of the JAR name.
group = "me.whereareiam.anvil.${plan.owner}"
description = "${plan.owner} adapter for ${plan.library} releases from Minecraft ${plan.segment.since}"
base {
	archivesName.set("${plan.owner}-${project.name}")
}

// The release the segment starts at is its compile baseline; the worker supplies the library at runtime.
dependencies {
	compileOnly(plan.release.module)
	testImplementation(plan.release.module)
}

// Classes outside the versioned package fail the compilation itself.
tasks.named<JavaCompile>("compileJava") {
	inputs.property("segmentPackage", plan.segment.packageName)
	doLast(SegmentPackageRule(plan.segment.packageName))
}

val toolchainJdk = javaToolchains.launcherFor(java.toolchain)
val projectClasses = configurations.named("compileClasspath").map { classpath ->
	classpath.incoming.artifactView { componentFilter { it is ProjectComponentIdentifier } }.files
}

val manifest = tasks.register<WriteSegmentManifest>("writeSegmentManifest") {
	description = "Writes the segment's selection properties and linkage manifest."
	library.set(plan.library)
	owner.set(plan.owner)
	since.set(plan.segment.since.toString())
	classes.from(sourceSets.main.map { it.output.classesDirs })
	classpath.from(configurations.named("compileClasspath"))
	projectDependencies.from(projectClasses)
	jdk.set(toolchainJdk)
	destination.set(layout.buildDirectory.dir("generated/segment"))
}

tasks.named<ProcessResources>("processResources") {
	from(manifest)
}

// The release module's dependency graph tells which references belong to the library; the locked closures of
// release data are what workers load, so the references must link on the compiled release's closure and on the
// closure of every later release the segment serves.
val libraryModule = releaseModule(plan.release)
val compiledRelease = releaseClosureClasspath(plan.release)
val laterReleaseClasspaths = plan.laterReleases.map { releaseClosureClasspath(it) }

val linkageCheck = tasks.register<CheckSegmentLinkage>("checkSegmentLinkage") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Verifies that the segment links against every library release it serves, as workers load them."
	linkage.set(manifest.flatMap { it.destination.file("META-INF/anvil/segment/linkage.txt") })
	library.from(libraryModule)
	release.set(compiledRelease)
	laterReleases.set(laterReleaseClasspaths)
	jdk.set(toolchainJdk)
	report.set(layout.buildDirectory.file("reports/segment/linkage.txt"))
}

tasks.named("check") {
	dependsOn(linkageCheck)
}
