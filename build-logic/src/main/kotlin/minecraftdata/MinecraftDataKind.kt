package me.whereareiam.anvil.buildlogic.minecraftdata

import java.io.Serializable

/**
 * One kind of data a module generates from PrismarineJS minecraft-data, such as the packet names of
 * `protocol-java-packet`: the pins file naming one minecraft-data release
 * and, per protocol, the files of that release the data is made of.
 *
 * Every protocol's files of one kind are found through minecraft-data's own `data/dataPaths.json`, which names the
 * directory below `data/` that holds each file for a Minecraft version, so a kind only says which entries it needs.
 *
 * @property subject what the data is, in failures and the pins file's header, such as `packet data`
 * @property stem the name of the kind's tasks, such as `PacketData` for `pinPacketData`, `checkPacketDataReleases` and
 * `generatePacketData`
 * @property pinsFile the pins file in the module's directory, such as `packet-data.toml`
 * @property files the files each protocol pins, in the order its rows list them
 */
data class MinecraftDataKind(
	val subject: String,
	val stem: String,
	val pinsFile: String,
	val files: List<File>,
) : Serializable {
	init {
		require(files.isNotEmpty()) { "$subject must pin at least one file per protocol" }
		require(files.map { it.field }.distinct().size == files.size) { "$subject names a file field twice" }
	}

	/**
	 * One file a protocol of the kind pins.
	 *
	 * @property field the key of the file's `{ path, sha256 }` table in a `[[release]]` row, such as `packets`;
	 * never `protocol`, which names the row's protocol
	 * @property entry the entry of `data/dataPaths.json` naming the file's directory, which is also the file's name
	 * without its `.json` extension, such as `protocol`
	 */
	data class File(val field: String, val entry: String) : Serializable {
		init {
			require(field != "protocol") { "A pinned file cannot be named 'protocol', which names a row's protocol" }
		}
	}
}
