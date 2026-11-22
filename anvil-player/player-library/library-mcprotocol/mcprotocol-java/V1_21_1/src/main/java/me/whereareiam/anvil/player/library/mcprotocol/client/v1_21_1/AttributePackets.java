package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.AttributeTranslation;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier;
import org.jetbrains.annotations.NotNull;

/**
 * The attributes of entities in the MCProtocolLib releases from 1.21.1 on, which name each modifier by a key; the
 * releases from 1.21.11 already use the attribute names of Minecraft 1.21.2.
 */
public final class AttributePackets extends AttributeTranslation {
	@Override
	protected @NotNull String modifierId(@NotNull AttributeModifier modifier) {
		return modifier.getId().asString();
	}
}
