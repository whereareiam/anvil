package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.edition.java.protocol.type.entity.AttributeOperation;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.ModifierOperation;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The attributes of entities in the MCProtocolLib releases from 1.20.5 on, which name each attribute by the key of its
 * library type and each modifier as their release does. Each release's {@code AttributePackets} names a modifier.
 *
 * <p>The translation reports every attribute in the names of Minecraft 1.21.2, which dropped the {@code generic.},
 * {@code player.} and {@code zombie.} prefixes: {@code minecraft:generic.movement_speed} is reported as
 * {@code minecraft:movement_speed}. A key without such a prefix is reported as it is.</p>
 */
public abstract class AttributeTranslation implements PacketTranslation<Packet> {
	private static final Set<String> PREFIXES = Set.of("generic.", "player.", "zombie.");

	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundUpdateAttributes.class);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ClientboundUpdateAttributesPacket update)) return null;

		return new ClientboundUpdateAttributes(new EntityId(update.getEntityId()),
				update.getAttributes().stream().map(this::snapshot).toList());
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Names a modifier as the protocol does: by its key, or by its UUID's text before Minecraft 1.21.
	 *
	 * @param modifier the library's modifier
	 * @return the modifier's ID
	 */
	protected abstract @NotNull String modifierId(@NotNull AttributeModifier modifier);

	private AttributeSnapshot snapshot(Attribute attribute) {
		List<me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeModifier> modifiers =
				attribute.getModifiers().stream().map(this::modifier).toList();
		return new AttributeSnapshot(attributeKey(attribute.getType().getIdentifier()), attribute.getValue(), modifiers);
	}

	private me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeModifier modifier(
			AttributeModifier modifier
	) {
		return new me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeModifier(modifierId(modifier),
				modifier.getAmount(), operation(modifier.getOperation()));
	}

	/**
	 * Names an attribute as Minecraft 1.21.2 does, without the prefix of its group.
	 */
	private static String attributeKey(Key key) {
		String value = key.value();
		for (String prefix : PREFIXES) {
			if (!value.startsWith(prefix)) continue;

			return key.namespace() + ":" + value.substring(prefix.length());
		}
		return key.asString();
	}

	private static AttributeOperation operation(ModifierOperation operation) {
		return switch (operation) {
			case ADD -> AttributeOperation.ADD_VALUE;
			case ADD_MULTIPLIED_BASE -> AttributeOperation.ADD_MULTIPLIED_BASE;
			case ADD_MULTIPLIED_TOTAL -> AttributeOperation.ADD_MULTIPLIED_TOTAL;
		};
	}
}
