package me.whereareiam.anvil.capability.overlays.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.overlays.model.BossBarEvent;
import me.whereareiam.anvil.capability.overlays.model.Title;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;

/**
 * The channel contract between the host overlays capability and its worker extensions: the capability ID both register
 * under and the events the worker emits.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OverlaysChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.overlays";
	/**
	 * A title the player's current connection shows, with its subtitle and times.
	 */
	public static final EventDescriptor<Title> TITLE = new EventDescriptor<>("overlays.title", Title.class);
	/**
	 * An action bar the player's current connection received, as plain text.
	 */
	public static final EventDescriptor<String> ACTION_BAR = new EventDescriptor<>("overlays.action-bar", String.class);
	/**
	 * A boss bar the player's current connection added, changed or removed.
	 */
	public static final EventDescriptor<BossBarEvent> BOSS_BAR = new EventDescriptor<>(
			"overlays.boss-bar", BossBarEvent.class
	);
}
