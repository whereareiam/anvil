package me.whereareiam.anvil.capability.entities.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The entities the player's current connection knows, as the worker answers a read of them.
 */
@Value
public class KnownEntities {
	/**
	 * The known entities in the order they appeared, each where it is now.
	 */
	@NotNull List<SeenEntity> entities;
}
