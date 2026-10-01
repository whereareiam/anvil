package me.whereareiam.anvil.integration.intellij.account.authentication;

import com.intellij.openapi.Disposable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.NotNull;

/**
 * An account-enrollment operation whose owner controls cancellation and cleanup.
 */
public interface AccountEnrollment extends Disposable {
	/**
	 * Starts preparation and provider authentication on the supplied background executor.
	 *
	 * @param executor executor used for blocking preparation and child-process I/O
	 * @return completion after enrollment and cleanup, or an exceptional cancellation/failure
	 */
	@NotNull CompletableFuture<Void> start(@NotNull Executor executor);

	@Override
	void dispose();
}
