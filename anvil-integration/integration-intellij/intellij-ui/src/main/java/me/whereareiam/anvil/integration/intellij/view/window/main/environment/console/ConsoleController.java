package me.whereareiam.anvil.integration.intellij.view.window.main.environment.console;

import com.intellij.ui.components.JBList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;

import lombok.Getter;
import me.whereareiam.anvil.integration.intellij.component.console.ConsolePresentation;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.view.window.main.ScenarioPresentation;
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusPresentation;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns console filtering, command targeting, and native console presentation.
 */
public final class ConsoleController {
	private static final ProcessChoice ALL_PROCESSES = new ProcessChoice(null, "All processes", null);
	private static final ProcessChoice SELECT_TARGET = new ProcessChoice(null, "Select target", null);

	private final @NotNull EnvironmentSession session;
	@Getter
	private final @NotNull ConsolePresentation presentation;
	private final @NotNull DefaultListModel<ProcessChoice> sourceModel;
	private final @NotNull JBList<ProcessChoice> sources;
	private final @NotNull JComboBox<ProcessChoice> target;
	private final @NotNull CommandInput command;
	private final @NotNull JButton send;

	private @NotNull List<ProcessChoice> displayedChoices = List.of();
	private @NotNull Map<String, ProcessSnapshot> liveProcesses = Map.of();
	private @Nullable String outputProcess;
	private boolean updatingControls;

	ConsoleController(@NotNull EnvironmentSession session, @NotNull ConsolePanel view) {
		this.session = session;
		presentation = new ConsolePresentation(session.getIdeProject(), session.getLog(), session::stop, session);
		sourceModel = view.sourceModel;
		sources = view.sources;
		target = view.target;
		command = view.command;
		send = view.send;

		sourceModel.addElement(ALL_PROCESSES);
		sources.setSelectedIndex(0);
		sources.setCellRenderer(new ConsoleProcessRenderer(session, () -> liveProcesses));
		target.addItem(SELECT_TARGET);
		target.setPrototypeDisplayValue(SELECT_TARGET);
		sources.addListSelectionListener(event -> {
			if (!updatingControls && !event.getValueIsAdjusting()) selectOutput(true);
		});
		target.addActionListener(event -> {
			if (!updatingControls) updateControls();
		});
		send.addActionListener(event -> send());
		command.onSubmit(this::send);

		update();
	}

	public void update() {
		ScenarioDescriptor scenario = session.getScenario();
		SessionSnapshot snapshot = session.getSnapshot();
		Map<String, ProcessChoice> choicesByName = processChoices(scenario, snapshot);
		liveProcesses = liveProcesses(snapshot);
		List<ProcessChoice> choices = List.copyOf(choicesByName.values());
		if (!choices.equals(displayedChoices)) refreshChoices(choicesByName, choices);

		sources.repaint();
		updateControls(snapshot);
	}

	private static @NotNull Map<String, ProcessChoice> processChoices(
			@NotNull ScenarioDescriptor scenario,
			@NotNull SessionSnapshot snapshot
	) {
		Map<String, ProcessChoice> choices = new LinkedHashMap<>();
		for (ScenarioPresentation.ScenarioProcess process : ScenarioPresentation.processes(scenario, snapshot)) {
			ProcessRole role = process.getDefinition() == null ? null : process.getDefinition().getRole();
			choices.put(process.getName(), new ProcessChoice(process.getName(), process.getDisplayName(), role));
		}

		return choices;
	}

	private static @NotNull Map<String, ProcessSnapshot> liveProcesses(@NotNull SessionSnapshot snapshot) {
		Map<String, ProcessSnapshot> live = new LinkedHashMap<>();
		for (ProcessSnapshot process : snapshot.getProcesses())
			live.put(process.getName(), process);

		return Map.copyOf(live);
	}

	private void refreshChoices(
			@NotNull Map<String, ProcessChoice> choicesByName,
			@NotNull List<ProcessChoice> choices
	) {
		ProcessChoice selectedTarget = selectedTarget();
		displayedChoices = choices;
		updatingControls = true;

		try {
			sourceModel.clear();
			target.removeAllItems();
			sourceModel.addElement(ALL_PROCESSES);
			target.addItem(SELECT_TARGET);
			for (ProcessChoice choice : choices) {
				sourceModel.addElement(choice);
				target.addItem(choice);
			}

			sources.setSelectedValue(selectedOutput(choicesByName), false);
			if (selectedTarget != null) {
				target.setSelectedItem(
						choicesByName.getOrDefault(selectedTarget.name(), SELECT_TARGET)
				);
			}
		} finally {
			updatingControls = false;
		}

		selectOutput(false);
	}

	private @NotNull ProcessChoice selectedOutput(@NotNull Map<String, ProcessChoice> choicesByName) {
		if (outputProcess == null) return ALL_PROCESSES;
		return choicesByName.getOrDefault(outputProcess, ALL_PROCESSES);
	}

	private void selectOutput(boolean chooseCommandTarget) {
		ProcessChoice selected = sources.getSelectedValue();
		String selectedName = selected == null ? null : selected.name();
		if (!Objects.equals(outputProcess, selectedName)) {
			outputProcess = selectedName;
			presentation.setProcessFilter(selectedName);
		}

		if (chooseCommandTarget) target.setSelectedItem(selectedName == null ? SELECT_TARGET : selected);

		updateControls();
	}

	private void updateControls() {
		updateControls(session.getSnapshot());
	}

	private void updateControls(@NotNull SessionSnapshot snapshot) {
		ProcessChoice selected = selectedTarget();
		String selectedName = selected == null ? null : selected.name();
		command.scope(selectedName, "console");
		ProcessSnapshot process = ScenarioPresentation.liveProcess(snapshot, selectedName);
		boolean available = session.isActive()
				&& snapshot.getState() == SessionState.RUNNING
				&& process != null
				&& process.getState() == ProcessState.READY;

		command.setEnabled(available);
		send.setEnabled(available);
		command.getEmptyText().setText(selectedName == null ? "Choose a command target" : "Console command");
		target.setToolTipText(selectedName == null
				? "Choose one process to receive the command"
				: selected.displayName()
				+ " · "
				+ StatusPresentation.processDescription(
				process == null ? null : process.getState(), snapshot.getState()));
	}

	private @Nullable ProcessChoice selectedTarget() {
		return (ProcessChoice) target.getSelectedItem();
	}

	private void send() {
		SessionSnapshot snapshot = session.getSnapshot();
		updateControls(snapshot);
		ProcessChoice selected = selectedTarget();
		if (!send.isEnabled()
				|| selected == null
				|| selected.name() == null
				|| command.getText().isBlank()) return;

		command.submit();
	}

	public record ProcessChoice(
			@Nullable String name,
			@NotNull String displayName,
			@Nullable ProcessRole role
	) {
		@Override
		public @NotNull String toString() {
			return displayName;
		}
	}
}
