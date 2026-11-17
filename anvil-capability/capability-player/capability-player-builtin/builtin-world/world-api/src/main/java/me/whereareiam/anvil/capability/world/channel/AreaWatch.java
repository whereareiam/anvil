package me.whereareiam.anvil.capability.world.channel;

import lombok.Value;
import me.whereareiam.anvil.capability.world.model.block.BlockArea;
import org.jetbrains.annotations.NotNull;

/**
 * An area the host asks the player's worker to watch, under an ID the host chose, which the worker's
 * {@link WatchedChanges} name and {@link WorldChannel#UNWATCH} closes.
 */
@Value
public class AreaWatch {
	/**
	 * The watch's ID, unique among the player's open watches.
	 */
	int id;
	/**
	 * The blocks to watch.
	 */
	@NotNull BlockArea area;
}
