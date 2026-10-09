import me.whereareiam.anvil.buildlogic.module.compileForLegacyJava

plugins {
	java
	id("module-java")
}

// Minecraft servers load these main classes, and the oldest supported server runs on the legacy Java release.
// Tests keep the build's release, so they may still use host-side projects. checkClassRelease fails the build on a
// newer main class, for example because a build file overrides the compilation's release.
compileForLegacyJava(sourceSets.main.get())
