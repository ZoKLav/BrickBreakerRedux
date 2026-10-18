package com.retro.brickbreaker;

/** Pure-Java gameplay settings so the touch geometry and difficulty are testable without Android. */
public final class GameplayRules {
    private GameplayRules() { }

    // Coordinates are the game's 240-unit logical canvas, not device pixels.
    public static boolean paddleTouch(float x, float y, float paddleY, float worldHeight) {
        return x >= 9f && x <= 231f && y >= paddleY - 26f
                && y <= Math.min(paddleY + 17f, worldHeight - 8f);
    }

    public static boolean fireTouch(float x, float y, float worldHeight) {
        float[] r = fireBounds(worldHeight);
        return x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3];
    }

    /** Zero means descending bricks are fully disabled (Easy). */
    public static float descentInterval(int difficulty) {
        return difficulty <= 0 ? 0f : difficulty == 1 ? 3.5f : 1.65f;
    }

    public static boolean descending(int difficulty, int impacts, int paddleHits) {
        return descentInterval(difficulty) > 0f && impacts >= 50 && paddleHits > 0;
    }

    /** Each eligible brick hit is an independent random trial, not a preset brick location. */
    public static float powerupChance(int difficulty) {
        return difficulty <= 0 ? .20f : difficulty == 1 ? .12f : .06f;
    }

    /** Pass a fresh [0,1) random float for every direct hit on a breakable brick. */
    public static boolean powerupDrops(int difficulty, float roll) {
        return roll >= 0f && roll < powerupChance(difficulty);
    }

    /** Every level shares one quiet gray background in Classic mode. */
    public static String backgroundAsset(boolean newBackgrounds, boolean menu, int level) {
        if (!newBackgrounds) return "backgrounds/classic.png";
        if (menu) return "backgrounds/menu.png";
        return String.format(java.util.Locale.US, "backgrounds/level_%02d.png", level);
    }

    /** Menu switch is below Difficulty; HUD switch sits beside Pause. */
    public static boolean backgroundToggleTouch(float x, float y, float worldHeight, boolean menu) {
        if (!menu) return x >= 141f && x <= 205f && y >= 26f && y <= 41f;
        if (worldHeight < 340f)
            return x >= 63f && x <= 231f && y >= worldHeight * .88f - 13f
                    && y <= worldHeight * .88f + 13f;
        return x >= 68f && x <= 230f && y >= worldHeight * .831f
                && y <= worldHeight * .882f;
    }

    /**
     * All screen sizes use the same logical 240-unit horizontal board. Use
     * screen WIDTH as the normal scale, and only reduce it when there are too
     * few pixels vertically to hold HUD + bricks + paddle + fire controls.
     * This avoids tiny boards on square cover displays. No DPI assumptions.
     * {uniformScale, offsetX, offsetY, logicalHeight, compactUIFlag}
     */
    public static float[] fittedViewport(int width, int height) {
        if (width <= 0 || height <= 0) return new float[]{1f, 0f, 0f, 440f, 0f};
        final float minLogicalHeight = 200f;
        float scale = Math.min(width / 240f, height / minLogicalHeight);
        float boardWidth = 240f * scale;
        float logicalHeight = height / scale;
        return new float[]{scale, (width - boardWidth) * .5f, 0f,
                logicalHeight, logicalHeight < 365f ? 1f : 0f};
    }

    /**
     * Reserve the bottom for the FIRE button on shorter screens, including
     * foldable covers. If Android reports a bottom camera cutout, keep the
     * paddle above it as well. Coordinates here are logical canvas units.
     */
    public static float paddleY(float height, float cutoutBottom) {
        float compactness = clamp((height - 290f) / 100f, 0f, 1f);
        float margin = 64f - 19f * compactness; // 64 short -> 45 tall
        float y = height - margin;
        if (cutoutBottom > 0f) y = Math.min(y, height - cutoutBottom - 17f);
        return Math.max(116f, y);
    }

    /** Compatibility entry point for older Java level tests. */
    public static float paddleY(float height, boolean unusedCompactFlag) {
        return paddleY(height, 0f);
    }

    /** The first row moves continuously from 54 to 80 logical units. */
    public static float brickStartY(float height) {
        return 54f + 26f * clamp((height - 260f) / 180f, 0f, 1f);
    }

    /**
     * Fit every stage's actual brick rows above the paddle. The row pitch
     * shrinks smoothly on a short view; bricks and collisions use the SAME
     * height. On ordinary tall phones this returns the original 15/12 units.
     */
    public static float brickPitch(float height, float paddleY, int lastRow) {
        float room = paddleY - 31f - brickStartY(height) - 12f;
        return Math.min(15f, Math.max(2.3f, room / Math.max(1, lastRow)));
    }

    public static float brickHeight(float rowPitch) {
        return Math.min(12f, rowPitch * .82f);
    }

    /** Used by the older cover layout tests; shares the new geometry. */
    public static float compactBrickPitch(float height, float paddleY, int lastRow) {
        return brickPitch(height, paddleY, lastRow);
    }

    public static float brickHeight(float rowPitch, boolean unusedCompactFlag) {
        return brickHeight(rowPitch);
    }

    /** For very long phones ball speed rises slightly to avoid slow traversal. */
    public static float speedMultiplier(float height) {
        return clamp((float)Math.sqrt(Math.max(1f, height - 55f) / 385f), .78f, 1.37f);
    }

    /** Short-screen FIRE sits BELOW paddle touch strip, not over the paddle. */
    public static float[] fireBounds(float height) {
        if (height < 365f) {
            float top = Math.max(height - 35f, paddleY(height, 0f) + 19f);
            return new float[]{13f, top, 76f, height - 10f};
        }
        return new float[]{174f, height - 118f, 227f, height - 73f};
    }

    /** The visible Difficulty button (compact menus are laid out separately). */
    public static boolean difficultyTouch(float y, float height) {
        if (height < 340f) return y >= height * .725f - 16f && y <= height * .725f + 16f;
        return y >= height * .68f && y <= height * .83f;
    }

    private static float clamp(float number, float low, float high) {
        return Math.max(low, Math.min(high, number));
    }

    /** -1 means the benefit lasts until replaced or the next lost ball/level. */
    public static float powerupDuration(int difficulty, float normalDuration) {
        return difficulty <= 0 ? -1f : difficulty == 1 ? normalDuration : normalDuration * .5f;
    }

    public static boolean bombAdjacent(int rowA, int colA, int rowB, int colB) {
        return Math.abs(rowA - rowB) <= 1 && Math.abs(colA - colB) <= 1;
    }

    public static int capsuleVariety() { return 15; }
}
