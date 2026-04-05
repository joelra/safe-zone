package com.moulberry.axiom.integration;

import java.util.List;

/**
 * Compile-only stub for Axiom's SectionPermissionChecker interface.
 * The real implementation is provided by AxiomPaperPlugin at runtime.
 */
public interface SectionPermissionChecker {

    boolean allAllowed();
    boolean noneAllowed();
    boolean allowed(int x, int y, int z);
    Box bounds();

    static SectionPermissionChecker combine(SectionPermissionChecker first, SectionPermissionChecker second) {
        throw new UnsupportedOperationException("Axiom stub");
    }

    static SectionPermissionChecker fromAllowedBoxes(List<Box> allowed) {
        throw new UnsupportedOperationException("Axiom stub");
    }

    static SectionPermissionChecker fromBoxWithBooleans(List<BoxWithBoolean> boxes, boolean defaultValue) {
        throw new UnsupportedOperationException("Axiom stub");
    }

    Box FULL_BOUNDS = new Box(0, 0, 0, 15, 15, 15);

    SectionPermissionChecker ALL_ALLOWED = new SectionPermissionChecker() {
        @Override public boolean allAllowed() { return true; }
        @Override public boolean noneAllowed() { return false; }
        @Override public boolean allowed(int x, int y, int z) { return true; }
        @Override public Box bounds() { return FULL_BOUNDS; }
    };

    Box EMPTY_BOUNDS = new Box(0, 0, 0, 0, 0, 0);

    SectionPermissionChecker NONE_ALLOWED = new SectionPermissionChecker() {
        @Override public boolean allAllowed() { return false; }
        @Override public boolean noneAllowed() { return true; }
        @Override public boolean allowed(int x, int y, int z) { return false; }
        @Override public Box bounds() { return EMPTY_BOUNDS; }
    };
}
