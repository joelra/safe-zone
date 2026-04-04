package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.data.ClaimData;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class ClaimLookupIndex {
	private static final Comparator<ClaimData> CLAIM_ORDER =
		Comparator.comparingLong((ClaimData claim) -> claim.createdAt).thenComparing(claim -> claim.claimId);

	private final Map<Long, List<ClaimData>> claimsByChunk = new HashMap<>();
	private final Map<String, List<ClaimData>> claimsByOwner = new HashMap<>();
	private final Map<String, List<ClaimData>> claimsByTrustedPlayer = new HashMap<>();

	void clear() {
		this.claimsByChunk.clear();
		this.claimsByOwner.clear();
		this.claimsByTrustedPlayer.clear();
	}

	void rebuild(Iterable<ClaimData> claims) {
		clear();
		for (ClaimData claim : claims) {
			add(claim);
		}
	}

	void add(ClaimData claim) {
		claim.ensureDefaults();
		indexClaim(claim, chunkKeys(claim.getMinX(), claim.getMaxX(), claim.getMinZ(), claim.getMaxZ()), this.claimsByChunk);
		indexClaim(claim.ownerUuid, claim, this.claimsByOwner);
		for (String trustedPlayer : claim.trusted) {
			indexClaim(trustedPlayer, claim, this.claimsByTrustedPlayer);
		}
	}

	void remove(ClaimData claim) {
		claim.ensureDefaults();
		unindexClaim(claim, chunkKeys(claim.getMinX(), claim.getMaxX(), claim.getMinZ(), claim.getMaxZ()), this.claimsByChunk);
		unindexClaim(claim.ownerUuid, claim, this.claimsByOwner);
		for (String trustedPlayer : claim.trusted) {
			unindexClaim(trustedPlayer, claim, this.claimsByTrustedPlayer);
		}
	}

	Optional<ClaimData> getClaimAt(BlockPos pos) {
		List<ClaimData> indexedClaims = this.claimsByChunk.get(chunkKey(blockToChunkCoord(pos.getX()), blockToChunkCoord(pos.getZ())));
		if (indexedClaims == null) {
			return Optional.empty();
		}

		return indexedClaims.stream().filter(claim -> claim.contains(pos)).findFirst();
	}

	List<ClaimData> getClaimsForOwner(UUID ownerId) {
		return copyClaims(this.claimsByOwner.get(ownerId.toString()));
	}

	int getClaimCountForOwner(UUID ownerId) {
		List<ClaimData> claims = this.claimsByOwner.get(ownerId.toString());
		return claims == null ? 0 : claims.size();
	}

	List<ClaimData> getClaimsTrustedFor(UUID playerId) {
		return copyClaims(this.claimsByTrustedPlayer.get(playerId.toString()));
	}

	List<ClaimData> getNearbyClaims(BlockPos firstCorner, BlockPos secondCorner, int distance, String ignoredClaimId) {
		int minX = Math.min(firstCorner.getX(), secondCorner.getX()) - distance;
		int maxX = Math.max(firstCorner.getX(), secondCorner.getX()) + distance;
		int minZ = Math.min(firstCorner.getZ(), secondCorner.getZ()) - distance;
		int maxZ = Math.max(firstCorner.getZ(), secondCorner.getZ()) + distance;

		Map<String, ClaimData> nearbyClaims = new LinkedHashMap<>();
		for (long chunkKey : chunkKeys(minX, maxX, minZ, maxZ)) {
			List<ClaimData> indexedClaims = this.claimsByChunk.get(chunkKey);
			if (indexedClaims == null) {
				continue;
			}

			for (ClaimData claim : indexedClaims) {
				if (ignoredClaimId != null && ignoredClaimId.equals(claim.claimId)) {
					continue;
				}
				nearbyClaims.putIfAbsent(claim.claimId, claim);
			}
		}

		return nearbyClaims.values().stream().sorted(CLAIM_ORDER).toList();
	}

	private static void indexClaim(String key, ClaimData claim, Map<String, List<ClaimData>> index) {
		List<ClaimData> claims = index.computeIfAbsent(key, ignored -> new ArrayList<>());
		int insertAt = 0;
		while (insertAt < claims.size() && CLAIM_ORDER.compare(claims.get(insertAt), claim) <= 0) {
			insertAt++;
		}
		claims.add(insertAt, claim);
	}

	private static void unindexClaim(String key, ClaimData claim, Map<String, List<ClaimData>> index) {
		List<ClaimData> claims = index.get(key);
		if (claims == null) {
			return;
		}

		claims.removeIf(indexedClaim -> indexedClaim.claimId.equals(claim.claimId));
		if (claims.isEmpty()) {
			index.remove(key);
		}
	}

	private static void indexClaim(ClaimData claim, List<Long> chunkKeys, Map<Long, List<ClaimData>> index) {
		for (long chunkKey : chunkKeys) {
			index.computeIfAbsent(chunkKey, ignored -> new ArrayList<>()).add(claim);
		}
	}

	private static void unindexClaim(ClaimData claim, List<Long> chunkKeys, Map<Long, List<ClaimData>> index) {
		for (long chunkKey : chunkKeys) {
			List<ClaimData> claims = index.get(chunkKey);
			if (claims == null) {
				continue;
			}

			claims.removeIf(indexedClaim -> indexedClaim.claimId.equals(claim.claimId));
			if (claims.isEmpty()) {
				index.remove(chunkKey);
			}
		}
	}

	private static List<ClaimData> copyClaims(List<ClaimData> indexedClaims) {
		return indexedClaims == null ? List.of() : List.copyOf(indexedClaims);
	}

	private static List<Long> chunkKeys(int minX, int maxX, int minZ, int maxZ) {
		int minChunkX = blockToChunkCoord(minX);
		int maxChunkX = blockToChunkCoord(maxX);
		int minChunkZ = blockToChunkCoord(minZ);
		int maxChunkZ = blockToChunkCoord(maxZ);

		List<Long> chunkKeys = new ArrayList<>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				chunkKeys.add(chunkKey(chunkX, chunkZ));
			}
		}
		return chunkKeys;
	}

	private static int blockToChunkCoord(int blockCoord) {
		return blockCoord >> 4;
	}

	private static long chunkKey(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
	}
}
