package me.whereareiam.anvil.integration.junit;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.launcher.config.EngineProperties;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.lang.reflect.Method;

/**
 * JUnit lifecycle and parameter resolver used by {@link AnvilTest} and {@link AnvilEnvironment} annotations.
 */
public final class AnvilExtension implements BeforeEachCallback, ParameterResolver {
	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(AnvilExtension.class);
	private static final String STATE_KEY = "state";

	@Override
	public void beforeEach(@NotNull ExtensionContext context) {
		OwnedResources resources = new OwnedResources();
		ScenarioInvocation invocation;
		try {
			Method method = context.getRequiredTestMethod();
			AnvilScenario scenario = ScenarioSelection.scenario(method, context.getRequiredTestClass(), resources);
			AccountRequirement accounts = AccountRequirement.of(method, context.getRequiredTestClass());
			ScenarioEngine engine = AnvilLauncher.create(EngineProperties.fromSystemProperties());
			invocation = new ScenarioInvocation(engine, scenario, () -> context.getExecutionException().isEmpty(),
					accounts, resources);
		} catch (RuntimeException | Error failure) {
			// A failed invocation has closed the resources already; closing again finds none left.
			try (resources) { throw failure; }
		}

		invocation.register(registered -> context.getStore(NAMESPACE).put(STATE_KEY, registered));
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, @NotNull ExtensionContext context) {
		Class<?> type = parameterContext.getParameter().getType();
		if (type.equals(ScenarioContext.class) || type.equals(AccountPool.class)) return true;

		ScenarioInvocation state = context.getStore(NAMESPACE).get(STATE_KEY, ScenarioInvocation.class);
		return state != null && state.resource(type) != null;
	}

	@Override
	public Object resolveParameter(@NotNull ParameterContext parameterContext, ExtensionContext context) {
		ScenarioInvocation state = context.getStore(NAMESPACE).get(STATE_KEY, ScenarioInvocation.class);
		if (state == null) {
			throw new ExtensionConfigurationException(parameterContext.getParameter().getType().getSimpleName()
					+ " requested outside an Anvil test lifecycle");
		}

		Class<?> type = parameterContext.getParameter().getType();
		if (type.equals(ScenarioContext.class)) return state.getContext();
		if (!type.equals(AccountPool.class)) return state.resource(type);
		if (state.getAccounts() == null)
			throw new ExtensionConfigurationException("An AccountPool parameter requires @AnvilAccounts on the test or its class");

		return state.getAccounts();
	}
}
