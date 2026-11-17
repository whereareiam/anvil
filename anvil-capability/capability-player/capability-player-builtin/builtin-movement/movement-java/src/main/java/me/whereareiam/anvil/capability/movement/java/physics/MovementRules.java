package me.whereareiam.anvil.capability.movement.java.physics;

import lombok.Getter;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Trigonometry;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * How the vanilla client of one Minecraft version moves its player: the {@link ClientBehaviour behaviours} its version
 * has, which the simulation asks for wherever versions differ.
 */
public final class MovementRules {
	@Getter
	private final @NotNull MinecraftVersion version;
	private final @NotNull Set<ClientBehaviour> behaviours;

	/**
	 * Collects the behaviours of a client version.
	 *
	 * @param version the client's Minecraft version
	 */
	public MovementRules(@NotNull MinecraftVersion version) {
		this.version = version;
		this.behaviours = EnumSet.noneOf(ClientBehaviour.class);
		Arrays.stream(ClientBehaviour.values()).filter(behaviour -> behaviour.appliesTo(version)).forEach(behaviours::add);
	}

	/**
	 * Tells whether the client behaves so.
	 *
	 * @param behaviour the behaviour
	 * @return whether the client's version has it
	 */
	public boolean has(@NotNull ClientBehaviour behaviour) {
		return behaviours.contains(behaviour);
	}

	/**
	 * Returns the sine and cosine the client computes with.
	 *
	 * @return the client's table trigonometry
	 */
	public @NotNull Trigonometry trigonometry() {
		return has(ClientBehaviour.DOUBLE_TRIGONOMETRY) ? Trigonometry.DOUBLE : Trigonometry.FLOAT;
	}
}
