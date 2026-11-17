package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier;
import org.jetbrains.annotations.NotNull;

/**
 * The attributes of entities in the MCProtocolLib 1.20.6 release, which still names each modifier by a UUID.
 */
public final class AttributePackets extends AttributeTranslation {
	@Override
	protected @NotNull String modifierId(@NotNull AttributeModifier modifier) {
		return modifier.getUuid().toString();
	}
}
