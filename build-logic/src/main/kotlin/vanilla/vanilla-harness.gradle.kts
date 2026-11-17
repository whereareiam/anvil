import me.whereareiam.anvil.buildlogic.vanilla.VanillaDataExtension

plugins {
	java
}

// The standalone harness build that runs the real vanilla client headless and writes reference data from it. Its build
// file names the live matrix, the pins of the clients, the courses and the traces through vanillaData.generate(...).
extensions.create<VanillaDataExtension>("vanillaData", project)
