package me.whereareiam.anvil.integration.intellij.view.window.main.component.status

import me.whereareiam.anvil.integration.intellij.type.EnvironmentState
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor
import me.whereareiam.anvil.tooling.api.type.ProcessState
import me.whereareiam.anvil.tooling.api.type.SessionState

/**
 * UI labels and visual tones for the typed lifecycle states owned by the runtime.
 */
object StatusPresentation {
	@JvmStatic fun processLabel(state: ProcessState?, session: SessionState): String {
		if (session == SessionState.STOPPING) return "Stopping"
		if (session == SessionState.STOPPED) return "Stopped"
		if (session == SessionState.FAILED && state != null && state != ProcessState.FAILED &&
			state != ProcessState.STOPPED && state != ProcessState.CREATED) return "Status unavailable"

		return when (state) {
			null, ProcessState.CREATED -> "Not started"
			ProcessState.STARTING -> "Starting"
			ProcessState.READY -> "Running"
			ProcessState.STOPPING -> "Stopping"
			ProcessState.STOPPED -> "Stopped"
			ProcessState.FAILED -> "Failed"
			ProcessState.UNKNOWN -> "Unknown"
		}
	}

	@JvmStatic fun processTone(state: ProcessState?, session: SessionState): Tone {
		if (session == SessionState.STOPPING || state == ProcessState.STARTING || state == ProcessState.STOPPING)
			return Tone.TRANSITION
		if (state == ProcessState.FAILED) return Tone.FAILURE
		if (session == SessionState.FAILED && state != null && state != ProcessState.STOPPED &&
			state != ProcessState.CREATED) return Tone.NEUTRAL
		if (state == ProcessState.READY && session != SessionState.STOPPED) return Tone.RUNNING

		return Tone.NEUTRAL
	}

	@JvmStatic fun sessionLabel(state: SessionState): String = when (state) {
		SessionState.IDLE -> "Not started"
		SessionState.STARTING -> "Starting"
		SessionState.RUNNING -> "Running"
		SessionState.STOPPING -> "Stopping"
		SessionState.STOPPED -> "Stopped"
		SessionState.FAILED -> "Failed"
	}

	@JvmStatic fun environmentLabel(state: EnvironmentState): String = when (state) {
		EnvironmentState.NOT_STARTED -> "Not started"
		EnvironmentState.STARTING -> "Starting"
		EnvironmentState.READY_TO_START -> "Ready to start"
		EnvironmentState.PARTIALLY_RUNNING -> "Partially running"
		EnvironmentState.SETUP_PENDING -> "Setup pending"
		EnvironmentState.RUNNING -> "Running"
		EnvironmentState.STOPPING -> "Stopping"
		EnvironmentState.STOPPED -> "Stopped"
		EnvironmentState.FAILED -> "Failed"
	}

	@JvmStatic fun environmentTone(state: EnvironmentState): Tone = when (state) {
		EnvironmentState.STARTING,
		EnvironmentState.PARTIALLY_RUNNING,
		EnvironmentState.SETUP_PENDING,
		EnvironmentState.STOPPING -> Tone.TRANSITION
		EnvironmentState.RUNNING -> Tone.RUNNING
		EnvironmentState.FAILED -> Tone.FAILURE
		EnvironmentState.NOT_STARTED,
		EnvironmentState.READY_TO_START,
		EnvironmentState.STOPPED -> Tone.NEUTRAL
	}

	@JvmStatic fun processDescription(state: ProcessState?, session: SessionState): String {
		val label = processLabel(state, session)
		if (session == SessionState.FAILED && state != null && state != ProcessState.FAILED &&
			state != ProcessState.STOPPED && state != ProcessState.CREATED)
			return "$label · Last reported: ${processLabel(state, SessionState.RUNNING)} · Environment failed"

		return label
	}

	@JvmStatic fun environmentDescription(
		scenario: ScenarioDescriptor,
		snapshot: SessionSnapshot,
		state: EnvironmentState
	): String {
		if (state == EnvironmentState.NOT_STARTED || state == EnvironmentState.STOPPED ||
			state == EnvironmentState.STOPPING || state == EnvironmentState.FAILED) return environmentLabel(state)

		val total = processCount(scenario, snapshot)
		return environmentLabel(state) + if (total == 0) "" else " · ${runningCount(snapshot)} of $total processes running"
	}

	@JvmStatic fun processSummary(scenario: ScenarioDescriptor, snapshot: SessionSnapshot): String {
		val total = processCount(scenario, snapshot)
		if (snapshot.state == SessionState.FAILED || snapshot.state == SessionState.STOPPING) return "$total configured"

		val running = if (snapshot.state == SessionState.STOPPED) 0 else runningCount(snapshot)
		return "$running of $total running"
	}

	private fun runningCount(snapshot: SessionSnapshot) = snapshot.processes.count { it.state == ProcessState.READY }

	private fun processCount(scenario: ScenarioDescriptor, snapshot: SessionSnapshot) =
		maxOf(scenario.processes.size, snapshot.processes.size)

	enum class Tone {
		NEUTRAL,
		RUNNING,
		TRANSITION,
		FAILURE
	}
}
