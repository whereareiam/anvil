package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveMobEffectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * The effects of entities in the MCProtocolLib releases from 1.20.5 on, which number each effect by its ID in the
 * game's {@code minecraft:mob_effect} registry. The library reads the number as its {@link Effect}, whose constants
 * follow that registry in its order and are named as it names them, so the translation reports the constant's name as
 * the effect's key in the {@code minecraft} namespace.
 */
public final class EffectPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundUpdateMobEffect.class, ClientboundRemoveMobEffect.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundUpdateMobEffectPacket update -> ClientboundUpdateMobEffect.builder()
					.entityId(update.getEntityId())
					.effect(key(update.getEffect()))
					.amplifier(update.getAmplifier())
					.duration(update.getDuration())
					.build();
			case ClientboundRemoveMobEffectPacket remove ->
					new ClientboundRemoveMobEffect(remove.getEntityId(), key(remove.getEffect()));
			default -> null;
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Returns the registry key of the library's effect.
	 */
	private static String key(Effect effect) {
		return "minecraft:" + effect.name().toLowerCase(Locale.ROOT);
	}
}
