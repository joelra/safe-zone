package com.moulberry.axiom.integration;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Compile-only stub for Axiom's Box integration class.
 * The real implementation is provided by AxiomPaperPlugin at runtime.
 */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    @Nullable
    public Box tryCombine(Box other) {
        throw new UnsupportedOperationException("Axiom stub");
    }

    public static void combineAll(List<Box> boxes) {
        throw new UnsupportedOperationException("Axiom stub");
    }

    public boolean completelyOverlaps(Box other) {
        throw new UnsupportedOperationException("Axiom stub");
    }

    public boolean contains(int x, int y, int z) {
        throw new UnsupportedOperationException("Axiom stub");
    }

    @Nullable
    public static Box intersection(Box first, Box second) {
        throw new UnsupportedOperationException("Axiom stub");
    }
}
