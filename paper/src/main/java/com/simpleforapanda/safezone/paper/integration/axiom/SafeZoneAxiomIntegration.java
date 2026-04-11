package com.simpleforapanda.safezone.paper.integration.axiom;

import com.moulberry.axiom.integration.Box;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.integration.SectionPermissionChecker;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.PermissionResult;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements Axiom's {@link Integration.CustomIntegration} to restrict
 * Axiom block editing to claim areas the player owns or is trusted in.
 *
 * <p>Players with {@code safezone.command.admin} bypass all restrictions.
 * All other players may only edit inside their own accessible claims;
 * unclaimed land and other players' claims are blocked.
 */
final class SafeZoneAxiomIntegration implements Integration.CustomIntegration {

    static final String ADMIN_PERMISSION = "safezone.command.admin";

    private final PaperClaimStore claimStore;

    SafeZoneAxiomIntegration(PaperClaimStore claimStore) {
        this.claimStore = claimStore;
    }

    @Override
    public boolean canBreakBlock(Player player, Block block) {
        return hasAccess(player, block.getLocation());
    }

    @Override
    public boolean canPlaceBlock(Player player, Location loc) {
        return hasAccess(player, loc);
    }

    /**
     * Per-block access check.
     * <ul>
     *   <li>Admins: always allowed.</li>
     *   <li>Others: only allowed if the block is inside a claim they own or are trusted in.</li>
     * </ul>
     */
    private boolean hasAccess(Player player, Location loc) {
        if (player.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        Optional<ClaimData> claim = this.claimStore.getClaimAt(loc);
        if (claim.isEmpty()) {
            // No claim here — Axiom is not permitted outside claims.
            return false;
        }
        PermissionResult result = this.claimStore.getPermission(claim.get(), player.getUniqueId(), false);
        return result != PermissionResult.DENIED;
    }

    /**
     * Efficient chunk-section (16×16×16) access check used by Axiom for bulk
     * operations. For each accessible claim that overlaps the section's XZ
     * footprint the corresponding local block range (full height) is added
     * to the allowed set.
     *
     * <p>Claims are full-height columns, so Y is irrelevant when determining
     * XZ overlap; the allowed box always spans the full local Y range 0–15.
     */
    @Override
    public SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {
        if (player.hasPermission(ADMIN_PERMISSION)) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }

        // Claims only exist in the claim world (Overworld).
        if (!this.claimStore.isClaimWorld(world)) {
            return SectionPermissionChecker.NONE_ALLOWED;
        }

        UUID playerId = player.getUniqueId();
        int sMinX = cx * 16;
        int sMaxX = cx * 16 + 15;
        int sMinZ = cz * 16;
        int sMaxZ = cz * 16 + 15;

        List<ClaimData> accessible = new ArrayList<>(this.claimStore.getClaimsForOwner(playerId));
        accessible.addAll(this.claimStore.getClaimsTrustedFor(playerId));

        List<Box> allowed = new ArrayList<>();
        for (ClaimData claim : accessible) {
            int interMinX = Math.max(claim.getMinX(), sMinX);
            int interMaxX = Math.min(claim.getMaxX(), sMaxX);
            int interMinZ = Math.max(claim.getMinZ(), sMinZ);
            int interMaxZ = Math.min(claim.getMaxZ(), sMaxZ);

            if (interMinX > interMaxX || interMinZ > interMaxZ) {
                continue;
            }

            // Convert to section-local coordinates (0–15).
            int localMinX = interMinX - sMinX;
            int localMaxX = interMaxX - sMinX;
            int localMinZ = interMinZ - sMinZ;
            int localMaxZ = interMaxZ - sMinZ;

            // Claims span full height, so allow the entire Y range in the section.
            allowed.add(new Box(localMinX, 0, localMinZ, localMaxX, 15, localMaxZ));
        }

        Box.combineAll(allowed);
        return SectionPermissionChecker.fromAllowedBoxes(allowed);
    }
}
