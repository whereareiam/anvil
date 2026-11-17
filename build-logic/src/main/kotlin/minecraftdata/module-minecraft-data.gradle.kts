import me.whereareiam.anvil.buildlogic.minecraftdata.MinecraftDataExtension

plugins {
	id("module-api")
}

// A module whose data is generated from pinned PrismarineJS minecraft-data files, such as protocol-java-block's block
// data. Its build file names the kind through minecraftData.generate(...); Gradle resolves the pinned files through
// the settings' minecraft-data repository, so they are fetched once and offline builds work.
extensions.create<MinecraftDataExtension>("minecraftData", project)
