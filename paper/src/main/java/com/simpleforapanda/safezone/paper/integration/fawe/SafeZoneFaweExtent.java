package com.simpleforapanda.safezone.paper.integration.fawe;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * WorldEdit extent that restricts all block changes to claim areas the
 * acting player owns or is trusted in.
 *
 * <p>This extent is injected into every {@link com.sk89q.worldedit.EditSession}
 * created for a player-backed actor when FAWE or WorldEdit is installed. The
 * restrictions mirror those applied to Axiom edits:
 * <ul>
 *   <li>Admins ({@code safezone.command.admin}) bypass all restrictions.</li>
 *   <li>Other players may only place or break blocks inside accessible claims.</li>
 * </ul>
 *
 * <p>Accessible claims are fetched once at construction time and stored as
 * flat int arrays to avoid per-block allocations during large WorldEdit
 * operations. Any claim changes that occur during the edit are not reflected.
 */
class SafeZoneFaweExtent extends AbstractDelegateExtent {

    private static final String ADMIN_PERMISSION = "safezone.command.admin";
    private static final int[] EMPTY_BOUNDS = new int[0];

    /**
     * {@code true} when all block changes should pass through without checks.
     * Set only when the player has the admin bypass permission.
     */
    final boolean unrestricted;

    /**
     * Flat array of accessible claim bounds: [minX, maxX, minZ, maxZ, ...].
     * Only populated when {@link #unrestricted} is {@code false}.
     */
    final int[] claimBounds;

    SafeZoneFaweExtent(Extent delegate, Player player, World editedWorld, PaperClaimStore claimStore) {
        super(delegate);
        if (player.hasPermission(ADMIN_PERMISSION)) {
            this.unrestricted = true;
            this.claimBounds = EMPTY_BOUNDS;
        } else if (!claimStore.isClaimWorld(editedWorld)) {
            // Not the claim world: deny all edits (no claims exist here, so no
            // areas are accessible). This matches the Axiom integration behaviour.
            this.unrestricted = false;
            this.claimBounds = EMPTY_BOUNDS;
        } else {
            this.unrestricted = false;
            List<ClaimData> accessible = new ArrayList<>(claimStore.getClaimsForOwner(player.getUniqueId()));
            accessible.addAll(claimStore.getClaimsTrustedFor(player.getUniqueId()));
            int[] bounds = new int[accessible.size() * 4];
            int i = 0;
            for (ClaimData claim : accessible) {
                bounds[i++] = claim.getMinX();
                bounds[i++] = claim.getMaxX();
                bounds[i++] = claim.getMinZ();
                bounds[i++] = claim.getMaxZ();
            }
            this.claimBounds = bounds;
        }
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 location, T block) throws WorldEditException {
        if (this.unrestricted || isAllowed(location.x(), location.z())) {
            return super.setBlock(location, block);
        }
        return false;
    }

    boolean isAllowed(int x, int z) {
        for (int i = 0; i < this.claimBounds.length; i += 4) {
            if (x >= this.claimBounds[i] && x <= this.claimBounds[i + 1]
                    && z >= this.claimBounds[i + 2] && z <= this.claimBounds[i + 3]) {
                return true;
            }
        }
        return false;
    }
}
