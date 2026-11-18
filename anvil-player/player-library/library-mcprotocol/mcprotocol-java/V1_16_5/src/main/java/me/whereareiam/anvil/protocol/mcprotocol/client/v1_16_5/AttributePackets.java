package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.MagicValues;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.Attribute;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.AttributeType;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.ModifierOperation;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityPropertiesPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.edition.java.protocol.type.entity.AttributeOperation;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The attributes of entities in the MCProtocolLib releases from 1.16.5 until 1.17.1, which read an attribute's key into
 * the library's attribute type and keep the key in the library's registry of magic values. Minecraft names the vanilla
 * attributes of these releases with a category before a dot, such as {@code minecraft:generic.movement_speed} or
 * {@code minecraft:horse.jump_strength}, which Minecraft 1.21.2 dropped, so the translation reports every key of the
 * {@code minecraft} namespace without it; keys of other namespaces, such as Forge's {@code forge:entity_gravity}, keep
 * their names. A modifier of these releases is named by its UUID, which the translation reports in its text form.
 */
public final class AttributePackets implements PacketTranslation<Packet> {
	private static final String MINECRAFT = "minecraft:";
	private static final char CATEGORY_SEPARATOR = '.';

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundUpdateAttributes.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ServerEntityPropertiesPacket properties)) return null;

		return new ClientboundUpdateAttributes(new EntityId(properties.getEntityId()),
				properties.getAttributes().stream().map(AttributePackets::snapshot).toList());
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	private static AttributeSnapshot snapshot(Attribute attribute) {
		return new AttributeSnapshot(key(attribute.getType()), attribute.getValue(), attribute.getModifiers().stream()
				.map(modifier -> new AttributeModifier(modifier.getUuid().toString(), modifier.getAmount(),
						operation(modifier.getOperation())))
				.toList());
	}

	/**
	 * Names an attribute by the key the library reads it from, without the category Minecraft 1.21.2 dropped.
	 */
	private static String key(AttributeType type) {
		String key = MagicValues.value(String.class, type);
		if (!key.startsWith(MINECRAFT)) return key;

		int category = key.indexOf(CATEGORY_SEPARATOR, MINECRAFT.length());
		return category < 0 ? key : MINECRAFT + key.substring(category + 1);
	}

	private static AttributeOperation operation(ModifierOperation operation) {
		return switch (operation) {
			case ADD -> AttributeOperation.ADD_VALUE;
			case ADD_MULTIPLIED -> AttributeOperation.ADD_MULTIPLIED_BASE;
			case MULTIPLY -> AttributeOperation.ADD_MULTIPLIED_TOTAL;
		};
	}
}
