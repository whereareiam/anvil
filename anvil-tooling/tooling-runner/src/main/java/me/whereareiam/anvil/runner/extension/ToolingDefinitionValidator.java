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
		identifier(definition.getId());
		Set<String> names = new HashSet<>();
		for (ActionInput input : definition.getInputs()) {
			if (!input.getName().matches("[A-Za-z][A-Za-z0-9_-]*") || !names.add(input.getName()))
				throw new IllegalArgumentException("Invalid or duplicate action input: " + input.getName());
			if (input.isSensitive() && input.getDefaultValue() != null)
				throw new IllegalArgumentException("Sensitive inputs cannot publish defaults: " + input.getName());
			if (input.getType() != ActionInputType.CHOICE && !input.getChoices().isEmpty())
				throw new IllegalArgumentException("Only choice inputs may declare choices: " + input.getName());
			if (input.isSensitive() && input.getType() != ActionInputType.TEXT)
				throw new IllegalArgumentException("Sensitive inputs must be text: " + input.getName());
			if (input.getType() == ActionInputType.CHOICE && input.getChoices().isEmpty())
				throw new IllegalArgumentException("Choice input has no choices: " + input.getName());
			if (input.getDefaultValue() != null) input.validate(input.getDefaultValue());
		}

		return definition;
	}

	static void observation(String id) {
		identifier(id);
	}

	static ToolingArguments arguments(ActionDefinition definition, Map<String, String> supplied) {
		Set<String> names = new HashSet<>();
		Map<String, String> values = new LinkedHashMap<>();
		for (ActionInput input : definition.getInputs()) {
			names.add(input.getName());
			String value = supplied.getOrDefault(input.getName(), input.getDefaultValue());
			if (value == null) {
				if (input.isRequired()) throw new IllegalArgumentException("Missing action input: " + input.getName());
				continue;
			}

			input.validate(value);
			values.put(input.getName(), value);
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
