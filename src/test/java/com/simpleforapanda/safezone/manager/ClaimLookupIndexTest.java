package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.data.ClaimData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimLookupIndexTest {
	@Test
	void findsClaimsByChunkIndexedBlockPosition() {
		ClaimLookupIndex index = new ClaimLookupIndex();
		ClaimData spanningClaim = claim("spanning", UUID.randomUUID(), 14, 14, 18, 18, 100L);

		index.add(spanningClaim);

		assertEquals(spanningClaim, index.getClaimAt(new BlockPos(15, 80, 15)).orElseThrow());
		assertEquals(spanningClaim, index.getClaimAt(new BlockPos(18, 80, 18)).orElseThrow());
		assertTrue(index.getClaimAt(new BlockPos(13, 80, 13)).isEmpty());
	}

	@Test
	void ownerAndTrustedIndexesStayAccurateAfterReindexing() {
		ClaimLookupIndex index = new ClaimLookupIndex();
		UUID ownerA = UUID.randomUUID();
		UUID ownerB = UUID.randomUUID();
		UUID trustedPlayer = UUID.randomUUID();
		ClaimData first = claim("first", ownerA, 0, 0, 4, 4, 100L);
		ClaimData second = claim("second", ownerB, 32, 0, 36, 4, 200L);
		ClaimData third = claim("third", ownerA, 64, 0, 68, 4, 300L);
		second.addTrustedPlayer(trustedPlayer, "Builder");
		third.addTrustedPlayer(trustedPlayer, "Builder");

		index.rebuild(List.of(third, second, first));

		assertIterableEquals(List.of(first, third), index.getClaimsForOwner(ownerA));
		assertIterableEquals(List.of(second, third), index.getClaimsTrustedFor(trustedPlayer));

		index.remove(first);
		first.ownerUuid = ownerB.toString();
		index.add(first);
		index.remove(third);
		third.removeTrustedPlayer(trustedPlayer);
		index.add(third);

		assertIterableEquals(List.of(third), index.getClaimsForOwner(ownerA));
		assertIterableEquals(List.of(first, second), index.getClaimsForOwner(ownerB));
		assertIterableEquals(List.of(second), index.getClaimsTrustedFor(trustedPlayer));
	}

	@Test
	void nearbyClaimLookupFiltersToBufferedChunkArea() {
		ClaimLookupIndex index = new ClaimLookupIndex();
		ClaimData nearby = claim("nearby", UUID.randomUUID(), 14, 0, 18, 4, 100L);
		ClaimData farAway = claim("far", UUID.randomUUID(), 200, 0, 204, 4, 200L);
		ClaimData ignored = claim("ignored", UUID.randomUUID(), 0, 0, 4, 4, 300L);

		index.rebuild(List.of(nearby, farAway, ignored));

		List<ClaimData> nearbyClaims = index.getNearbyClaims(new BlockPos(0, 64, 0), new BlockPos(4, 64, 4), 10, "ignored");

		assertTrue(nearbyClaims.contains(nearby));
		assertFalse(nearbyClaims.contains(farAway));
		assertFalse(nearbyClaims.contains(ignored));
		assertEquals(1, nearbyClaims.size());
	}

	private static ClaimData claim(String claimId, UUID ownerId, int minX, int minZ, int maxX, int maxZ, long createdAt) {
		return new ClaimData(claimId, ownerId.toString(), "Owner", new BlockPos(minX, 64, minZ), new BlockPos(maxX, 64, maxZ), createdAt);
	}
}
