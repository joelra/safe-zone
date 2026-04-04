package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.data.ClaimData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimValidatorTest {
	private final ClaimValidator validator = new ClaimValidator();

	@Test
	void rejectsClaimsOutsideTheOverworld() {
		var result = this.validator.validate(false, UUID.randomUUID(),
			new BlockPos(0, 64, 0), new BlockPos(4, 64, 4), List.of(), 0, 3, 64, 64, 0, null, true);

		assertEquals(ClaimManager.ClaimValidationFailure.DIMENSION_NOT_ALLOWED, result.failure());
	}

	@Test
	void enforcesOwnerClaimLimitForNewClaims() {
		var result = this.validator.validate(true, UUID.randomUUID(),
			new BlockPos(0, 64, 0), new BlockPos(4, 64, 4), List.of(), 3, 3, 64, 64, 0, null, true);

		assertEquals(ClaimManager.ClaimValidationFailure.CLAIM_LIMIT_REACHED, result.failure());
	}

	@Test
	void respectsConfiguredMinimumDistanceBetweenClaims() {
		ClaimData existingClaim = new ClaimData("existing", UUID.randomUUID().toString(), "Owner",
			new BlockPos(0, 64, 0), new BlockPos(4, 64, 4), 100L);

		var result = this.validator.validate(true, UUID.randomUUID(),
			new BlockPos(10, 64, 0), new BlockPos(14, 64, 4), List.of(existingClaim), 0, 3, 64, 64, 6, null, true);

		assertEquals(ClaimManager.ClaimValidationFailure.TOO_CLOSE_TO_EXISTING_CLAIM, result.failure());
		assertEquals(existingClaim, result.conflictingClaim());
	}

	@Test
	void ignoresCurrentClaimWhenResizing() {
		UUID ownerId = UUID.randomUUID();
		ClaimData existingClaim = new ClaimData("existing", ownerId.toString(), "Owner",
			new BlockPos(0, 64, 0), new BlockPos(4, 64, 4), 100L);

		var result = this.validator.validate(true, ownerId,
			new BlockPos(0, 64, 0), new BlockPos(8, 64, 8), List.of(existingClaim), 1, 3, 64, 64, 10, "existing", false);

		assertTrue(result.isAllowed());
	}

	@Test
	void enforcesConfiguredClaimSizeLimits() {
		var result = this.validator.validate(true, UUID.randomUUID(),
			new BlockPos(0, 64, 0), new BlockPos(8, 64, 4), List.of(), 0, 3, 8, 64, 0, null, true);

		assertEquals(ClaimManager.ClaimValidationFailure.CLAIM_TOO_LARGE, result.failure());
	}
}
