package me.whereareiam.anvil.integration.intellij.view.window.main.environment.action;

import com.intellij.ui.components.JBLabel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;

import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDescriptor;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Filters contributed actions and observations for the selected runtime targets.
 */
final class TargetContributionsController {
	private final @NotNull EnvironmentSession session;
	private final @NotNull TargetContributionsPanel view;
	private final @NotNull JComboBox<ActionDescriptor> actions;
	private final @NotNull JButton invoke;
	private final @NotNull JBLabel availability;
	private final @NotNull DefaultListModel<ObservationDescriptor> observations;

	private List<ActionDescriptor> shownActions = List.of();
	private List<ObservationDescriptor> shownObservations = List.of();
	private boolean contributionsVisible;
	private boolean actionControlsVisible;

	TargetContributionsController(
			@NotNull EnvironmentSession session,
			@NotNull TargetContributionsPanel view
	) {
		this.session = session;
		this.view = view;

		actions = view.actions;
		invoke = view.invoke;
		availability = view.availability;
		observations = view.observations;
		actions.addActionListener(event -> updateAvailability());
		invoke.addActionListener(event -> {
			ActionDescriptor selected = (ActionDescriptor) actions.getSelectedItem();
			if (selected != null && invoke.isEnabled()) new ActionInvocationDialog(session, selected).show();
		});
	}

	public void update(@Nullable ActionTarget target, ActionTarget... additional) {
		Set<ActionTarget> targets = new HashSet<>(Arrays.asList(additional));
		if (target != null) targets.add(target);

		SessionSnapshot snapshot = session.getSnapshot();
		view.setMultipleScopes(targets.size() > 1);
		List<ActionDescriptor> currentActions =
				snapshot.getActions().stream()
						.filter(action -> targets.contains(action.getTarget()))
						.toList();
		List<ObservationDescriptor> currentObservations =
				snapshot.getObservations().stream()
						.filter(value -> targets.contains(value.getTarget()))
						.toList();

		updateActions(currentActions);
		updateObservations(currentObservations);
		updateVisibility(!currentActions.isEmpty() || !currentObservations.isEmpty(), !currentActions.isEmpty());
		updateAvailability(snapshot);
	}

	private void updateVisibility(boolean contributionsVisible, boolean actionControlsVisible) {
		if (this.contributionsVisible != contributionsVisible) {
			this.contributionsVisible = contributionsVisible;
			view.setVisible(contributionsVisible);
		}
		if (this.actionControlsVisible != actionControlsVisible) {
			this.actionControlsVisible = actionControlsVisible;
			view.setActionsVisible(actionControlsVisible);
		}
	}

	private void updateActions(@NotNull List<ActionDescriptor> current) {
		if (shownActions.equals(current)) return;

		ActionDescriptor previous = (ActionDescriptor) actions.getSelectedItem();
		shownActions = current;
		actions.setModel(new DefaultComboBoxModel<>(current.toArray(ActionDescriptor[]::new)));
		if (previous == null) return;

		ActionDescriptor replacement = current.stream()
				.filter(action -> sameAction(action, previous))
				.findFirst()
				.orElseGet(() -> current.stream()
						.filter(action -> sameDefinition(action, previous))
						.findFirst()
						.orElse(null));

		if (replacement != null) actions.setSelectedItem(replacement);
	}

	private void updateObservations(@NotNull List<ObservationDescriptor> current) {
		if (shownObservations.equals(current)) return;

		shownObservations = current;
		observations.clear();
		current.forEach(observations::addElement);
	}

	private static boolean sameAction(
			@NotNull ActionDescriptor first,
			@NotNull ActionDescriptor second
	) {
		return first.getTarget().equals(second.getTarget()) && sameDefinition(first, second);
	}

	private static boolean sameDefinition(
			@NotNull ActionDescriptor first,
			@NotNull ActionDescriptor second
	) {
		return first.getDefinition().getId().equals(second.getDefinition().getId());
	}

	private void updateAvailability() {
		updateAvailability(session.getSnapshot());
	}

	private void updateAvailability(@NotNull SessionSnapshot snapshot) {
		ActionDescriptor action = (ActionDescriptor) actions.getSelectedItem();
		boolean active = session.isActive()
				&& snapshot.getSessionId() != null
				&& snapshot.getState() == SessionState.RUNNING;

		invoke.setEnabled(active && action != null && action.getAvailability().isEnabled());
		String reason = action == null || action.getAvailability().isEnabled()
				? ""
				: action.getAvailability().getReason();

		availability.setText(
				!active ? "Environment is not running" : reason == null ? "Action unavailable" : reason);
	}

}
