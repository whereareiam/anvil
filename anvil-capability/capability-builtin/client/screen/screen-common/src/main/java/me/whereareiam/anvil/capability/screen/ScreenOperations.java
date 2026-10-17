package me.whereareiam.anvil.capability.screen;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.screen.model.BossBarEvent;
import me.whereareiam.anvil.capability.screen.model.Title;

/**
 * Typed screen events shared by the host screen capability and the worker's screen binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScreenOperations {
	/**
	 * A title the player's current native session shows, with its subtitle and times.
	 */
	public static final EventDescriptor<Title> TITLE = new EventDescriptor<>("screen.title", Title.class);
	/**
	 * An action bar the player's current native session received, as plain text.
	 */
	public static final EventDescriptor<String> ACTION_BAR = new EventDescriptor<>("screen.action-bar", String.class);
	/**
	 * A boss bar the player's current native session added, changed or removed.
	 */
	public static final EventDescriptor<BossBarEvent> BOSS_BAR = new EventDescriptor<>("screen.boss-bar", BossBarEvent.class);
}
