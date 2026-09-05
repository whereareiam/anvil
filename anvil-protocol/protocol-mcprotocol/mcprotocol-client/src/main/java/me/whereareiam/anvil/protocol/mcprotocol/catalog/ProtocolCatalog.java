package me.whereareiam.anvil.protocol.mcprotocol.catalog;

import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import me.whereareiam.anvil.protocol.api.type.ProtocolCapability;
import me.whereareiam.anvil.protocol.mcprotocol.model.ProtocolDefinition;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pinned, verified MCProtocolLib distributions shipped by this Anvil release.
 */
public final class ProtocolCatalog {
	private final Map<String, ProtocolDefinition> definitions = new LinkedHashMap<>();

	public ProtocolCatalog() {
		register("1.18.2", 758, "1.18.2-1",
				"https://repo.opencollab.dev/maven-releases/com/github/steveice10/mcprotocollib/1.18.2-1/mcprotocollib-1.18.2-1.jar",
				"ec729cd7e8539f157d4780e8e62fc5fcb3e063787c8e286b9529d29517afd0c8",
				"com.github.steveice10:mcprotocollib", "mcprotocol-packetlib");
		register("1.19.4", 762, "1.19.4-2-20230503.145414-3",
				"https://repo.opencollab.dev/maven-snapshots/com/github/steveice10/mcprotocollib/"
						+ "1.19.4-2-SNAPSHOT/mcprotocollib-1.19.4-2-20230503.145414-3.jar",
				"531426b2df06db91840638d2e93927b9fa5e54fbb3dda60f6309af0d9b8fd685",
				"com.github.steveice10:mcprotocollib", "mcprotocol-packetlib");
		register("1.20.6", 766, "1.20.6-2-20240610.212238-11",
				"https://repo.opencollab.dev/maven-snapshots/org/geysermc/mcprotocollib/protocol/"
						+ "1.20.6-2-SNAPSHOT/protocol-1.20.6-2-20240610.212238-11.jar",
				"4f25c724a7adb4be4f58b6f3747df1fe6d3db0ba05ee2ef07c354c28a3a906f1",
				"org.geysermc.mcprotocollib:protocol", "mcprotocol-tcp");
		register(
				"1.21.11",
				774,
				"1.21.11-20260512.221357-18",
				"https://repo.opencollab.dev/maven-snapshots/org/geysermc/mcprotocollib/protocol/"
						+ "1.21.11-SNAPSHOT/protocol-1.21.11-20260512.221357-18.jar",
				"00d9ae3464dac8dcfe861303d728cb3361a9794d8ad0455aa340229ee83ee709"
		);
		register(
				"26.1.2",
				775,
				"26.1-20260708.090514-22",
				"https://repo.opencollab.dev/maven-snapshots/org/geysermc/mcprotocollib/protocol/"
						+ "26.1-SNAPSHOT/protocol-26.1-20260708.090514-22.jar",
				"649c00934cc5a38da2256598a9d83e5c9049c7e3c327a6dc568bbb8ac818bd08"
		);
		register(
				"26.2",
				776,
				"26.2-20260824.124638-17",
				"https://repo.opencollab.dev/maven-snapshots/org/geysermc/mcprotocollib/protocol/"
						+ "26.2-SNAPSHOT/protocol-26.2-20260824.124638-17.jar",
				"07ec18ba92c8b4041286eeff2470e08257fd1f383881515cba4a0a9bf6fa98c1"
		);
	}

	/**
	 * Returns an immutable snapshot of the pinned support entries.
	 */
	public @NotNull Collection<ProtocolDefinition> definitions() {
		return List.copyOf(definitions.values());
	}

	/**
	 * Resolves one exact Minecraft version without implicit compatibility fallback.
	 */
	public @NotNull ProtocolDefinition require(@NotNull String minecraftVersion) {
		ProtocolDefinition definition = definitions.get(minecraftVersion);
		if (definition == null) {
			throw new IllegalArgumentException("Unsupported clientVersion '" + minecraftVersion
					+ "'. Supported: " + definitions.keySet());
		}

		return definition;
	}

	private void register(
			String minecraftVersion,
			int protocolNumber,
			String libraryVersion,
			String url,
			String sha256
	) {
		register(minecraftVersion, protocolNumber, libraryVersion, url, sha256,
				"org.geysermc.mcprotocollib:protocol", "mcprotocol-2026");
	}

	private void register(
			String minecraftVersion,
			int protocolNumber,
			String libraryVersion,
			String url,
			String sha256,
			String library,
			String bindingFamily
	) {
		ProtocolSupport support = ProtocolSupport.builder()
				.minecraftVersion(minecraftVersion)
				.protocolNumber(protocolNumber)
				.libraryVersion(libraryVersion)
				.bindingFamily(bindingFamily)
				.javaVersion(21)
				.capability(ProtocolCapability.ONLINE_AUTHENTICATION)
				.build();
		definitions.put(minecraftVersion, ProtocolDefinition.builder()
				.support(support)
				.artifact(URI.create(url))
				.sha256(sha256)
				.coordinate(library + ":" + libraryVersion)
				.build());
	}
}
