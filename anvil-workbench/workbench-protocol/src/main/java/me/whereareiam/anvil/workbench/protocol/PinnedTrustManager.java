package me.whereareiam.anvil.workbench.protocol;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Trusts exactly one workbench certificate, the one an engine pinned when pairing, as SSH trusts a known host. A
 * manager without a pin trusts the first certificate it sees and remembers it, which only pairing does.
 */
public final class PinnedTrustManager implements X509TrustManager {
	private final @Nullable String pinned;
	private volatile @Nullable String seen;

	/**
	 * Creates a manager trusting one fingerprint, or, without one, the first certificate it sees.
	 *
	 * @param pinned fingerprint the engine pinned, or null while pairing
	 */
	public PinnedTrustManager(@Nullable String pinned) {
		this.pinned = pinned;
	}

	/**
	 * Returns the fingerprint of the certificate the workbench presented.
	 *
	 * @return fingerprint, or null before a handshake
	 */
	public @Nullable String seen() {
		return seen;
	}

	@Override
	public void checkServerTrusted(@NotNull X509Certificate[] chain, @NotNull String authType) throws CertificateException {
		if (chain.length == 0) throw new CertificateException("The workbench presented no certificate");

		String fingerprint = CertificateFingerprint.of(chain[0]);
		if (pinned != null && !pinned.equalsIgnoreCase(fingerprint))
			throw new CertificateException("The workbench presented certificate " + fingerprint + ", but " + pinned
					+ " was paired; pair it again if it was reinstalled");
		seen = fingerprint;
	}

	@Override
	public void checkClientTrusted(@NotNull X509Certificate[] chain, @NotNull String authType) throws CertificateException {
		throw new CertificateException("An engine presents no certificate");
	}

	@Override
	public @NotNull X509Certificate[] getAcceptedIssuers() {
		return new X509Certificate[0];
	}
}
