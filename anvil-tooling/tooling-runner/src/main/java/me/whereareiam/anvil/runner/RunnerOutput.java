package me.whereareiam.anvil.runner;

import me.whereareiam.anvil.api.runtime.AnvilContext;
import me.whereareiam.anvil.api.runtime.RunningProcess;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;

import java.io.PrintWriter;

final class RunnerOutput {
	static void printRegistry(ScenarioRegistry registry, PrintWriter output) {
		output.println("Scenarios:");
		registry.scenarios().forEach(scenario ->
				output.println("  " + scenario.getName() + (scenario.isManual() ? " (manual)" : ""))
		);

		output.println("Groups:");
		registry.groups().forEach(group ->
				output.println("  " + group.getName() + " -> " + group.getScenarios())
		);
	}

	static void printAddresses(AnvilContext context, PrintWriter output) {
		output.println("Scenario '" + context.scenario().getName() + "' is ready:");
		output.println("  Join: " + context.process(context.scenario().getEntrypoint()).address());
		for (RunningProcess process : context.processes()) {
			output.println("  " + process.name() + " [" + process.state() + "] " + process.address());
		}
	}
}
