package me.whereareiam.anvil.capability.movement.java.physics.server;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

/**
 * The packets of the player's protocol through which the vanilla client learns what the server sets of its player
 * ({@code ClientPacketListener}'s handlers), each handed to the {@link PlayerState} it updates. Packet listeners run
 * under the client's lock once the client's own player followed the packet, so the state reads the client's entity,
 * game mode and death as the packet left them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlayerStatePackets {
	/**
	 * The packets a release must carry for the client's physics to follow the server: those the state and the client's
	 * own player follow, and the teleports and velocities of the player's own entity, which the movement follows.
	 */
	public static final @NotNull List<Class<? extends JavaPacket>> PACKETS = List.of(
			ClientboundLogin.class, ClientboundRespawn.class, ClientboundGameEvent.class,
			ClientboundPlayerAbilities.class, ClientboundSetHealth.class, ClientboundPlayerCombatKill.class,
			ClientboundUpdateAttributes.class, ClientboundUpdateMobEffect.class, ClientboundRemoveMobEffect.class,
			ClientboundPlayerPosition.class, ClientboundSetEntityMotion.class
	);

	/**
	 * Follows the packets that set a player's state; a packet the release does not carry is left out, which
	 * {@link #PACKETS} lets the owner refuse.
	 *
	 * @param state the player's state
	 * @param protocol the player's protocol
	 * @return registration that stops following when closed
	 */
	public static @NotNull Subscription follow(@NotNull PlayerState state, @NotNull JavaProtocol protocol) {
		return Subscription.all(
				on(protocol, ClientboundLogin.class, login -> state.login()),
				on(protocol, ClientboundRespawn.class, respawn -> state.respawn()),
				on(protocol, ClientboundGameEvent.class, state::gameEvent),
				on(protocol, ClientboundPlayerAbilities.class, state::abilities),
				on(protocol, ClientboundSetHealth.class, state::health),
				on(protocol, ClientboundUpdateAttributes.class, state::attributes),
				on(protocol, ClientboundUpdateMobEffect.class, state::effect),
				on(protocol, ClientboundRemoveMobEffect.class, state::removeEffect)
		);
	}

	/**
	 * Listens to a packet the release carries.
	 */
	private static <P extends ClientboundPacket> @NotNull Subscription on(
			@NotNull JavaProtocol protocol,
			@NotNull Class<P> type,
			@NotNull Consumer<P> update
	) {
		if (!protocol.supports(type)) return () -> { };

		return protocol.listen(type, update);
	}
}
