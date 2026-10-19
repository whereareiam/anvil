package me.whereareiam.anvil.launcher.adapter.service;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.service.ServiceAddresses;
import me.whereareiam.anvil.api.model.service.ServiceContainer;
import me.whereareiam.anvil.api.service.RunningService;
import me.whereareiam.anvil.api.service.ScenarioServices;
import me.whereareiam.anvil.environment.service.api.ServiceInstance;
import me.whereareiam.anvil.environment.service.api.ServiceProvider;
import me.whereareiam.anvil.environment.service.api.ServiceSession;
import me.whereareiam.anvil.environment.service.api.model.ServiceContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The services of one scenario: started when its execution environment opens, handed to its processes as
 * addresses, to the test as {@link ScenarioServices}, and removed before the environment closes.
 */
public final class ScenarioServiceSet implements ScenarioServices {
	private final @Nullable ServiceProvider provider;
	private final @NotNull List<ServiceContainer> declarations;
	private final @NotNull ServiceContext context;
	private final Map<String, ServiceInstance> running = new LinkedHashMap<>();
	private @Nullable ServiceSession session;
	private ServiceAddresses processAddresses = ServiceAddresses.NONE;

	/**
	 * @param provider     the installed service provider, or null when none is installed
	 * @param declarations the scenario's services
	 * @param context      where the services publish their ports; its network is filled in on {@link #open(String)}
	 */
	public ScenarioServiceSet(
			@Nullable ServiceProvider provider,
			@NotNull List<ServiceContainer> declarations,
			@NotNull ServiceContext context
	) {
		this.provider = provider;
		this.declarations = List.copyOf(declarations);
		this.context = context;
	}

	/**
	 * Starts every service, joining them to the container network of the processes when there is one.
	 *
	 * @param containerNetwork the processes' container network, or null when they run on the host
	 * @throws ProvisioningException when the scenario has services but no provider is installed, or one does not
	 *                               start
	 */
	public synchronized void open(@Nullable String containerNetwork) {
		if (declarations.isEmpty()) return;
		if (provider == null)
			throw new ProvisioningException("The scenario declares services, but no Anvil service provider is installed");

		ServiceSession opened = provider.open(context.toBuilder().network(containerNetwork).build());
		session = opened;
		Map<String, Map<Integer, InetSocketAddress>> addresses = new LinkedHashMap<>();
		for (ServiceContainer declaration : declarations) {
			ServiceInstance service = opened.start(declaration);
			running.put(declaration.getName(), service);
			addresses.put(declaration.getName(), processAddresses(service, declaration));
		}
		processAddresses = ServiceAddresses.of(addresses);
	}

	/**
	 * Returns where the scenario's processes reach its services: inside their container network when they run in
	 * one, else at the published host ports.
	 *
	 * @return the processes' service addresses
	 */
	public synchronized @NotNull ServiceAddresses processAddresses() {
		return processAddresses;
	}

	@Override
	public synchronized @NotNull Collection<RunningService> all() {
		return List.copyOf(running.values());
	}

	@Override
	public synchronized @NotNull RunningService get(@NotNull String name) {
		RunningService service = running.get(name);
		if (service == null) throw new IllegalArgumentException("The scenario has no running service '" + name + "'");

		return service;
	}

	/**
	 * Removes the services, including those of a partial start.
	 */
	public synchronized void close() {
		running.clear();
		processAddresses = ServiceAddresses.NONE;
		if (session == null) return;

		try (ServiceSession closing = session) {
			session = null;
		}
	}

	private static Map<Integer, InetSocketAddress> processAddresses(ServiceInstance service, ServiceContainer declaration) {
		Map<Integer, InetSocketAddress> ports = new LinkedHashMap<>();
		for (int port : declaration.getPorts()) {
			InetSocketAddress inside = service.networkAddress(port);
			ports.put(port, inside == null ? service.address(port) : inside);
		}

		return ports;
	}
}
