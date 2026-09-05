package me.whereareiam.anvil.junit;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilContext;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.launcher.config.EngineProperties;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit lifecycle and parameter resolver used by {@link AnvilTest}.
 */
public final class AnvilExtension implements BeforeEachCallback, ParameterResolver {
	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(AnvilExtension.class);
	private static final String STATE_KEY = "state";

	@Override
	public void beforeEach(@NotNull ExtensionContext context) {
		AnvilTest selection = selection(context);
		try {
			AnvilScenarioDefinition definition = selection.value().getDeclaredConstructor().newInstance();
			AnvilScenario scenario = definition.define();
			EngineOptions options = EngineProperties.fromSystemProperties();

			ScenarioEngine engine = AnvilLauncher.create(options);
			AnvilContext runtimeContext = engine.start(scenario);
			context.getStore(NAMESPACE).put(STATE_KEY, new State(engine, runtimeContext));
		} catch (ReflectiveOperationException e) {
			throw new ExtensionConfigurationException("Could not instantiate scenario definition "
					+ selection.value().getName(), e);
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, @NotNull ExtensionContext context) {
		return parameterContext.getParameter().getType().equals(AnvilContext.class);
	}

	@Override
	public Object resolveParameter(@NotNull ParameterContext parameterContext, ExtensionContext context) {
		State state = context.getStore(NAMESPACE).get(STATE_KEY, State.class);
		if (state == null) {
			throw new ExtensionConfigurationException("AnvilContext requested outside an @AnvilTest lifecycle");
		}

		return state.context;
	}

	private AnvilTest selection(ExtensionContext context) {
		AnvilTest method = context.getRequiredTestMethod().getAnnotation(AnvilTest.class);
		if (method != null) return method;

		AnvilTest type = context.getRequiredTestClass().getAnnotation(AnvilTest.class);
		if (type != null) return type;

		throw new ExtensionConfigurationException("AnvilExtension requires @AnvilTest");
	}

	@RequiredArgsConstructor
	private static final class State implements AutoCloseable {
		private final ScenarioEngine engine;
		private final AnvilContext context;

		@Override
		public void close() {
			try {
				context.close();
			} finally {
				engine.close();
			}
		}
	}
}
