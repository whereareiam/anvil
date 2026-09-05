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
		ProtocolSupport support = ProtocolSupport.builder()
				.minecraftVersion(minecraftVersion)
				.protocolNumber(protocolNumber)
				.libraryVersion(libraryVersion)
				.bindingFamily("mcprotocol-2026")
				.javaVersion(21)
				.capability(ProtocolCapability.ONLINE_AUTHENTICATION)
				.build();
		definitions.put(minecraftVersion, ProtocolDefinition.builder()
				.support(support)
				.artifact(URI.create(url))
				.sha256(sha256)
				.build());
	}
}
