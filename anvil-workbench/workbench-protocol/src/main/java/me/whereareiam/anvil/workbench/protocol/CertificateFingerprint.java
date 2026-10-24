package me.whereareiam.anvil.workbench.protocol;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

/**
 * The SHA-256 fingerprint an engine pins a workbench's certificate by, as it saw it when pairing.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CertificateFingerprint {
	/**
	 * Returns a certificate's fingerprint as lowercase hexadecimal.
	 *
	 * @param certificate certificate the workbench presented
	 * @return SHA-256 of its encoded form
	 */
	public static @NotNull String of(@NotNull X509Certificate certificate) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
		} catch (NoSuchAlgorithmException | CertificateEncodingException failure) {
			throw new IllegalStateException("Could not fingerprint the workbench's certificate", failure);
		}
	}
}
