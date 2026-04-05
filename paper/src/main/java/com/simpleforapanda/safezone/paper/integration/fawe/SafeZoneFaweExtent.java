package com.simpleforapanda.safezone.paper.integration.fawe;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.PermissionResult;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

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
 */
final class SafeZoneFaweExtent extends AbstractDelegateExtent {

    private static final String ADMIN_PERMISSION = "safezone.command.admin";

    private final UUID playerId;
    private final boolean adminBypass;
    private final PaperClaimStore claimStore;
    private final World editedWorld;

    SafeZoneFaweExtent(Extent delegate, Player player, World editedWorld, PaperClaimStore claimStore) {
        super(delegate);
        this.playerId = player.getUniqueId();
        this.adminBypass = player.hasPermission(ADMIN_PERMISSION);
        this.claimStore = claimStore;
        this.editedWorld = editedWorld;
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 location, T block) throws WorldEditException {
        if (this.adminBypass || isAllowed(location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
            return super.setBlock(location, block);
        }
        return false;
    }

    private boolean isAllowed(int x, int y, int z) {
        Location loc = new Location(this.editedWorld, x, y, z);
        Optional<ClaimData> claim = this.claimStore.getClaimAt(loc);
        if (claim.isEmpty()) {
            return false;
        }
        PermissionResult result = this.claimStore.getPermission(claim.get(), this.playerId, false);
        return result != PermissionResult.DENIED;
    }
}
