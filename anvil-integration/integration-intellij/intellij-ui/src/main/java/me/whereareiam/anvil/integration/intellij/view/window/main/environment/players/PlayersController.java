package me.whereareiam.anvil.integration.intellij.view.window.main.environment.players;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultListModel;

import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.action.TargetContributionsPanel;
import me.whereareiam.anvil.tooling.api.model.PlayerDescriptor;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget;
import me.whereareiam.anvil.tooling.api.type.action.ActionTargetType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Retains observed players and coordinates the selected player controls.
 */
final class PlayersController {
	private final @NotNull EnvironmentSession session;
	private final @NotNull Map<String, PlayerDescriptor> retainedPlayers = new LinkedHashMap<>();
	private final @NotNull DefaultListModel<PlayerDescriptor> model;
	private final @NotNull JBList<PlayerDescriptor> players;
	private final @NotNull JBLabel title;
	private final @NotNull JBLabel info;
	private final @NotNull TargetContributionsPanel contributions;

	private @NotNull List<PlayerDescriptor> displayedPlayers = List.of();
	private boolean updatingList;

	PlayersController(
			@NotNull EnvironmentSession session,
			@NotNull PlayersPanel view
	) {
		this.session = session;

		model = view.model;
		players = view.players;
		title = view.title;
		info = view.info;
		contributions = view.contributions;

		players.setCellRenderer(new PlayerRenderer(player -> status(player, session.getSnapshot())));
		players.addListSelectionListener(event -> {
			if (!updatingList) updateControls(session.getSnapshot());
		});
	}

	void update() {
		SessionSnapshot snapshot = session.getSnapshot();
		retainedPlayers.putAll(index(snapshot.getPlayers()));
		List<PlayerDescriptor> current = List.copyOf(retainedPlayers.values());

		if (!current.equals(displayedPlayers)) refreshList(current);
		players.repaint();
		updateControls(snapshot);
	}

	private static @NotNull Map<String, PlayerDescriptor> index(
			@NotNull List<PlayerDescriptor> players
	) {
		Map<String, PlayerDescriptor> indexed = new LinkedHashMap<>();
		for (PlayerDescriptor player : players) indexed.put(player.getName(), player);

		return indexed;
	}

	private void refreshList(@NotNull List<PlayerDescriptor> current) {
		PlayerDescriptor previous = players.getSelectedValue();
		displayedPlayers = current;
		updatingList = true;

		try {
			model.clear();
			current.forEach(model::addElement);
			restoreSelection(previous);
		} finally {
			updatingList = false;
		}
	}

	private void restoreSelection(@Nullable PlayerDescriptor previous) {
		if (previous != null && retainedPlayers.containsKey(previous.getName())) {
			players.setSelectedValue(retainedPlayers.get(previous.getName()), true);
			return;
		}

		if (!model.isEmpty()) players.setSelectedIndex(0);
	}

	private void updateControls(@NotNull SessionSnapshot snapshot) {
		PlayerDescriptor selected = players.getSelectedValue();
		updateContributions(selected);
		title.setText(selected == null ? "No player selected" : selected.getDisplayName());
		info.setText(selected == null
				? "Players appear when the scenario creates them."
				: status(selected, snapshot).getLabel());
	}

	private void updateContributions(@Nullable PlayerDescriptor selected) {
		if (selected == null) {
			contributions.update(null);
			return;
		}

		contributions.update(target(selected.getName()));
	}

	private static @NotNull ActionTarget target(@NotNull String player) {
		return ActionTarget.builder().type(ActionTargetType.PLAYER).name(player).build();
	}

	private @NotNull PlayerStatus status(
			@NotNull PlayerDescriptor player,
			@NotNull SessionSnapshot snapshot
	) {
		if (!session.isActive()) return PlayerStatus.COMPLETED;
		if (snapshot.getPlayers().stream().noneMatch(present -> present.getName().equals(player.getName())))
			return PlayerStatus.LEFT;

		return PlayerStatus.PRESENT;
	}
}
