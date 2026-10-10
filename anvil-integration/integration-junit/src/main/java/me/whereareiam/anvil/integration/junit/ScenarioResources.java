package me.whereareiam.anvil.integration.junit;

import org.jetbrains.annotations.NotNull;

/**
 * Objects that belong to one scenario besides its Minecraft processes, such as a stand-in for a web service
 * the plugin calls. An {@link AnvilScenarioFactory} hands them over while it builds the scenario; the test
 * receives them as parameters of their type, and they are closed after the scenario's processes have stopped.
 *
 * <pre>{@code
 * public final class ShopScenarios implements AnvilScenarioFactory<Shop> {
 *     public AnvilScenario create(Shop shop, ScenarioResources resources) {
 *         PaymentStub payments = resources.own(PaymentStub.start());
 *         return Scenarios.shop(payments.address());
 *     }
 * }
 *
 * @Test
 * @Shop
 * void refundsACancelledOrder(ScenarioContext anvil, PaymentStub payments) {
 * }
 * }</pre>
 */
public interface ScenarioResources {
	/**
	 * Makes an object part of the scenario being built. A test method, or a before-each or after-each method,
	 * receives it through a parameter whose type the object is an instance of. An {@link AutoCloseable} object
	 * is closed when the scenario ends, after its processes have stopped and in reverse order of ownership,
	 * also when the scenario fails to start.
	 *
	 * @param resource object owned by the scenario
	 * @param <T> resource type
	 * @return the same object, for use while building the scenario
	 */
	<T> @NotNull T own(@NotNull T resource);
}
