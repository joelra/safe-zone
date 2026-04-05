package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.data.ClaimData;

import java.util.Objects;

public record ClaimValidationResult(ClaimValidationFailure failure, ClaimData conflictingClaim) {
	public static ClaimValidationResult allowed() {
		return new ClaimValidationResult(null, null);
	}

	public static ClaimValidationResult denied(ClaimValidationFailure failure, ClaimData conflictingClaim) {
		return new ClaimValidationResult(Objects.requireNonNull(failure, "failure"), conflictingClaim);
	}

	public boolean isAllowed() {
		return this.failure == null;
	}
}
