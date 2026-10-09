package me.whereareiam.anvil.api.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportPolicyTest {
	@Test
	void lenientRunsUntestedComponentsAndStrictRefusesThem() {
		assertTrue(SupportPolicy.LENIENT.permits(SupportLevel.UNTESTED));
		assertFalse(SupportPolicy.STRICT.permits(SupportLevel.UNTESTED));

		for (SupportPolicy policy : SupportPolicy.values()) {
			assertTrue(policy.permits(SupportLevel.VERIFIED));
			assertTrue(policy.permits(SupportLevel.COMPATIBLE));
			assertFalse(policy.permits(SupportLevel.UNSUPPORTED));
		}
	}

	@Test
	void combinedAssessmentsKeepTheWeakestLevel() {
		assertEquals(SupportLevel.UNTESTED, SupportLevel.VERIFIED.weakest(SupportLevel.UNTESTED));
		assertEquals(SupportLevel.COMPATIBLE, SupportLevel.COMPATIBLE.weakest(SupportLevel.VERIFIED));
		assertEquals(SupportLevel.UNSUPPORTED, SupportLevel.UNSUPPORTED.weakest(SupportLevel.UNTESTED));
	}
}
