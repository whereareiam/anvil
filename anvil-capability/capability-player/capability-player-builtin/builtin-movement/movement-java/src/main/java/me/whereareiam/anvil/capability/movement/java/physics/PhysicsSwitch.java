package me.whereareiam.anvil.capability.movement.java.physics;

import lombok.Getter;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerStatePackets;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.block.JavaBlockData;
import me.whereareiam.anvil.protocol.java.block.exception.BlockDataUnavailableException;
import me.whereareiam.anvil.protocol.java.physics.world.BlockReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Whether client physics ticks the player, and why it turned itself off: it turns on only where the player's release
 * carries what physics follows and Anvil ships the block data of its protocol, whose reader it then keeps over the
 * player's world, which keeps chunks from the binding on, so physics turned on later knows the blocks sent before.
 *
 * <p>Not thread-safe; its owner serializes operations and ticks.</p>
 */
final class PhysicsSwitch {
	private final @NotNull JavaProtocol protocol;
	private final @NotNull JavaWorld world;
	private final @NotNull Runnable stopped;
	@Getter
	private boolean enabled;
	@Getter
	private @Nullable String failure;
	private @Nullable BlockReader reader;

	/**
	 * @param protocol the player's protocol
	 * @param stopped what to do when physics turns off: the client stays where the simulated player was
	 */
	PhysicsSwitch(@NotNull JavaProtocol protocol, @NotNull Runnable stopped) {
		this.protocol = protocol;
		this.world = protocol.world();
		this.stopped = stopped;
	}

	/**
	 * Returns the blocks the client knows while physics is on, or {@code null} while it is off.
	 */
	@Nullable BlockReader blocks() {
		return enabled ? reader : null;
	}

	/**
	 * Turns physics on, unless it is.
	 *
	 * @return why it cannot run for the player's release, or {@code null}
	 */
	@Nullable String turnOn() {
		String unavailable = unavailable();
		if (unavailable != null || enabled) return unavailable;

		enabled = true;
		failure = null;
		return null;
	}

	/**
	 * Turns physics off.
	 *
	 * @param reason why a tick turned it off, or {@code null} when a test did
	 */
	void turnOff(@Nullable String reason) {
		stopped.run();
		enabled = false;
		failure = reason;
	}

	private @Nullable String unavailable() {
		try {
			protocol.require("what client physics follows", PlayerStatePackets.PACKETS);
			protocol.require("the flight client physics toggles", List.of(ServerboundPlayerAbilities.class));
			if (reader == null) reader = new BlockReader(world, JavaBlockData.shipped().require(protocol.protocolNumber()));
			return null;
		} catch (ProtocolUnavailableException | BlockDataUnavailableException exception) {
			return exception.getMessage();
		}
	}
}
