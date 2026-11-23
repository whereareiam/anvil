package me.whereareiam.anvil.runner.extension;

import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionInput;
import me.whereareiam.anvil.tooling.api.type.action.ActionInputType;
import me.whereareiam.anvil.tooling.extension.api.model.ToolingArguments;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Validates portable contribution definitions and normalizes action arguments.
 */
final class ToolingDefinitionValidator {
	static ActionDefinition action(ActionDefinition definition) {
		identifier(definition.id());
		Set<String> names = new HashSet<>();
		for (ActionInput input : definition.inputs()) {
			if (!input.name().matches("[A-Za-z][A-Za-z0-9_-]*") || !names.add(input.name()))
				throw new IllegalArgumentException("Invalid or duplicate action input: " + input.name());
			if (input.sensitive() && input.defaultValue() != null)
				throw new IllegalArgumentException("Sensitive inputs cannot publish defaults: " + input.name());
			if (input.type() != ActionInputType.CHOICE && !input.choices().isEmpty())
				throw new IllegalArgumentException("Only choice inputs may declare choices: " + input.name());
			if (input.sensitive() && input.type() != ActionInputType.TEXT)
				throw new IllegalArgumentException("Sensitive inputs must be text: " + input.name());
			if (input.type() == ActionInputType.CHOICE && input.choices().isEmpty())
				throw new IllegalArgumentException("Choice input has no choices: " + input.name());
			if (input.defaultValue() != null) input.validate(input.defaultValue());
		}

		return definition;
	}

	static void observation(String id) {
		identifier(id);
	}

	static ToolingArguments arguments(ActionDefinition definition, Map<String, String> supplied) {
		Set<String> names = new HashSet<>();
		Map<String, String> values = new LinkedHashMap<>();
		for (ActionInput input : definition.inputs()) {
			names.add(input.name());
			String value = supplied.getOrDefault(input.name(), input.defaultValue());
			if (value == null) {
				if (input.required()) throw new IllegalArgumentException("Missing action input: " + input.name());
				continue;
			}

			input.validate(value);
			values.put(input.name(), value);
		}

		if (!names.containsAll(supplied.keySet())) {
			throw new IllegalArgumentException("Unknown action inputs: " + supplied.keySet());
		}

		return new ToolingArguments(values);
	}

	private static void identifier(String id) {
		if (!id.matches("[A-Za-z][A-Za-z0-9_.:-]*[.:][A-Za-z0-9_.:-]+"))
			throw new IllegalArgumentException("Tooling identifier must be namespaced: " + id);
	}
}
