import me.whereareiam.anvil.buildlogic.block.BlockDataExtension

plugins {
	id("module-api")
}

// A module whose block data is generated from the block data the physics harness's generateVanillaData read from each
// release's vanilla client, committed as vanilla/<protocol>.blocks, such as data-block. Its build file names
// the generator through blockData.generate(...); nothing is downloaded, so offline builds and CI work.
extensions.create<BlockDataExtension>("blockData", project)
