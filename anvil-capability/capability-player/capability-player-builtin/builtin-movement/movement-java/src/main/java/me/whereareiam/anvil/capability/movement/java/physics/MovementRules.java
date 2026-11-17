package me.whereareiam.anvil.capability.movement.java.physics;

import lombok.Getter;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.java.physics.EntityRules;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * How the vanilla client of one Minecraft version moves its player: the {@link ClientBehaviour behaviours} its version
 * has, which the player's simulation asks for wherever versions differ, and the {@link EntityRules} its entities move
 * by.
 */
public final class MovementRules {
	@Getter
	private final @NotNull MinecraftVersion version;
	private final @NotNull Set<ClientBehaviour> behaviours;
	private final @NotNull EntityRules entity;

	/**
	 * Collects the behaviours of a client version.
	 *
	 * @param version the client's Minecraft version
	 */
	public MovementRules(@NotNull MinecraftVersion version) {
		this.version = version;
		this.behaviours = EnumSet.noneOf(ClientBehaviour.class);
		this.entity = new EntityRules(version);
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
	 * Returns how the client's entities move, its player's body among them.
	 *
	 * @return the entity rules of the client's version
	 */
	public @NotNull EntityRules entity() {
		return entity;
	}
}
