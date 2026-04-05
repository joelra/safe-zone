package com.simpleforapanda.safezone.manager;

import java.util.List;

public record ClaimExpiryRefreshResult(
	List<String> expiredClaimIds,
	int refreshedClaimCount
) {
	public ClaimExpiryRefreshResult {
		expiredClaimIds = List.copyOf(expiredClaimIds);
	}

	public static ClaimExpiryRefreshResult empty() {
		return new ClaimExpiryRefreshResult(List.of(), 0);
	}

	public boolean hasExpiredClaims() {
		return !this.expiredClaimIds.isEmpty();
	}
}
