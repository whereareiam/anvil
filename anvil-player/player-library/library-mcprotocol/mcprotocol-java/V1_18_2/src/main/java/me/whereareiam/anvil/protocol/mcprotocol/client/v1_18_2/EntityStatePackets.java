package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.Effect;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.Attribute;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.ModifierOperation;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundRemoveMobEffectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.protocol.java.api.type.entity.AttributeOperation;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The attributes and effects of entities in the MCProtocolLib releases from 1.18.2 until 1.19.4, the entity state
 * besides entity data, which needs the entity's type and stays with the entity translation.
 *
 * <p>These releases name attributes with the {@code generic.}, {@code player.}, {@code zombie.} or {@code horse.}
 * prefix that Minecraft 1.21.2 dropped, and the library namespaces each name; the translation reports a
 * {@code minecraft} attribute without its prefix, as {@code minecraft:movement_speed}, and an attribute of another
 * namespace as the server named it. A modifier is identified by its UUID, which the translation reports in its text
 * form. The releases number their effects, which the library reads into its {@link Effect} enum, whose constants carry
 * the names of the game's registry; the translation reports each in the {@code minecraft} namespace.</p>
 */
public final class EntityStatePackets implements PacketTranslation<Packet> {
	private static final String MINECRAFT = "minecraft";
	private static final char NAMESPACE_SEPARATOR = ':';
	private static final List<String> RELEASE_PREFIXES = List.of("generic.", "player.", "zombie.", "horse.");

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundUpdateAttributes.class, ClientboundUpdateMobEffect.class,
				ClientboundRemoveMobEffect.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundUpdateAttributesPacket attributes ->
					new ClientboundUpdateAttributes(new EntityId(attributes.getEntityId()),
							attributes.getAttributes().stream().map(EntityStatePackets::snapshot).toList());
			case ClientboundUpdateMobEffectPacket effect -> ClientboundUpdateMobEffect.builder()
					.entityId(new EntityId(effect.getEntityId()))
					.effect(effect(effect.getEffect()))
					.amplifier(effect.getAmplifier())
					.duration(effect.getDuration())
					.build();
			case ClientboundRemoveMobEffectPacket effect ->
					new ClientboundRemoveMobEffect(new EntityId(effect.getEntityId()), effect(effect.getEffect()));
			default -> null;
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	private static AttributeSnapshot snapshot(Attribute attribute) {
		List<AttributeModifier> modifiers = attribute.getModifiers().stream()
				.map(modifier -> new AttributeModifier(modifier.getUuid().toString(), modifier.getAmount(),
						operation(modifier.getOperation())))
				.toList();
		return new AttributeSnapshot(attribute(attribute.getType().getIdentifier()), attribute.getValue(), modifiers);
	}

	/**
	 * Names an attribute as Minecraft 1.21.2 does, without the prefix the releases give a {@code minecraft} attribute.
	 */
	private static String attribute(String identifier) {
		int separator = identifier.indexOf(NAMESPACE_SEPARATOR);
		if (!identifier.substring(0, separator).equals(MINECRAFT)) return identifier;

		String path = identifier.substring(separator + 1);
		for (String prefix : RELEASE_PREFIXES)
			if (path.startsWith(prefix)) return MINECRAFT + NAMESPACE_SEPARATOR + path.substring(prefix.length());

		return identifier;
	}

	private static AttributeOperation operation(ModifierOperation operation) {
		return switch (operation) {
			case ADD -> AttributeOperation.ADD_VALUE;
			case ADD_MULTIPLIED -> AttributeOperation.ADD_MULTIPLIED_BASE;
			case MULTIPLY -> AttributeOperation.ADD_MULTIPLIED_TOTAL;
		};
	}

	/**
	 * Names an effect by its key in the game's registry, which the library's constant spells in capitals.
	 */
	private static String effect(Effect effect) {
		return MINECRAFT + NAMESPACE_SEPARATOR + effect.name().toLowerCase(Locale.ROOT);
	}
}
