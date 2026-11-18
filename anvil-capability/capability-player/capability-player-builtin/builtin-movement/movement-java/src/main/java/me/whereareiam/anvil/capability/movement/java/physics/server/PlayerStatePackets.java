package me.whereareiam.anvil.capability.movement.java.physics.server;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.player.api.channel.Subscription;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The packets of the player's protocol through which the vanilla client learns what the server sets of its player
 * ({@code ClientPacketListener}'s handlers), each handed to the {@link PlayerState} it updates.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlayerStatePackets {
	/**
	 * The packets a release must carry for the client's physics to follow the server: those the state follows, and the
	 * teleports and velocities of the player's own entity, which the movement follows.
	 */
	public static final @NotNull List<Class<? extends JavaPacket>> PACKETS = List.of(
			ClientboundLogin.class, ClientboundRespawn.class, ClientboundGameEvent.class,
			ClientboundPlayerAbilities.class, ClientboundSetHealth.class, ClientboundPlayerCombatKill.class,
			ClientboundUpdateAttributes.class, ClientboundUpdateMobEffect.class, ClientboundRemoveMobEffect.class,
			ClientboundPlayerPosition.class, ClientboundSetEntityMotion.class
	);

	/**
	 * Follows the packets that set a player's state, each update run through the owner's serialization; a packet the
	 * release does not carry is left out, which {@link #PACKETS} lets the owner refuse.
	 *
	 * @param state the player's state
	 * @param protocol the player's protocol
	 * @param serialized runs each update as the owner serializes packets and ticks
	 * @return registration that stops following when closed
	 */
	public static @NotNull Subscription follow(
			@NotNull PlayerState state,
			@NotNull JavaProtocol protocol,
			@NotNull Executor serialized
	) {
		Follower follower = new Follower(protocol, serialized);
		return Subscription.all(
				follower.on(ClientboundLogin.class, state::login),
				follower.on(ClientboundRespawn.class, state::respawn),
				follower.on(ClientboundGameEvent.class, state::gameEvent),
				follower.on(ClientboundPlayerAbilities.class, state::abilities),
				follower.on(ClientboundSetHealth.class, state::health),
				follower.on(ClientboundPlayerCombatKill.class, state::combatKill),
				follower.on(ClientboundUpdateAttributes.class, state::attributes),
				follower.on(ClientboundUpdateMobEffect.class, state::effect),
				follower.on(ClientboundRemoveMobEffect.class, state::removeEffect)
		);
	}

	/**
	 * Listens to the packets the release carries, through the owner's serialization.
	 */
	private record Follower(@NotNull JavaProtocol protocol, @NotNull Executor serialized) {
		private <P extends ClientboundPacket> @NotNull Subscription on(@NotNull Class<P> type, @NotNull Consumer<P> update) {
			if (!protocol.supports(type)) return () -> { };

			return protocol.listen(type, packet -> serialized.execute(() -> update.accept(packet)));
		}
	}
}
