package com.retro.brickbreaker;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The first five patterns were transcribed from the five user-supplied screenshots.
 * Blue outlines on the source pictures mark old fixed capsule positions,
 * NOT blue bricks. Remix keeps this metadata for reference, but power-up
 * drops are random per hit (GameplayRules), independent of capsule locations.
 * Levels 6-34 are loaded from editable, bundled levels/level_XX.txt assets.
 * The bundled data is based on documented patterns but has not been verified
 * against the original GIF diagrams cell by cell. The Android game never needs
 * a network connection to load levels. Procedural fallback data is retained
 * solely for a missing or invalid edited map.
 */
public final class LevelData {
    private LevelData() {}

    public static final int FINAL_LEVEL = 34;
    public static final float BRICK_W = 28f;
    public static final float BRICK_H = 12f;
    public static final float PITCH_X = 31f;
    public static final float PITCH_Y = 15f;
    public static final float START_Y = 80f;

    private static final String[][] FIRST_FIVE = {
        {".3.3.", "3...3", ".....", "3...3", "3...3", ".....", "3...3", ".3.3."},
        {".....", "..2..", ".232.", "23R32", ".232.", "..2.."},
        {"23.33", "33.23", ".....", "23.23", "33.33", ".....", "32.33", "33.33"},
        {"R.R.R", "2.R.R", "R...R", ".....", ".....", "R...2", "2.R.R", "2.2.2"},
        {".RRR.", ".....", "RR.RR", "R...R", "R...R", "RR.RR", ".....", ".R2S."}
    };

    // Row/column pairs for the highlighted bricks in the original diagrams.
    private static final int[][][] CAPSULES_FIRST_FIVE = {
        {{0,3}, {3,0}},
        {{2,1}, {2,2}, {3,2}, {3,3}},
        {{0,4}, {3,4}, {4,1}},
        {{1,4}, {5,0}, {7,2}},
        {{2,3}, {5,0}, {5,4}}
    };

    public static final class Brick {
        public float x, y;
        public final int row, col;
        public int strength; // 1-4 hits remaining; -1 = unbreakable silver/steel (gun only)
        public final boolean capsule;
        public boolean alive = true;
        public int laserHits = 0;

        Brick(float x, float y, int row, int col, int strength, boolean capsule) {
            this.x = x;
            this.y = y;
            this.row = row;
            this.col = col;
            this.strength = strength;
            this.capsule = capsule;
        }
    }

    public static List<Brick> forLevel(int level) {
        return forLevel(level, null);
    }

