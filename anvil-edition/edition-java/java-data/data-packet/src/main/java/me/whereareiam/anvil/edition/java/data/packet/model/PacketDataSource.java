package me.whereareiam.anvil.edition.java.data.packet.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Where a protocol's packet names come from: the PrismarineJS minecraft-data release, the commit it is pinned to and
 * the protocol file whose packet mappings name every packet.
 */
@Value
@Builder
public class PacketDataSource {
	/**
	 * The GitHub repository, such as {@code PrismarineJS/minecraft-data}.
	 */
	@NotNull String repository;
	/**
	 * The minecraft-data release, such as {@code 3.117.0}.
	 */
	@NotNull String version;
	/**
	 * The commit the release is pinned to.
	 */
	@NotNull String commit;
	/**
	 * The protocol file, such as {@code data/pc/1.21.11/protocol.json}.
	 */
	@NotNull String protocol;
}
