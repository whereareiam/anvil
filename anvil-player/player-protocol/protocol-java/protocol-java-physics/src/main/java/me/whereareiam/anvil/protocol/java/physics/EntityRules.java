package me.whereareiam.anvil.protocol.java.physics;

import lombok.Getter;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.java.geometry.Trigonometry;
import me.whereareiam.anvil.protocol.java.geometry.VectorLength;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * How the vanilla client of one Minecraft version moves entities: the {@link EntityBehaviour behaviours} its version
 * has, which the physics asks for wherever versions differ.
 *
 * <pre>{@code
 * EntityRules rules = new EntityRules(MinecraftVersion.parse("1.21.11"));
 * boolean supported = rules.has(EntityBehaviour.SUPPORTING_BLOCK);
 * }</pre>
 */
public final class EntityRules {
	@Getter
	private final @NotNull MinecraftVersion version;
	private final @NotNull Set<EntityBehaviour> behaviours;

	/**
	 * Collects the behaviours of a client version.
	 *
	 * @param version the client's Minecraft version
	 */
	public EntityRules(@NotNull MinecraftVersion version) {
		this.version = version;
		this.behaviours = EnumSet.noneOf(EntityBehaviour.class);
		Arrays.stream(EntityBehaviour.values()).filter(behaviour -> behaviour.appliesTo(version)).forEach(behaviours::add);
	}

	/**
	 * Tells whether entities of the client's version behave so.
	 *
	 * @param behaviour the behaviour
	 * @return whether the client's version has it
	 */
	public boolean has(@NotNull EntityBehaviour behaviour) {
		return behaviours.contains(behaviour);
	}

	/**
	 * Returns the sine and cosine the client computes with.
	 *
	 * @return the client's table trigonometry
	 */
	public @NotNull Trigonometry trigonometry() {
		return has(EntityBehaviour.DOUBLE_TRIGONOMETRY) ? Trigonometry.DOUBLE : Trigonometry.FLOAT;
	}

	/**
	 * Returns the length and direction of vectors the client computes with.
	 *
	 * @return the client's vector length
	 */
	public @NotNull VectorLength vectorLength() {
		if (has(EntityBehaviour.FLOAT_VECTOR_LENGTH)) return VectorLength.FLOAT;

		return has(EntityBehaviour.FINE_NORMALIZATION) ? VectorLength.FINE_DOUBLE : VectorLength.DOUBLE;
	}
}
