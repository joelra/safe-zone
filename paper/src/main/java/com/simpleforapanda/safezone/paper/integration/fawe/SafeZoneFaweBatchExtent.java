package com.simpleforapanda.safezone.paper.integration.fawe;

import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.fastasyncworldedit.core.queue.IChunk;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import com.sk89q.worldedit.extent.Extent;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * FAWE-specific extent that enforces claim boundaries at the chunk batch level.
 *
 * <p>FAWE 2.x with {@code fast-placement: true} writes blocks directly to world
 * chunks in batches, completely bypassing
 * {@link com.sk89q.worldedit.extent.AbstractDelegateExtent#setBlock}. This class
 * implements {@link IBatchProcessor} so FAWE calls {@link #processSet} during chunk
 * batch processing — the correct interception point for FAWE 2.x.
 *
 * <p>The parent class {@link SafeZoneFaweExtent} still overrides {@code setBlock}
 * for compatibility with plain WorldEdit (without FAWE), where block changes are
 * routed through the extent chain directly.
 *
 * <p>This class must only be loaded when FastAsyncWorldEdit is confirmed to be
 * enabled, because it references FAWE-specific types ({@code IBatchProcessor},
 * {@code IChunk}, etc.) that are absent in plain WorldEdit.
 */
final class SafeZoneFaweBatchExtent extends SafeZoneFaweExtent implements IBatchProcessor {

    private static final Logger LOGGER = Logger.getLogger("SafeZone");

    // Minecraft 1.21.x world height: Y -64 to 319.
    // Section (16-block-tall chunk slice) indices range from -4 (Y -64..-49)
    // through 19 (Y 304..319).
    private static final int MIN_SECTION = -4;
    private static final int MAX_SECTION = 19;

    // Log only once per instance so we don't spam every chunk.
    private final AtomicBoolean firstCall = new AtomicBoolean(true);

    SafeZoneFaweBatchExtent(Extent delegate, Player player, World editedWorld, PaperClaimStore claimStore) {
        super(delegate, player, editedWorld, claimStore);
    }

    /**
     * Called by FAWE during chunk batch processing.
     *
     * <p>Filters block changes so that positions in denied x,z columns are cleared
     * from the chunk set (char value 0 = "no change"), leaving the original world
     * blocks untouched. Returns {@code null} when the entire chunk falls outside
     * all accessible claims, instructing FAWE to discard all changes for that chunk.
     */
    @Override
    public IChunkSet processSet(IChunk chunk, IChunkGet get, IChunkSet set) {
        if (firstCall.getAndSet(false)) {
            LOGGER.info("[SafeZone-FAWE] processSet IS being called: chunk=" + chunk.getX() + "," + chunk.getZ()
                + " unrestricted=" + this.unrestricted
                + " claimBoundsCount=" + (this.claimBounds.length / 4));
        }
        if (this.unrestricted || set == null) {
            return set;
        }

        int chunkBlockX = chunk.getX() << 4;
        int chunkBlockZ = chunk.getZ() << 4;

        // Build a 16×16 allowed-column grid for this chunk. Claims are full-height
        // columns, so we only need to check x,z — y is irrelevant.
        boolean[] allowed = new boolean[256]; // index: lx + lz * 16
        boolean anyAllowed = false;
        boolean anyDenied = false;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                boolean ok = isAllowed(chunkBlockX + lx, chunkBlockZ + lz);
                allowed[lx + lz * 16] = ok;
                if (ok) anyAllowed = true;
                else anyDenied = true;
            }
        }

        if (!anyDenied) return set;    // all columns in this chunk are allowed
        if (!anyAllowed) return null;  // all columns denied — discard entire chunk

        // Partial chunk: clear denied columns in each occupied section.
        // Setting a block's char value to 0 means "no change at this position",
        // so the original world block remains untouched.
        for (int section = MIN_SECTION; section <= MAX_SECTION; section++) {
            if (!set.hasSection(section)) {
                continue;
            }
            char[] blocks = set.load(section);
            if (blocks == null) {
                continue;
            }
            boolean modified = false;
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        if (!allowed[lx + lz * 16]) {
                            // FAWE section block index: (y << 8) | (z << 4) | x
                            int index = (ly << 8) | (lz << 4) | lx;
                            if (blocks[index] != 0) {
                                blocks[index] = 0;
                                modified = true;
                            }
                        }
                    }
                }
            }
            if (modified) {
                set.setBlocks(section, blocks);
            }
        }

        return set;
    }

    /**
     * Not used in Safe Zone's registration path: extents are registered per
     * EditSession via {@link FaweIntegration#onEditSession}. Returning the child
     * unchanged is correct here.
     */
    @Override
    public Extent construct(Extent child) {
        return child;
    }
}
