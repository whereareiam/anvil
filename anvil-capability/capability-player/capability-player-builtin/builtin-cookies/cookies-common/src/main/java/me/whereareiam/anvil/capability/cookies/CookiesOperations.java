package me.whereareiam.anvil.capability.cookies;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.cookies.exchange.AnsweredRequest;
import me.whereareiam.anvil.capability.cookies.exchange.CookieKey;
import me.whereareiam.anvil.capability.cookies.exchange.HeldCookies;
import me.whereareiam.anvil.capability.cookies.exchange.StoredCookie;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * Typed cookies operations and events shared by the host cookies capability and the worker's cookies binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CookiesOperations {
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
	public static final EventDescriptor<AnsweredRequest> REQUESTED = new EventDescriptor<>(
			"cookies.requested", AnsweredRequest.class
	);
	/**
	 * The cookies the player holds after a change: the server stored one, or a connection started or ended.
	 */
	public static final EventDescriptor<HeldCookies> HELD = new EventDescriptor<>("cookies.held", HeldCookies.class);
}