    /**
     * Guide data is optional UTF-8 text loaded by the Activity from assets.
     * Each row contains 5 cells: R,2,3,4,S or .; the last line is capsules:r,c;r,c
     * Source data is deliberately editable without touching the game engine.
     */
    public static List<Brick> forLevel(int level, String guideData) {
        String[] rows;
        int[][] capsuleCells;
        Parsed parsed = level > 5 ? parseImported(guideData) : null;
        if (parsed != null) {
            rows = parsed.rows;
            capsuleCells = parsed.capsules;
        } else if (level >= 1 && level <= 5) {
            rows = FIRST_FIVE[level - 1];
            capsuleCells = CAPSULES_FIRST_FIVE[level - 1];
        } else {
            String[] documentedShape = documentedFallback(level);
            rows = documentedShape != null ? documentedShape : generateRows(level);
            capsuleCells = level == 27 || level == 31 || level == 33 ? new int[0][]
                    : generateCapsules(rows, level);
        }
        int columns = rows[0].length();
        float startX = columns == 5 ? 42f : 13f;
        List<Brick> bricks = new ArrayList<>();
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < columns; c++) {
                char ch = rows[r].charAt(c);
                if (ch != 'R' && ch != 'S' && ch != '2' && ch != '3' && ch != '4') continue;
                boolean containsCapsule = false;
                if (capsuleCells != null) {
                    for (int[] cell : capsuleCells) {
                        if (cell[0] == r && cell[1] == c) containsCapsule = true;
                    }
                }
                int strength = ch == 'S' ? -1 : ch == '4' ? 4 : ch == '3' ? 3 : ch == '2' ? 2 : 1;
                bricks.add(new Brick(startX + c * PITCH_X, START_Y + r * PITCH_Y,
                        r, c, strength, containsCapsule));
            }
        }
        return bricks;
    }

    private static final class Parsed {
        String[] rows;
        int[][] capsules;
        Parsed(String[] rows, int[][] capsules) {
            this.rows = rows;
            this.capsules = capsules;
        }
    }

    private static Parsed parseImported(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            List<String> rows = new ArrayList<>();
            List<int[]> capsules = new ArrayList<>();
            for (String raw : data.split("\\r?\\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("capsules:")) {
                    String cells = line.substring("capsules:".length());
                    if (!cells.isEmpty()) {
                        for (String pair : cells.split(";")) {
                            String[] parts = pair.split(",");
                            if (parts.length == 2) capsules.add(new int[] {
                                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1])});
                        }
                    }
                } else if (line.matches("[R234S.]{5,7}")) {
                    if (!rows.isEmpty() && rows.get(0).length() != line.length()) return null;
                    rows.add(line);
                }
            }
            if (rows.isEmpty()) return null;
            return new Parsed(rows.toArray(new String[0]), capsules.toArray(new int[0][]));
        } catch (RuntimeException ex) {
            return null; // A partially edited import must not break launching the game.
        }
    }

    // Source-informed emergency fallback forms for distinctive late challenges.
    // These are only emergency fallbacks. Android normally loads bundled maps.
    private static String[] documentedFallback(int level) {
        switch (level) {
            case 27: // Single red brick in a chamber, opening near the left wall.
                return new String[]{"SSSSS", "S...S", "S.3.S", "S...S", "SSSSS",
                        "..SSS", "SSSSS", "SSSSS"};
            case 30: // Two high bricks and one lower brick behind steel rows.
                return new String[]{"3...3", "SS.SS", ".....", ".....", "..2..",
                        ".....", "SSSSS", ".SSS."};
            case 31: // Very narrow zigzag steel openings, no capsules.
                return new String[]{"SS.SS", "S.2.S", "SS.SS", "S...S", "SS.SS",
                        "S.2.S", "SS.SS", "SS.SS"};
            case 32: // A left-side chute with a capsule behind the first opening.
                return new String[]{"2SSSS", "R...S", "R.SSS", "R.S.S", "R.S.S",
                        ".SS.S", "SSS.S", "SSSSS"};
            case 33: // Scattered one-hit reds among steel; no capsules.
                return new String[]{"S.R.S", "R.S.R", ".R.R.", "SS.SS", "R.R.R",
                        ".S.S.", "R.R.R", "S.S.S", "..R.."};
            case 34: // The documented finale: steel S-shaped maze, ONE red brick.
                return new String[]{"3SSSS", "S...S", "S.SSS", "S.S..", "S.SSS",
                        "S...S", "SSS.S", "S...S", "SSS.S", "...SS"};
            default:
                return null;
        }
    }

    private static int[][] generateCapsules(String[] rows, int level) {
        List<int[]> eligible = new ArrayList<>();
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                if (rows[r].charAt(c) == 'R' || rows[r].charAt(c) == '2' || rows[r].charAt(c) == '3') eligible.add(new int[]{r, c});
            }
        }
        java.util.Collections.shuffle(eligible, new Random(113L + level * 1419L));
        int count = Math.min(eligible.size(), 3 + level % 3);
        int[][] selected = new int[count][2];
        for (int i = 0; i < count; i++) selected[i] = eligible.get(i);
        return selected;
    }

    private static String[] generateRows(int level) {
        String[] pattern = new String[10];
        Random rng = new Random(93171L + level * 611L);
        int form = (level - 6) % 10;
        for (int r = 0; r < pattern.length; r++) {
            StringBuilder sb = new StringBuilder(7);
            for (int c = 0; c < 7; c++) {
                boolean red;
                int dc = Math.abs(c - 3), dr = Math.abs(r - 4);
                switch (form) {
                    case 0: red = (c == r % 7 || c == 6 - r % 7 || r == 0); break;
                    case 1: red = (r % 3 != 2 && c % 3 != 2); break;
                    case 2: red = dc + dr / 2 <= 3; break;
                    case 3: red = r == 0 || r == 9 || c == 0 || c == 6 || (r == 4 && c % 2 == 0); break;
                    case 4: red = ((r + c) % 2 == 0 || (r == 1 && c % 2 == 1)); break;
                    case 5: red = r >= dc * 2 && r < 10 - dc; break;
                    case 6: red = c == 0 || c == 6 || (r % 3 == 0 && c >= 1 && c <= 5); break;
                    case 7: red = (r == 1 || r == 4 || r == 7 || (c == 3 && r % 3 != 0)); break;
                    case 8: red = r % 2 == 0 ? c != 3 : dc <= 1; break;
                    default: red = rng.nextFloat() > .32f && (r < 8 || c % 2 == 0); break;
                }
                boolean steel = red && level >= 13 && (r * 7 + c + level * 2) % 23 == 0;
                sb.append(steel ? 'S' : red ? 'R' : '.');
            }
            pattern[r] = sb.toString();
        }
        return pattern;
    }
}
