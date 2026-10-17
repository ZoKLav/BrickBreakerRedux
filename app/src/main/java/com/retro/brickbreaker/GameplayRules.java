package com.retro.brickbreaker;

/** Pure-Java gameplay settings so the touch geometry and difficulty are testable without Android. */
public final class GameplayRules {
    private GameplayRules() { }

    // Coordinates are the game's 240-unit logical canvas, not device pixels.
    public static boolean paddleTouch(float x, float y, float paddleY, float worldHeight) {
        return x >= 9f && x <= 231f && y >= paddleY - 26f && y < worldHeight - 8f;
    }

    public static boolean fireTouch(float x, float y, float worldHeight) {
        return x >= 174f && x <= 227f
                && y >= worldHeight - 118f && y <= worldHeight - 73f;
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

    /** Menu switch is below Difficulty; HUD switch sits beside (not over) Pause. */
    public static boolean backgroundToggleTouch(float x, float y, float worldHeight, boolean menu) {
        return menu
                ? x >= 68f && x <= 230f && y >= worldHeight * .831f && y <= worldHeight * .882f
                : x >= 141f && x <= 205f && y >= 26f && y <= 41f;
    }

    /** The cover display's cameras occupy its lower right. Reserve 18% of its height.
     *  Returns {uniform scale, x inset, y inset, logical height, is cover-sized}.
     *  No pixel/DPI assumptions: all calculations use View.getWidth()/getHeight().
     */
    public static float[] fittedViewport(int width, int height) {
        if (width <= 0 || height <= 0) return new float[] {1f, 0f, 0f, 440f, 0f};
        boolean compact = (float)height / width < 1.63f;
        float safeHeight = compact ? height * .82f : height;
        float scale = Math.min(width / 240f, safeHeight / 440f);
        float boardHeight = Math.max(440f, safeHeight / scale);
        float xOffset = (width - 240f * scale) * .5f;
        return new float[] {scale, xOffset, 0f, boardHeight, compact ? 1f : 0f};
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
