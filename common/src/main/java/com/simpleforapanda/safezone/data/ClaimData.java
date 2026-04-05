package com.simpleforapanda.safezone.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

public ClaimData(String claimId, String ownerUuid, String ownerName, ClaimCoordinates firstCorner, ClaimCoordinates secondCorner, long createdAt) {
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
		this.ownerUuid = normalizePlayerId(this.ownerUuid);
		if (this.trusted == null) {
			this.trusted = new ArrayList<>();
		} else if (this.trustedLookup == null || this.trusted.stream().anyMatch(ClaimData::requiresNormalization)) {
			LinkedHashSet<String> normalizedTrusted = new LinkedHashSet<>();
			for (String trustedPlayer : this.trusted) {
				normalizedTrusted.add(normalizePlayerId(trustedPlayer));
			}
			this.trusted = new ArrayList<>(normalizedTrusted);
		}
		if (this.trustedNames == null) {
			this.trustedNames = new LinkedHashMap<>();
		} else if (this.trustedNames.keySet().stream().anyMatch(ClaimData::requiresNormalization)) {
			LinkedHashMap<String, String> normalizedTrustedNames = new LinkedHashMap<>();
			for (Map.Entry<String, String> entry : this.trustedNames.entrySet()) {
				normalizedTrustedNames.put(normalizePlayerId(entry.getKey()), entry.getValue());
			}
			this.trustedNames = normalizedTrustedNames;
		}
		if (this.trustedLookup == null) {
			this.trustedLookup = new HashSet<>(this.trusted);
		}
	}

public boolean contains(ClaimCoordinates pos) {
return pos.x() >= getMinX()
&& pos.x() <= getMaxX()
&& pos.z() >= getMinZ()
&& pos.z() <= getMaxZ();
}

public boolean isCorner(ClaimCoordinates pos) {
boolean matchesX = pos.x() == getMinX() || pos.x() == getMaxX();
boolean matchesZ = pos.z() == getMinZ() || pos.z() == getMaxZ();
return matchesX && matchesZ;
}

public ClaimCoordinates getOppositeCorner(ClaimCoordinates pos) {
if (!isCorner(pos)) {
throw new IllegalArgumentException("Position is not a claim corner");
}

int oppositeX = pos.x() == getMinX() ? getMaxX() : getMinX();
int oppositeZ = pos.z() == getMinZ() ? getMaxZ() : getMinZ();
return new ClaimCoordinates(oppositeX, pos.y(), oppositeZ);
}

	public boolean owns(UUID playerId) {
		return normalizePlayerId(this.ownerUuid).equals(playerId.toString());
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
		return this.trustedNames.get(normalizePlayerId(playerUuid));
	}

	public static String normalizePlayerId(String playerId) {
		String resolved = Objects.requireNonNull(playerId, "playerId").trim();
		if (resolved.isEmpty()) {
			return resolved;
		}
		try {
			return UUID.fromString(resolved).toString();
		} catch (IllegalArgumentException ignored) {
			return resolved;
		}
	}

	private static boolean requiresNormalization(String playerId) {
		if (playerId == null) {
			return true;
		}
		return !normalizePlayerId(playerId).equals(playerId);
	}

public void touch(long timestamp) {
this.lastActiveAt = timestamp;
}

public void setCorners(ClaimCoordinates firstCorner, ClaimCoordinates secondCorner) {
this.x1 = firstCorner.x();
this.y1 = firstCorner.y();
this.z1 = firstCorner.z();
this.x2 = secondCorner.x();
this.y2 = secondCorner.y();
this.z2 = secondCorner.z();
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

public ClaimCoordinates getCenter() {
return new ClaimCoordinates((this.x1 + this.x2) / 2, (this.y1 + this.y2) / 2, (this.z1 + this.z2) / 2);
}

public boolean intersectsOrIsWithinDistance(ClaimData other, int distance) {
return rangesOverlap(getMinX() - distance, getMaxX() + distance, other.getMinX(), other.getMaxX())
&& rangesOverlap(getMinZ() - distance, getMaxZ() + distance, other.getMinZ(), other.getMaxZ());
}

private static boolean rangesOverlap(int minA, int maxA, int minB, int maxB) {
return maxA >= minB && maxB >= minA;
}
}
