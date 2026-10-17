package me.whereareiam.anvil.capability.cookies.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.cookies.model.CookieKey;
import me.whereareiam.anvil.capability.cookies.model.CookieRequest;
import me.whereareiam.anvil.capability.cookies.model.HeldCookies;
import me.whereareiam.anvil.capability.cookies.model.StoredCookie;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * The channel contract between the host cookies capability and its worker extensions: the capability ID both
 * register under, the operations through which the host gives and takes away cookies, and the events the worker
 * emits.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CookiesChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.cookies";
	/**
	 * Gives the player a cookie and returns the cookies it then holds.
	 */
	public static final OperationDescriptor<StoredCookie, HeldCookies> STORE = new OperationDescriptor<>(
			"cookies.store", StoredCookie.class, HeldCookies.class
	);
	/**
	 * Takes a cookie away from the player and returns the cookies it then holds.
	 */
	public static final OperationDescriptor<CookieKey, HeldCookies> REMOVE = new OperationDescriptor<>(
			"cookies.remove", CookieKey.class, HeldCookies.class
	);
	/**
	 * A cookie request of the player's current connection, with the answer the worker sent.
	 */
	public static final EventDescriptor<CookieRequest> REQUESTED = new EventDescriptor<>(
			"cookies.requested", CookieRequest.class
	);
	/**
	 * The cookies the player holds after a change: the server stored one, the test gave or took one, or a connection
	 * started.
	 */
	public static final EventDescriptor<HeldCookies> HELD = new EventDescriptor<>("cookies.held", HeldCookies.class);
}
