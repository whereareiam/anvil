package me.whereareiam.anvil.integration.junit;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.launcher.config.EngineProperties;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit lifecycle and parameter resolver used by {@link AnvilTest} and {@link AnvilEnvironment} annotations.
 */
public final class AnvilExtension implements BeforeEachCallback, ParameterResolver {
	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(AnvilExtension.class);
	private static final String STATE_KEY = "state";

	@Override
	public void beforeEach(@NotNull ExtensionContext context) {
		AnvilScenario scenario = ScenarioSelection.scenario(context.getRequiredTestMethod(), context.getRequiredTestClass());
		AccountRequirement accounts = AccountRequirement.of(context.getRequiredTestMethod(), context.getRequiredTestClass());
		EngineOptions options = EngineProperties.fromSystemProperties();
		new ScenarioInvocation(AnvilLauncher.create(options), scenario, () -> context.getExecutionException().isEmpty(), accounts)
				.register(invocation -> context.getStore(NAMESPACE).put(STATE_KEY, invocation));
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, @NotNull ExtensionContext context) {
		Class<?> type = parameterContext.getParameter().getType();
		return type.equals(ScenarioContext.class) || type.equals(AccountPool.class);
	}

	@Override
	public Object resolveParameter(@NotNull ParameterContext parameterContext, ExtensionContext context) {
		ScenarioInvocation state = context.getStore(NAMESPACE).get(STATE_KEY, ScenarioInvocation.class);
		if (state == null) {
			throw new ExtensionConfigurationException(parameterContext.getParameter().getType().getSimpleName()
					+ " requested outside an Anvil test lifecycle");
		}

		if (!parameterContext.getParameter().getType().equals(AccountPool.class)) return state.getContext();
		if (state.getAccounts() == null)
			throw new ExtensionConfigurationException("An AccountPool parameter requires @AnvilAccounts on the test or its class");

		return state.getAccounts();
	}
}
