package com.simpleforapanda.safezone.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimDataTest {
	@Test
	void trustedPlayersTrackNamesAndRemoval() {
		ClaimData claim = new ClaimData("claim1", UUID.randomUUID().toString(), "Owner",
			new ClaimCoordinates(0, 64, 0), new ClaimCoordinates(4, 64, 4), 123L);
		UUID trustedPlayer = UUID.randomUUID();

		assertTrue(claim.addTrustedPlayer(trustedPlayer, "Builder"));
		assertFalse(claim.addTrustedPlayer(trustedPlayer, "Updated Builder"));
		assertTrue(claim.isTrusted(trustedPlayer));
		assertEquals("Updated Builder", claim.getTrustedName(trustedPlayer.toString()));

		assertTrue(claim.removeTrustedPlayer(trustedPlayer));
		assertFalse(claim.isTrusted(trustedPlayer));
		assertNull(claim.getTrustedName(trustedPlayer.toString()));
	}

	@Test
	void setCornersUpdatesClaimBounds() {
		ClaimData claim = new ClaimData("claim1", UUID.randomUUID().toString(), "Owner",
			new ClaimCoordinates(0, 64, 0), new ClaimCoordinates(1, 64, 1), 123L);

		claim.setCorners(new ClaimCoordinates(10, 70, 20), new ClaimCoordinates(13, 75, 24));

		assertEquals(10, claim.x1);
		assertEquals(70, claim.y1);
		assertEquals(20, claim.z1);
		assertEquals(13, claim.x2);
		assertEquals(75, claim.y2);
		assertEquals(24, claim.z2);
		assertEquals(4, claim.getWidth());
		assertEquals(5, claim.getDepth());
	}

	@Test
	void oppositeCornerRequiresActualCorner() {
		ClaimData claim = new ClaimData("claim1", UUID.randomUUID().toString(), "Owner",
			new ClaimCoordinates(3, 64, 7), new ClaimCoordinates(8, 70, 11), 123L);

		assertEquals(new ClaimCoordinates(8, 90, 11), claim.getOppositeCorner(new ClaimCoordinates(3, 90, 7)));
		assertThrows(IllegalArgumentException.class, () -> claim.getOppositeCorner(new ClaimCoordinates(4, 90, 7)));
	}

	@Test
	void intersectionCheckHonorsDistanceBuffer() {
		ClaimData origin = new ClaimData("origin", UUID.randomUUID().toString(), "Owner",
			new ClaimCoordinates(0, 64, 0), new ClaimCoordinates(4, 64, 4), 123L);
		ClaimData nearby = new ClaimData("nearby", UUID.randomUUID().toString(), "Other",
			new ClaimCoordinates(10, 64, 0), new ClaimCoordinates(14, 64, 4), 456L);

		assertFalse(origin.intersectsOrIsWithinDistance(nearby, 5));
		assertTrue(origin.intersectsOrIsWithinDistance(nearby, 6));
	}

	@Test
	void trustedLookupIsRebuiltFromLoadedData() {
		UUID trustedPlayer = UUID.randomUUID();
		ClaimData claim = new ClaimData();
		String uppercaseTrustedPlayer = trustedPlayer.toString().toUpperCase(java.util.Locale.ROOT);
		claim.ownerUuid = UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
		claim.trusted = new java.util.ArrayList<>(java.util.List.of(uppercaseTrustedPlayer, trustedPlayer.toString()));
		claim.trustedNames = new java.util.LinkedHashMap<>(java.util.Map.of(uppercaseTrustedPlayer, "Builder"));

		assertTrue(claim.owns(UUID.fromString(claim.ownerUuid)));
		assertTrue(claim.isTrusted(trustedPlayer));
		assertEquals(1, claim.trusted.size());
		assertEquals("Builder", claim.getTrustedName(trustedPlayer.toString()));
		assertTrue(claim.removeTrustedPlayer(trustedPlayer));
		assertFalse(claim.isTrusted(trustedPlayer));
	}
}
