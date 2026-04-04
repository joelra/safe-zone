package com.simpleforapanda.safezone.data;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ClaimData {
	public String claimId;
	public String ownerUuid;
	public String ownerName;
	public int x1;
	public int y1;
	public int z1;
	public int x2;
	public int y2;
	public int z2;
	public List<String> trusted;
	public Map<String, String> trustedNames;
	public long createdAt;
	public long lastActiveAt;
	private transient Set<String> trustedLookup;

	public ClaimData() {
		this.trusted = new ArrayList<>();
		this.trustedNames = new LinkedHashMap<>();
	}

	public ClaimData(String claimId, String ownerUuid, String ownerName, BlockPos firstCorner, BlockPos secondCorner, long createdAt) {
		this.claimId = Objects.requireNonNull(claimId, "claimId");
		this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
		this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
		setCorners(firstCorner, secondCorner);
		this.trusted = new ArrayList<>();
		this.trustedNames = new LinkedHashMap<>();
		this.createdAt = createdAt;
		this.lastActiveAt = createdAt;
	}

	public void ensureDefaults() {
		if (this.trusted == null) {
			this.trusted = new ArrayList<>();
		} else if (this.trustedLookup == null) {
			this.trusted = new ArrayList<>(new LinkedHashSet<>(this.trusted));
		}
		if (this.trustedNames == null) {
			this.trustedNames = new LinkedHashMap<>();
		}
		if (this.trustedLookup == null) {
			this.trustedLookup = new HashSet<>(this.trusted);
		}
	}

	public boolean contains(BlockPos pos) {
		return pos.getX() >= getMinX()
			&& pos.getX() <= getMaxX()
			&& pos.getZ() >= getMinZ()
			&& pos.getZ() <= getMaxZ();
	}

	public boolean isCorner(BlockPos pos) {
		boolean matchesX = pos.getX() == getMinX() || pos.getX() == getMaxX();
		boolean matchesZ = pos.getZ() == getMinZ() || pos.getZ() == getMaxZ();
		return matchesX && matchesZ;
	}

	public BlockPos getOppositeCorner(BlockPos pos) {
		if (!isCorner(pos)) {
			throw new IllegalArgumentException("Position is not a claim corner");
		}

		int oppositeX = pos.getX() == getMinX() ? getMaxX() : getMinX();
		int oppositeZ = pos.getZ() == getMinZ() ? getMaxZ() : getMinZ();
		return new BlockPos(oppositeX, pos.getY(), oppositeZ);
	}

	public boolean owns(UUID playerId) {
		return this.ownerUuid.equals(playerId.toString());
	}

	public boolean isTrusted(UUID playerId) {
		ensureDefaults();
		return this.trustedLookup.contains(playerId.toString());
	}

	public boolean addTrustedPlayer(UUID playerId, String playerName) {
		ensureDefaults();
		String trustedPlayer = playerId.toString();
		if (this.trustedLookup.contains(trustedPlayer)) {
			if (playerName != null && !playerName.isBlank()) {
				this.trustedNames.put(trustedPlayer, playerName);
			}
			return false;
		}

		this.trusted.add(trustedPlayer);
		this.trustedLookup.add(trustedPlayer);
		if (playerName != null && !playerName.isBlank()) {
			this.trustedNames.put(trustedPlayer, playerName);
		}
		return true;
	}

	public boolean removeTrustedPlayer(UUID playerId) {
		ensureDefaults();
		String trustedPlayer = playerId.toString();
		this.trustedNames.remove(trustedPlayer);
		boolean removed = this.trustedLookup.remove(trustedPlayer);
		if (removed) {
			this.trusted.remove(trustedPlayer);
		}
		return removed;
	}

	public String getTrustedName(String playerUuid) {
		ensureDefaults();
		return this.trustedNames.get(playerUuid);
	}

	public void touch(long timestamp) {
		this.lastActiveAt = timestamp;
	}

	public void setCorners(BlockPos firstCorner, BlockPos secondCorner) {
		this.x1 = firstCorner.getX();
		this.y1 = firstCorner.getY();
		this.z1 = firstCorner.getZ();
		this.x2 = secondCorner.getX();
		this.y2 = secondCorner.getY();
		this.z2 = secondCorner.getZ();
	}

	public int getMinX() {
		return Math.min(this.x1, this.x2);
	}

	public int getMaxX() {
		return Math.max(this.x1, this.x2);
	}

	public int getMinZ() {
		return Math.min(this.z1, this.z2);
	}

	public int getMaxZ() {
		return Math.max(this.z1, this.z2);
	}

	public int getWidth() {
		return Math.abs(this.x2 - this.x1) + 1;
	}

	public int getDepth() {
		return Math.abs(this.z2 - this.z1) + 1;
	}

	public BlockPos getCenter() {
		return new BlockPos((this.x1 + this.x2) / 2, (this.y1 + this.y2) / 2, (this.z1 + this.z2) / 2);
	}

	public boolean intersectsOrIsWithinDistance(ClaimData other, int distance) {
		return rangesOverlap(getMinX() - distance, getMaxX() + distance, other.getMinX(), other.getMaxX())
			&& rangesOverlap(getMinZ() - distance, getMaxZ() + distance, other.getMinZ(), other.getMaxZ());
	}

	private static boolean rangesOverlap(int minA, int maxA, int minB, int maxB) {
		return maxA >= minB && maxB >= minA;
	}
}
