import me.whereareiam.anvil.buildlogic.minecraftdata.MinecraftDataKind

plugins {
	id("module-minecraft-data")
	id("module-edition")
}

description = "Item registry keys of every Minecraft: Java Edition release Anvil supports, looked up by item ID"

// item-data.toml pins the PrismarineJS minecraft-data items.json of each protocol, which names every item of the
// release's item registry by its numeric ID.
minecraftData.generate(
	MinecraftDataKind(
		subject = "item data",
		stem = "ItemData",
		pinsFile = "item-data.toml",
		files = listOf(MinecraftDataKind.File("items", "items")),
	),
	generator = "me.whereareiam.anvil.edition.java.data.item.ItemDataGenerator",
	packagePath = "me/whereareiam/anvil/edition/java/data/item",
)
