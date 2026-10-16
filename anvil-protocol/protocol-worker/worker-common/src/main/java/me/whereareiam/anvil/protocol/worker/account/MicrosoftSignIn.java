package me.whereareiam.anvil.protocol.worker.account;

import me.whereareiam.anvil.protocol.api.worker.model.MicrosoftToken;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.msa.data.MsaConstants;
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig;
import net.raphimc.minecraftauth.msa.model.MsaToken;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * The Microsoft device-code sign-in of a stored account, shared by every library: it prints the sign-in page as
 * {@code Open <url>}, waits for the user, and returns the Microsoft token that each library later derives its own
 * session from.
 *
 * <p>Every account signs in through one Microsoft application, the Minecraft Launcher's Xbox title
 * ({@value MsaConstants#JAVA_TITLE_ID}, scope {@value MsaConstants#SCOPE_TITLE_AUTH}). A refresh token only works
 * with the application that issued it; this is the application the accounts stored by earlier Anvil versions were
 * signed in through, so they keep working without a new sign-in.</p>
 */
final class MicrosoftSignIn {
	private static final MsaApplicationConfig APPLICATION = new MsaApplicationConfig(MsaConstants.JAVA_TITLE_ID, MsaConstants.SCOPE_TITLE_AUTH);

	/**
	 * Signs an account in through the device-code flow.
	 *
	 * @param accountId account ID the token will be stored under, named in failures
	 * @param output receives {@code Open <url>} with the sign-in page; never tokens
	 * @return the account's Microsoft token
	 * @throws IllegalStateException when the sign-in fails, times out or is interrupted
	 */
	@NotNull MicrosoftToken signIn(@NotNull String accountId, @NotNull Consumer<String> output) {
		try {
			MsaToken token = new DeviceCodeMsaAuthService(
					MinecraftAuth.createHttpClient(),
					APPLICATION,
					code -> output.accept("Open " + code.getDirectVerificationUri())
			).acquireToken();
			return MicrosoftToken.builder()
					.clientId(APPLICATION.getClientId())
					.scope(APPLICATION.getScope())
					.accessToken(token.getAccessToken())
					.refreshToken(token.getRefreshToken())
					.expiresAt(Instant.ofEpochMilli(token.getExpireTimeMs()))
					.build();
		} catch (IOException exception) {
			throw new IllegalStateException("Microsoft sign-in failed for account '" + accountId + "'", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Microsoft sign-in was interrupted for account '" + accountId + "'", exception);
		} catch (TimeoutException exception) {
			throw new IllegalStateException("Microsoft sign-in timed out for account '" + accountId + "'", exception);
		}
	}
}
