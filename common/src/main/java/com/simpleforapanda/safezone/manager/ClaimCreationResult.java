package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.data.ClaimData;

import java.util.Objects;

public record ClaimCreationResult(ClaimData claim, ClaimValidationFailure failure, ClaimData conflictingClaim) {
	public static ClaimCreationResult created(ClaimData claim) {
		return new ClaimCreationResult(Objects.requireNonNull(claim, "claim"), null, null);
	}

	public static ClaimCreationResult denied(ClaimValidationFailure failure, ClaimData conflictingClaim) {
		return new ClaimCreationResult(null, Objects.requireNonNull(failure, "failure"), conflictingClaim);
	}

	public boolean created() {
		return this.claim != null;
	}
}
