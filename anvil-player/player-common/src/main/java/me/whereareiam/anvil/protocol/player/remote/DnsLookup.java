package me.whereareiam.anvil.protocol.player.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;

/**
 * The system's name lookups, configured as the vanilla client configures them: SRV records through the JDK's JNDI
 * DNS provider with the system's DNS servers and one retry, and addresses through {@link InetAddress#getByName}.
 */
public final class DnsLookup implements RemoteAddressResolver.Lookup {
	@Override
	public @Nullable String serviceRecord(@NotNull String name) {
		Hashtable<String, String> environment = new Hashtable<>();
		environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
		environment.put("java.naming.provider.url", "dns:");
		environment.put("com.sun.jndi.dns.timeout.retries", "1");
		DirContext context = null;
		try {
			context = new InitialDirContext(environment);
			Attribute records = context.getAttributes(name, new String[]{"SRV"}).get("srv");

			return records == null ? null : records.get().toString();
		} catch (NamingException | RuntimeException failed) {
			// Vanilla treats every failed lookup as a name without a record.
			return null;
		} finally {
			close(context);
		}
	}

	@Override
	public @NotNull InetAddress address(@NotNull String host) throws UnknownHostException {
		return InetAddress.getByName(host);
	}

	private static void close(@Nullable DirContext context) {
		if (context == null) return;

		try {
			context.close();
		} catch (NamingException ignored) {
			// Closing releases no resource a later lookup needs.
		}
	}
}
