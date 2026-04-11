package com.simpleforapanda.safezone.paper.integration.fawe;

import com.sk89q.worldedit.entity.Player;
import com.fastasyncworldedit.core.regions.FaweMask;
import com.fastasyncworldedit.core.regions.FaweMaskManager;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.AbstractRegion;
import com.sk89q.worldedit.regions.RegionOperationException;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Registers claim boundaries with FAWE's mask system so that every
 * player-backed WorldEdit edit session is automatically restricted to
 * claim areas the player owns or is trusted in.
 *
 * <p>FAWE calls {@link #getMask} once per {@link com.sk89q.worldedit.EditSession}
 * and caches the returned mask for the duration of that session. The mask's
 * {@code contains(BlockVector3)} is then checked for every block change.
 *
 * <p>Requires {@code region-restrictions: true} in {@code FastAsyncWorldEdit/config.yml}
 * (the setting that enables FAWE's mask/region framework). With that flag set,
 * FAWE will query all registered {@link FaweMaskManager} implementations — including
 * this one — before allowing any block change.
 *
 * <p>This class must only be loaded after a Bukkit-level check confirms that
 * FastAsyncWorldEdit is enabled.
 */
final class SafeZoneMaskManager extends FaweMaskManager {

    private static final Logger LOGGER = Logger.getLogger("SafeZone");
    private static final String ADMIN_PERMISSION = "safezone.command.admin";

    /**
     * Sentinel region that passes every {@code contains()} check, used to
     * give admin players unrestricted access everywhere.
     */
    private static final AbstractRegion ALLOW_ALL = new AbstractRegion(null) {
        @Override
        public boolean contains(BlockVector3 pos) {
            return true;
        }

        @Override
        public BlockVector3 getMinimumPoint() {
            return BlockVector3.at(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        }

        @Override
        public BlockVector3 getMaximumPoint() {
            return BlockVector3.at(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        @Override
        public void expand(BlockVector3... changes) throws RegionOperationException {
            // Not applicable for this sentinel region.
        }

        @Override
        public void contract(BlockVector3... changes) throws RegionOperationException {
            // Not applicable for this sentinel region.
        }

        @Override
        public Iterator<BlockVector3> iterator() {
            throw new UnsupportedOperationException();
        }
    };

    private final PaperClaimStore claimStore;

    SafeZoneMaskManager(PaperClaimStore claimStore) {
        super("SafeZone");
        this.claimStore = claimStore;
    }

    /**
     * Returns the edit mask for {@code player}.
     *
     * <ul>
     *   <li>Admin players ({@code safezone.command.admin}): unrestricted — returns a
     *       mask that allows all positions.</li>
     *   <li>Players with no accessible claims: denied everywhere — returns {@code null},
     *       which FAWE treats as "no allowed region" when
     *       {@code region-restrictions: true}.</li>
     *   <li>Everyone else: restricted to the union of owned and trusted claim areas.</li>
     * </ul>
     *
     * <p>Claim bounds are captured into a final array at call time so concurrent
     * reads during FAWE's async block processing are safe without locking.
     */
    @Override
    public FaweMask getMask(Player player, MaskType type, boolean isWhitelist) {
        org.bukkit.entity.Player bukkit = Bukkit.getPlayer(player.getUniqueId());
        if (bukkit == null) {
            return null;
        }

        if (bukkit.hasPermission(ADMIN_PERMISSION)) {
            LOGGER.fine("[SafeZone-FAWE] getMask: admin bypass for " + bukkit.getName());
            return new FaweMask(ALLOW_ALL);
        }

        List<ClaimData> accessible = new ArrayList<>(this.claimStore.getClaimsForOwner(bukkit.getUniqueId()));
        accessible.addAll(this.claimStore.getClaimsTrustedFor(bukkit.getUniqueId()));

        LOGGER.fine("[SafeZone-FAWE] getMask: " + bukkit.getName() + " has " + accessible.size() + " accessible claims");

        if (accessible.isEmpty()) {
            // No accessible claims: deny all WorldEdit operations.
            return null;
        }

        // Snapshot claim bounds into a flat int array.
        // Claims are full-height columns so only x,z matter for containment.
        int[] bounds = new int[accessible.size() * 4]; // [minX, maxX, minZ, maxZ, ...]
        int i = 0;
        for (ClaimData claim : accessible) {
            bounds[i++] = claim.getMinX();
            bounds[i++] = claim.getMaxX();
            bounds[i++] = claim.getMinZ();
            bounds[i++] = claim.getMaxZ();
        }

        return new FaweMask(new ClaimUnionRegion(bounds));
    }

    /**
     * A region whose {@link #contains} returns {@code true} for any position that
     * falls within at least one of the player's accessible claim columns.
     *
     * <p>Claims are full-height columns (the y axis is unconstrained).
     * Minimum and maximum points advertise the widest possible bounds so FAWE
     * never skips the per-block check based on a coarse bounding-box test.
     */
    private static final class ClaimUnionRegion extends AbstractRegion {

        private final int[] claimBounds; // flat: [minX, maxX, minZ, maxZ, ...]

        ClaimUnionRegion(int[] claimBounds) {
            super(null); // world not needed — only x,z coordinates are checked
            this.claimBounds = claimBounds;
        }

        @Override
        public boolean contains(BlockVector3 pos) {
            int x = pos.x();
            int z = pos.z();
            for (int i = 0; i < this.claimBounds.length; i += 4) {
                if (x >= this.claimBounds[i]     && x <= this.claimBounds[i + 1]
                 && z >= this.claimBounds[i + 2] && z <= this.claimBounds[i + 3]) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public BlockVector3 getMinimumPoint() {
            return BlockVector3.at(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        }

        @Override
        public BlockVector3 getMaximumPoint() {
            return BlockVector3.at(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        @Override
        public void expand(BlockVector3... changes) throws RegionOperationException {
            // Not applicable for a mask-only region.
        }

        @Override
        public void contract(BlockVector3... changes) throws RegionOperationException {
            // Not applicable for a mask-only region.
        }

        @Override
        public Iterator<BlockVector3> iterator() {
            throw new UnsupportedOperationException("ClaimUnionRegion does not support iteration");
        }
    }
}
