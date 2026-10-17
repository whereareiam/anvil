package me.whereareiam.anvil.capability.entities;

import lombok.Value;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The entities the player's current connection knows, as the worker answers a read of them.
 */
@Value
public class KnownEntities {
	@NotNull List<SeenEntity> entities;
}
