package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.data.ClaimCoordinates;
import com.simpleforapanda.safezone.data.ClaimData;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public final class ClaimValidator {
	public ClaimValidationResult validate(
		boolean overworld,
		UUID ownerId,
		ClaimCoordinates firstCorner,
		ClaimCoordinates secondCorner,
		Collection<ClaimData> existingClaims,
		int ownerClaimCount,
		int ownerClaimLimit,
		int maxClaimWidth,
		int maxClaimDepth,
		int minClaimDistance,
		String ignoredClaimId,
		boolean enforceClaimLimit
	) {
		Objects.requireNonNull(ownerId, "ownerId");
		Objects.requireNonNull(firstCorner, "firstCorner");
		Objects.requireNonNull(secondCorner, "secondCorner");
		Objects.requireNonNull(existingClaims, "existingClaims");

		if (!overworld) {
			return ClaimValidationResult.denied(ClaimValidationFailure.DIMENSION_NOT_ALLOWED, null);
		}
		if (Math.abs(secondCorner.x() - firstCorner.x()) + 1 > maxClaimWidth
			|| Math.abs(secondCorner.z() - firstCorner.z()) + 1 > maxClaimDepth) {
			return ClaimValidationResult.denied(ClaimValidationFailure.CLAIM_TOO_LARGE, null);
		}
		if (enforceClaimLimit && ownerClaimCount >= ownerClaimLimit) {
			return ClaimValidationResult.denied(ClaimValidationFailure.CLAIM_LIMIT_REACHED, null);
		}

		ClaimData candidate = new ClaimData("preview", ownerId.toString(), "", firstCorner, secondCorner, 0L);
		for (ClaimData existingClaim : existingClaims) {
			if (ignoredClaimId != null && ignoredClaimId.equals(existingClaim.claimId)) {
				continue;
			}
			if (candidate.intersectsOrIsWithinDistance(existingClaim, minClaimDistance)) {
				return ClaimValidationResult.denied(ClaimValidationFailure.TOO_CLOSE_TO_EXISTING_CLAIM, existingClaim);
			}
		}

		return ClaimValidationResult.allowed();
	}
}
