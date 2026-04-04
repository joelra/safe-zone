package com.simpleforapanda.safezone.manager;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimIdGeneratorTest {
	@Test
	void retriesUntilAnUnusedIdIsGenerated() {
		AtomicInteger attempts = new AtomicInteger();
		ClaimIdGenerator generator = new ClaimIdGenerator(() -> switch (attempts.getAndIncrement()) {
			case 0, 1 -> "taken1";
			default -> "fresh1";
		});

		String claimId = generator.generateUniqueId(Set.of("taken1")::contains);

		assertEquals("fresh1", claimId);
		assertEquals(3, attempts.get());
	}

	@Test
	void failsAfterTooManyCollisions() {
		ClaimIdGenerator generator = new ClaimIdGenerator(() -> "taken1");

		assertThrows(IllegalStateException.class, () -> generator.generateUniqueId(Set.of("taken1")::contains));
	}
}
