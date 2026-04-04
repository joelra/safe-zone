package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.data.ClaimData;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

final class ClaimValidator {
	ClaimManager.ClaimValidationResult validate(
		boolean overworld,
		UUID ownerId,
		BlockPos firstCorner,
		BlockPos secondCorner,
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
			return ClaimManager.ClaimValidationResult.denied(ClaimManager.ClaimValidationFailure.DIMENSION_NOT_ALLOWED, null);
		}
		if (Math.abs(secondCorner.getX() - firstCorner.getX()) + 1 > maxClaimWidth
			|| Math.abs(secondCorner.getZ() - firstCorner.getZ()) + 1 > maxClaimDepth) {
			return ClaimManager.ClaimValidationResult.denied(ClaimManager.ClaimValidationFailure.CLAIM_TOO_LARGE, null);
		}
		if (enforceClaimLimit && ownerClaimCount >= ownerClaimLimit) {
			return ClaimManager.ClaimValidationResult.denied(ClaimManager.ClaimValidationFailure.CLAIM_LIMIT_REACHED, null);
		}

		ClaimData candidate = new ClaimData("preview", ownerId.toString(), "", firstCorner, secondCorner, 0L);
		for (ClaimData existingClaim : existingClaims) {
			if (ignoredClaimId != null && ignoredClaimId.equals(existingClaim.claimId)) {
				continue;
			}
			if (candidate.intersectsOrIsWithinDistance(existingClaim, minClaimDistance)) {
				return ClaimManager.ClaimValidationResult.denied(ClaimManager.ClaimValidationFailure.TOO_CLOSE_TO_EXISTING_CLAIM, existingClaim);
			}
		}

		return ClaimManager.ClaimValidationResult.allowed();
	}
}
