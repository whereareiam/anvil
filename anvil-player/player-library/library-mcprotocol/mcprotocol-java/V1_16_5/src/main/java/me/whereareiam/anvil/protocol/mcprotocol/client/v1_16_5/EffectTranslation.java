package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.Effect;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityEffectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityRemoveEffectPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The effects of entities in the MCProtocolLib releases from 1.16.5 until 1.17.1, which number their effects and read
 * each number into the library's effect, whose names changed between the releases, so each release's
 * {@code EffectPackets} names an effect by its key.
 */
public abstract class EffectTranslation implements PacketTranslation<Packet> {
	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundUpdateMobEffect.class, ClientboundRemoveMobEffect.class);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ServerEntityEffectPacket update -> ClientboundUpdateMobEffect.builder()
					.entityId(update.getEntityId())
					.effect(key(update.getEffect()))
					.amplifier(update.getAmplifier())
					.duration(update.getDuration())
					.build();
			case ServerEntityRemoveEffectPacket removal ->
					new ClientboundRemoveMobEffect(removal.getEntityId(), key(removal.getEffect()));
			default -> null;
		};
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Names an effect of the release by its key.
	 *
	 * @param effect the library's effect
	 * @return the effect's key with its namespace, such as {@code minecraft:speed}
	 */
	protected abstract @NotNull String key(@NotNull Effect effect);
}
