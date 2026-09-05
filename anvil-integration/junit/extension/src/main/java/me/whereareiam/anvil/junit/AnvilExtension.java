package me.whereareiam.anvil.junit;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.runtime.AnvilContext;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.engine.AnvilEngine;
import me.whereareiam.anvil.engine.model.EngineOptions;
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
			EngineOptions options = EngineOptions.fromSystemProperties();

			AnvilEngine engine = new AnvilEngine(options);
			try {
				AnvilContext runtimeContext = engine.start(scenario);
				context.getStore(NAMESPACE).put(STATE_KEY, new State(engine::close, runtimeContext, context));
			} catch (RuntimeException | Error failure) {
				try {
					engine.close();
				} catch (RuntimeException cleanup) {
					failure.addSuppressed(cleanup);
				}
				throw failure;
			}
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
	static final class State implements AutoCloseable {
		private final Runnable closeEngine;
		private final AnvilContext context;
		private final ExtensionContext owner;

		@Override
		public void close() {
			Throwable testFailure = owner.getExecutionException().orElse(null);
			RuntimeException failure = null;
			try {
				context.close(testFailure == null);
			} catch (RuntimeException exception) {
				failure = exception;
			}
			try {
				closeEngine.run();
			} catch (RuntimeException exception) {
				if (failure == null) failure = exception;
				else if (failure != exception) failure.addSuppressed(exception);
			}
			if (failure == null) return;
			if (testFailure != null) {
				if (testFailure != failure) testFailure.addSuppressed(failure);
				return;
			}
			throw failure;
		}
	}
}
