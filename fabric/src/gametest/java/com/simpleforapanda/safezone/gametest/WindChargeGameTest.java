package com.simpleforapanda.safezone.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

//? if >=26.2 {
import net.minecraft.world.entity.EntityTypes;
//?} else {
/*import net.minecraft.world.entity.EntityType;
*///?}

/**
 * Regression tests for issues #5 and #6: wind-charge knockback is a movement mechanic
 * and must never be suppressed by claim explosion protection — neither in unclaimed
 * wilderness (#5) nor inside a claim the player owns or is trusted in (#6).
 */
public final class WindChargeGameTest {
	private static final Vec3 PLAYER_POS = new Vec3(3.5, 2.0, 3.5);
	private static final Vec3 BLAST_POS = new Vec3(5.0, 2.0, 3.5);

	@GameTest
	public void windChargeKnocksBackPlayerInWilderness(GameTestHelper helper) {
		// Issue #5: pre-fix, knockback was suppressed everywhere OUTSIDE claims because
		// canBuild() returns OWNER for unclaimed positions.
		ServerPlayer player = SafeZoneGameTestSupport.playerAt(helper, PLAYER_POS);

		detonateWindCharge(helper);

		assertKnockedBack(player, "in the wilderness");
		SafeZoneGameTestSupport.succeed(helper);
	}

	@GameTest
	public void windChargeKnocksBackPlayerInOwnClaim(GameTestHelper helper) {
		// Issue #6: pre-fix, knockback was suppressed inside claims the player has access to.
		ServerPlayer owner = SafeZoneGameTestSupport.playerAt(helper, PLAYER_POS);
		SafeZoneGameTestSupport.claimAround(helper, owner);

		detonateWindCharge(helper);

		assertKnockedBack(owner, "inside their own claim");
		SafeZoneGameTestSupport.succeed(helper);
	}

	@GameTest
	public void windChargeSuppressedInClaimWhenConfigDisabled(GameTestHelper helper) {
		// Admin opt-out: windChargeKnockbackInClaims=false suppresses wind-charge
		// knockback for players standing inside a claim.
		ServerPlayer owner = SafeZoneGameTestSupport.playerAt(helper, PLAYER_POS);
		SafeZoneGameTestSupport.claimAround(helper, owner);

		SafeZoneGameTestSupport.withWindChargeKnockbackInClaims(false, () -> detonateWindCharge(helper));

		if (owner.getDeltaMovement().lengthSqr() > 1.0E-4) {
			throw new IllegalStateException(
				"windChargeKnockbackInClaims=false should suppress wind-charge knockback inside a claim, but velocity was "
					+ owner.getDeltaMovement());
		}
		SafeZoneGameTestSupport.succeed(helper);
	}

	@GameTest
	public void windChargeStillWorksInWildernessWhenConfigDisabled(GameTestHelper helper) {
		// The opt-out only affects claims — wilderness knockback must survive it.
		ServerPlayer player = SafeZoneGameTestSupport.playerAt(helper, PLAYER_POS);

		SafeZoneGameTestSupport.withWindChargeKnockbackInClaims(false, () -> detonateWindCharge(helper));

		assertKnockedBack(player, "in the wilderness with windChargeKnockbackInClaims=false");
		SafeZoneGameTestSupport.succeed(helper);
	}

	/**
	 * Spawns a real wind charge and detonates it as the explosion source, so the
	 * explosion is attributed to an AbstractWindCharge exactly like a thrown one.
	 */
	private static void detonateWindCharge(GameTestHelper helper) {
		WindCharge charge = helper.spawn(
			//? if >=26.2 {
			EntityTypes.WIND_CHARGE,
			//?} else {
			/*EntityType.WIND_CHARGE,
			*///?}
			BlockPos.containing(BLAST_POS));
		Vec3 blast = helper.absoluteVec(BLAST_POS);
		helper.getLevel().explode(charge, blast.x, blast.y, blast.z, 1.2F, Level.ExplosionInteraction.TRIGGER);
		charge.discard();
	}

	private static void assertKnockedBack(ServerPlayer player, String where) {
		if (player.getDeltaMovement().lengthSqr() < 1.0E-4) {
			throw new IllegalStateException("Wind-charge knockback was suppressed for a player " + where
				+ " (velocity stayed " + player.getDeltaMovement() + ") — issues #5/#6 regression");
		}
	}
}
