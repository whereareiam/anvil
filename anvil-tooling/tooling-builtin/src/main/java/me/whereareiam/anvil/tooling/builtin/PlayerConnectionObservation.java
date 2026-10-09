package me.whereareiam.anvil.tooling.builtin;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationValue;
import me.whereareiam.anvil.tooling.api.type.ObservationTone;
import me.whereareiam.anvil.tooling.extension.api.observation.scoped.player.PlayerCapabilityObservation;
import org.jetbrains.annotations.NotNull;

/**
 * Reads connection state through the current player's Session capability.
 */
final class PlayerConnectionObservation extends PlayerCapabilityObservation<Session> {
	PlayerConnectionObservation() {
		super(Session.class, ObservationDefinition.builder().id("anvil.session.connection").displayName("Connection").build());
	}

	@Override
	public @NotNull ObservationValue observe(@NotNull SimulatedPlayer player, @NotNull Session session) {
		boolean connected = session.state().connected();
		return ObservationValue.builder().text(connected ? "Connected" : "Disconnected")
				.tone(connected ? ObservationTone.SUCCESS : ObservationTone.WARNING).build();
	}
}
