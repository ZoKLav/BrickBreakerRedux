package com.retro.brickbreaker;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Standalone portrait arcade engine; no external gameplay libraries.
 * Logical coordinates are 240 units wide, height adapts to the device.
 * All graphics are canvas-drawn or PNGs in assets/ and can be changed.
 */
public final class BrickGameView extends View {
    private static final float WIDTH = 240f;
    private static final float PADDLE_HEIGHT = 11f;
    private static final int MENU = 0, READY = 1, PLAY = 2, PAUSED = 3, CLEAR = 4, OVER = 5;
    private static final String[] DIFFICULTIES = {"EASY", "NORMAL", "HARD"};
    private static final float[] INPUT_DELAYS = {0f, .25f, .5f};
    private static final int[] START_LIVES = {5, 3, 1};
    private static final String[] POWER_NAMES = {
        "LONG", "SLOW", "GUN", "LASER", "MULTI", "CATCH", "FLIP", "WRAP", "BOMB", "LIFE",
        "PIERCE", "SHIELD", "MEGA", "BIG BALL", "MAGNET"
    };
    private static final int LONG = 0, SLOW = 1, GUN = 2, LASER = 3, MULTI = 4,
            CATCH = 5, FLIP = 6, WRAP = 7, BOMB = 8, LIFE = 9,
            PIERCE = 10, SHIELD = 11, MEGA = 12, BIG_BALL = 13, MAGNET = 14;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final ArrayDeque<PendingMove> pendingMoves = new ArrayDeque<>();
    private final List<Ball> balls = new ArrayList<>();
    private final List<Drop> drops = new ArrayList<>();
    private final List<Shot> shots = new ArrayList<>();
    private final SharedPreferences prefs;
    private final RetroAudio audio;
    private final Random rng = new Random();
    private List<LevelData.Brick> bricks = new ArrayList<>();
    private Bitmap background, brickRed, brickTwo, brickThree, brickFour, brickSilver, paddleImage, menuImage;
    private float scale = 1f, offsetX = 0f, offsetY = 0f, worldHeight = 440f, paddleY = 395f;
    private boolean compactCover;
    private float paddleX = 120f, paddleTarget = 120f, paddleWidth = 46f;
    private float timeSeconds = 0f, clearCounter, descendClock;
    private long lastFrameNanos;
    private float weaponCooldown = 0f, speedUpThreshold = 48f;
    private int difficulty, phase = MENU, level = 1, cycle = 0, lives, score, highScore;
    private int hitsThisLife, paddleHits, gunBullets;
    private int power = -1;
    private float powerRemaining = 0f;
    private boolean bombReady, shieldReady, fireHeld, newBackgrounds;
    private float explosionClock, explosionX, explosionY;
    private int paddlePointerId = -1, firePointerId = -1;
    private String toast = "";
    private float toastClock = 0f;

    private static final class Ball {
        float x, y, vx, vy;
        Ball(float x, float y, float vx, float vy) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        }
    }
    private static final class Drop {
        float x, y; final int kind;
        Drop(float x, float y, int kind) { this.x = x; this.y = y; this.kind = kind; }
    }
    private static final class Shot {
        float x, y; final boolean gun;
        Shot(float x, float y, boolean gun) { this.x = x; this.y = y; this.gun = gun; }
    }
    private static final class PendingMove {
        float target, due;
        PendingMove(float target, float due) { this.target = target; this.due = due; }
    }

    BrickGameView(Context context) {
        super(context);
        setFocusable(true);
        prefs = context.getSharedPreferences("classic_breaker", Context.MODE_PRIVATE);
        difficulty = Math.max(0, Math.min(2, prefs.getInt("difficulty", 0)));
        highScore = prefs.getInt("high_score", 0);
        newBackgrounds = prefs.getBoolean("new_backgrounds", true);
        audio = new RetroAudio(context);
        brickRed = loadBitmap("sprites/brick_red.png");
        brickTwo = loadBitmap("sprites/brick_2hit.png");
        brickThree = loadBitmap("sprites/brick_3hit.png");
        brickFour = loadBitmap("sprites/brick_4hit.png");
        brickSilver = loadBitmap("sprites/brick_steel.png");
        paddleImage = loadBitmap("sprites/paddle.png");
        menuImage = loadBitmap("backgrounds/menu.png");
        background = backgroundFor(true);
        p.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        setBackgroundColor(Color.rgb(207, 208, 211));
    }

    private Bitmap backgroundFor(boolean menu) {
        String path = GameplayRules.backgroundAsset(newBackgrounds, menu, level);
        Bitmap image = newBackgrounds && menu ? menuImage : loadBitmap(path);
        if (image == null) image = loadBitmap("backgrounds/level_default.png");
        return image;
    }

    private void switchBackgrounds() {
        newBackgrounds = !newBackgrounds;
        prefs.edit().putBoolean("new_backgrounds", newBackgrounds).apply();
        background = backgroundFor(phase == MENU);
        audio.play(RetroAudio.CATCH);
        invalidate();
    }

    private Bitmap loadBitmap(String name) {
        try (InputStream in = getContext().getAssets().open(name)) {
            return BitmapFactory.decodeStream(in);
        } catch (IOException ex) {
            return null; // Color fallback keeps the game playable if files are replaced/removed.
        }
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        float oldPaddleY = paddleY;
        float[] frame = GameplayRules.fittedViewport(w, h);
        scale = frame[0]; offsetX = frame[1]; offsetY = frame[2];
        worldHeight = frame[3]; compactCover = frame[4] > 0f;
        paddleY = worldHeight - 45f;
        // On fold/unfold, keep a live ball near the paddle, not below the new screen.
        if (phase == PLAY) {
            for (Ball ball : balls) if (ball.y > oldPaddleY - 90f)
                ball.y += paddleY - oldPaddleY;
        }
        if (phase == READY && !balls.isEmpty()) attachBall();
    }

    void resetFrameTime() { lastFrameNanos = 0L; invalidate(); }

    void releaseAudio() { audio.release(); }

    void pauseForBackground() {
        if (phase == PLAY || phase == READY) phase = PAUSED;
        resetTouchPointers();
        resetFrameTime();
    }

    void handleBack() {
        resetTouchPointers();
        if (phase == PLAY || phase == READY) phase = PAUSED;
        else if (phase == PAUSED || phase == OVER) {
            phase = MENU;
            background = backgroundFor(true);
        } else if (phase != MENU) phase = MENU;
        invalidate();
    }

    private void beginGame() {
        score = 0;
        lives = START_LIVES[difficulty];
        level = 1;
        cycle = 0;
        newStage();
    }

    /** All 29 later-stage reference-style maps ship with the APK as editable assets. */
    private String loadLevelMap() {
        String path = String.format(Locale.US, "levels/level_%02d.txt", level);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getContext().getAssets().open(path), StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
            return out.toString();
        } catch (IOException ignored) {
            return null; // The bundled offline fallback is always playable.
        }
    }

    private void newStage() {
        bricks = LevelData.forLevel(level, loadLevelMap());
        background = backgroundFor(false);
        paddleX = WIDTH * .5f;
        paddleTarget = paddleX;
        paddleWidth = 46f;
        paddleHits = 0;
        hitsThisLife = 0;
        speedUpThreshold = 48f;
        descendClock = 0f;
        drops.clear();
        shots.clear();
        pendingMoves.clear();
        resetTouchPointers();
        shieldReady = false;
        resetPower();
        bombReady = false;
        explosionClock = 0f;
        prepareNewBall();
        phase = READY;
        invalidate();
        toast = "LEVEL " + (level + cycle * LevelData.FINAL_LEVEL);
        toastClock = 1.4f;
    }

    private void resetPower() {
        power = -1;
        powerRemaining = 0f;
        gunBullets = 0;
        paddleWidth = 46f;
    }

    private void prepareNewBall() {
        balls.clear();
        shieldReady = false;
        balls.add(new Ball(paddleX, paddleY - 7f, 0f, 0f));
        pendingMoves.clear();
        bombReady = false;
        resetPower();
        hitsThisLife = 0;
        paddleHits = 0;
    }

    private void attachBall() {
        if (balls.isEmpty()) balls.add(new Ball(paddleX, paddleY - 7f, 0f, 0f));
        balls.get(0).x = paddleX;
        balls.get(0).y = paddleY - 6.5f;
        balls.get(0).vx = 0f;
        balls.get(0).vy = 0f;
    }

    private void launchBall() {
        if (phase != READY || balls.isEmpty()) return;
        Ball b = balls.get(0);
        float speed = ballSpeed();
        b.vx = speed * .32f;
        b.vy = -(float)Math.sqrt(speed * speed - b.vx * b.vx);
        phase = PLAY;
        audio.play(RetroAudio.HIT);
    }

    private float ballSpeed() {
        float basic = 148f + Math.min(36f, (level - 1) * 1.15f);
        boolean fast;
        if (cycle == 0) fast = hitsThisLife >= speedUpThreshold && hitsThisLife < 114;
        else if (cycle == 1) fast = hitsThisLife < 98;
        else fast = false;
        if (power == SLOW) fast = false;
        return (fast ? basic * 1.53f : basic) * (power == SLOW ? .83f : 1f);
    }

    private void regulateSpeed(Ball ball) {
        float length = (float)Math.hypot(ball.vx, ball.vy);
        if (length < .01f) return;
        float speed = ballSpeed();
        ball.vx *= speed / length;
        ball.vy *= speed / length;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0 ? 0f : Math.min(.045f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        if (phase == PLAY || phase == READY || phase == CLEAR) update(delta);
        canvas.save();
        // Draw dark decorative gutters on compact covers without placing controls
        // or bricks behind the Razr's lower camera lenses.
        if (compactCover) drawCoverGutters(canvas);
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        canvas.clipRect(0, 0, WIDTH, worldHeight);
        renderBackground(canvas);
        if (phase == MENU) renderMenu(canvas);
        else {
            renderGame(canvas);
            if (phase == PAUSED) renderPause(canvas);
            else if (phase == CLEAR) renderClear(canvas);
            else if (phase == OVER) renderOver(canvas);
        }
        canvas.restore();
        if (phase == PLAY || phase == READY || phase == CLEAR) postInvalidateOnAnimation();
    }

    private void update(float dt) {
        timeSeconds += dt;
        if (toastClock > 0) toastClock -= dt;
        if (weaponCooldown > 0) weaponCooldown -= dt;
        if (explosionClock > 0f) explosionClock -= dt;
        if (phase == CLEAR) {
            clearCounter -= dt;
            if (clearCounter <= 0f) {
                level++;
                if (level > LevelData.FINAL_LEVEL) { level = 1; cycle++; }
                newStage();
            }
            return;
        }
        while (!pendingMoves.isEmpty() && pendingMoves.peekFirst().due <= timeSeconds) {
            paddleTarget = pendingMoves.removeFirst().target;
        }
        // No input delay for Easy. Normal and Hard hold touch events for exactly 250/500ms.
        float maxMove = dt * 910f;
        if (power == WRAP) {
            float difference = paddleTarget - paddleX;
            if (difference > WIDTH / 2) difference -= WIDTH;
            if (difference < -WIDTH / 2) difference += WIDTH;
            paddleX += Math.max(-maxMove, Math.min(maxMove, difference));
            if (paddleX < 0) paddleX += WIDTH;
            if (paddleX > WIDTH) paddleX -= WIDTH;
        } else {
            float half = paddleWidth / 2;
            paddleTarget = Math.max(11f + half, Math.min(WIDTH - 11f - half, paddleTarget));
            paddleX += Math.max(-maxMove, Math.min(maxMove, paddleTarget - paddleX));
        }
        if (powerRemaining > 0f) {
            powerRemaining -= dt;
            if (powerRemaining <= 0f) resetPower();
        }
        if (phase == READY) {
            attachBall();
            updateFallingDrops(dt); // Capsules can still fall while Catch holds the ball.
            return;
        }
        if (fireHeld) fireWeapon();
        // Fixed-size physics steps prevent tunnelling through 12px-tall bricks on high refresh displays.
        float remaining = dt;
        while (remaining > .000001f && phase == PLAY) {
            float substep = Math.min(remaining, 1f / 180f);
            updatePhysics(substep);
            remaining -= substep;
        }
        if (phase != PLAY) return;
        // After 50 hits, Normal moves down every 3.5 seconds and Hard every
        // 1.65 seconds. Easy disables descent completely. Manual versions differ
        // slightly in what counts as an impact.
        if (GameplayRules.descending(difficulty, hitsThisLife, paddleHits)) {
            descendClock += dt;
            if (descendClock >= GameplayRules.descentInterval(difficulty)) {
                descendClock = 0f;
                for (LevelData.Brick brick : bricks) if (brick.alive) brick.y += LevelData.PITCH_Y;
                for (LevelData.Brick brick : bricks) {
                    if (brick.alive && brick.y + LevelData.BRICK_H >= paddleY - 18f) {
                        loseLife();
                        break;
                    }
                }
            }
        }
        boolean hasBreakable = false;
        for (LevelData.Brick brick : bricks) {
            if (brick.alive && brick.strength > 0) { hasBreakable = true; break; }
        }
        if (!hasBreakable && phase == PLAY) {
            phase = CLEAR;
            clearCounter = 1.6f;
            drops.clear();
            shots.clear();
            audio.play(RetroAudio.CLEAR);
        }
    }

    private void updatePhysics(float dt) {
        Iterator<Ball> bit = balls.iterator();
        while (bit.hasNext()) {
            Ball b = bit.next();
            regulateSpeed(b);
            float px = b.x, py = b.y;
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            float radius = ballRadius();
            if (b.x < 9f + radius) { b.x = 9f + radius; b.vx = Math.abs(b.vx); hitsThisLife++; audio.play(RetroAudio.HIT); }
            if (b.x > WIDTH - 9f - radius) { b.x = WIDTH - 9f - radius; b.vx = -Math.abs(b.vx); hitsThisLife++; audio.play(RetroAudio.HIT); }
            if (b.y < 43f + radius) { b.y = 43f + radius; b.vy = Math.abs(b.vy); hitsThisLife++; audio.play(RetroAudio.HIT); }

            if (power == MAGNET && b.vy > 0 && b.y > paddleY - 100f) {
                // Gentle pull toward the paddle; enough to help, not enough to teleport.
                b.vx += Math.max(-50f, Math.min(50f, (paddleX - b.x) * 0.8f)) * dt;
            }
            if (b.vy > 0 && py + radius <= paddleY + 2f && b.y + radius >= paddleY
                    && Math.abs(b.x - paddleX) < paddleWidth / 2f + radius) {
                if (power == CATCH) {
                    bit.remove();
                    if (balls.isEmpty()) {
                        balls.add(new Ball(paddleX, paddleY - 7f, 0f, 0f));
                        phase = READY;
                        toast = "CAUGHT - TAP TO LAUNCH";
                        toastClock = 1.6f;
                        return;
                    }
                    continue;
                }
                b.y = paddleY - radius - .2f;
                float fraction = Math.max(-.96f, Math.min(.96f, (b.x - paddleX) / (paddleWidth / 2f)));
                float speed = ballSpeed();
                b.vx = speed * .88f * fraction;
                // Avoid dead vertical columns and almost-horizontal endless bounces.
                if (Math.abs(b.vx) < speed * .12f) b.vx = (b.vx < 0 ? -1 : 1) * speed * .12f;
                b.vy = -(float)Math.sqrt(speed * speed - b.vx * b.vx);
                paddleHits++;
                hitsThisLife++;
                audio.play(RetroAudio.HIT);
            }

            for (LevelData.Brick brick : bricks) {
                if (!brick.alive) continue;
                if (b.x + radius <= brick.x || b.x - radius >= brick.x + LevelData.BRICK_W
                        || b.y + radius <= brick.y || b.y - radius >= brick.y + LevelData.BRICK_H) continue;
                // Resolve on the entry side to get recognizable brickbreaker reflection.
                if (power != PIERCE || brick.strength == -1) {
                    if (py + radius <= brick.y || py - radius >= brick.y + LevelData.BRICK_H) b.vy = -b.vy;
                    else if (px + radius <= brick.x || px - radius >= brick.x + LevelData.BRICK_W) b.vx = -b.vx;
                    else b.vy = -b.vy;
                    b.x = px;
                    b.y = py;
                }
                hitsThisLife++;
                if (brick.strength == -1) audio.play(RetroAudio.HIT);
                else if (bombReady) {
                    bombReady = false;
                    detonateAt(brick);
                } else damageBrick(brick, 10);
                if (power != PIERCE || brick.strength == -1) break;
            }
            // SHIELD is a one-use lower barrier. It catches a missed ball
            // (including one of several balls) before it falls off screen.
            if (shieldReady && b.vy > 0 && b.y + radius >= worldHeight - 17f) {
                b.y = worldHeight - 17f - radius;
                b.vy = -Math.abs(b.vy);
                shieldReady = false;
                toast = "SHIELD SAVED YOU";
                toastClock = 1.4f;
                audio.play(RetroAudio.CATCH);
            }
            if (b.y > worldHeight + 8f) bit.remove();
        }
        for (Iterator<Shot> it = shots.iterator(); it.hasNext();) {
            Shot shot = it.next();
            shot.y -= (shot.gun ? 350f : 460f) * dt;
            if (shot.y < 43f) { it.remove(); continue; }
            boolean hit = false;
            for (LevelData.Brick brick : bricks) {
                if (brick.alive && shot.x >= brick.x && shot.x <= brick.x + LevelData.BRICK_W
                        && shot.y >= brick.y && shot.y <= brick.y + LevelData.BRICK_H) {
                    if (brick.strength == -1 && !shot.gun) {
                        audio.play(RetroAudio.HIT);
                    } else if (shot.gun) {
                        damageBrick(brick, 50);
                    } else {
                        // Original Laser needs two beams to break a regular brick.
                        // The first beam can release its hidden capsule already.
                        brick.laserHits++;
                        if (brick.laserHits == 1) {
                            addScore(5);
                            maybeDropPowerup(brick);
                            audio.play(RetroAudio.HIT);
                        } else {
                            damageBrick(brick, 5);
                        }
                    }
                    hit = true;
                    break;
                }
            }
            if (hit) it.remove();
        }
        updateFallingDrops(dt);
        if (balls.isEmpty() && phase == PLAY) loseLife();
    }

    private void updateFallingDrops(float dt) {
        for (Iterator<Drop> it = drops.iterator(); it.hasNext();) {
            Drop d = it.next();
            d.y += 69f * dt;
            if (Math.abs(d.x - paddleX) < paddleWidth / 2f + 5f
                    && d.y + 6 >= paddleY && d.y <= paddleY + PADDLE_HEIGHT) {
                applyPower(d.kind);
                it.remove();
                continue;
            }
            if (d.y > worldHeight) it.remove();
        }
    }

    /** Classic behavior: the armed ball instantly destroys its next colored brick,
     * then delivers one hit to all eight neighboring positions (even strong bricks).
     * The explosion ring and shaking bricks make this effect unmistakable.
     */
    private void detonateAt(LevelData.Brick center) {
        explosionX = center.x + LevelData.BRICK_W * .5f;
        explosionY = center.y + LevelData.BRICK_H * .5f;
        explosionClock = .65f;
        // Explosion is a distinct, one-use capsule, not a timed mode.
        center.strength = 1;
        damageBrick(center, 15, false);
        for (LevelData.Brick other : bricks) {
            if (other == center || !other.alive || other.strength <= 0) continue;
            if (GameplayRules.bombAdjacent(other.row, other.col, center.row, center.col))
                damageBrick(other, 5, false);
        }
        toast = "BOOM!";
        toastClock = 1f;
        audio.play(RetroAudio.CLEAR);
    }

    private void damageBrick(LevelData.Brick brick, int points) {
        damageBrick(brick, points, true);
    }

    private void damageBrick(LevelData.Brick brick, int points, boolean directHit) {
        if (!brick.alive) return;
        boolean breakable = brick.strength > 0;
        if (brick.strength == -1 && points < 50) return;
        if (points >= 50) brick.strength = 0; // Gun destroys even steel.
        else brick.strength--;
        addScore(points);
        // New Remix rule: successful direct hits on colored bricks roll separately.
        // Splash damage and indestructible steel do not farm power-ups.
        if (directHit && breakable) maybeDropPowerup(brick);
        if (brick.strength <= 0) {
            brick.alive = false;
            audio.play(RetroAudio.BREAK);
        } else {
            audio.play(RetroAudio.HIT);
        }
    }

    private void maybeDropPowerup(LevelData.Brick brick) {
        if (brick.strength == -1 || !GameplayRules.powerupDrops(difficulty, rng.nextFloat())) return;
        drops.add(new Drop(brick.x + LevelData.BRICK_W / 2f,
                brick.y + LevelData.BRICK_H, rng.nextInt(GameplayRules.capsuleVariety())));
    }

    private void addScore(int points) {
        score += points;
        if (score > highScore) {
            highScore = score;
            prefs.edit().putInt("high_score", highScore).apply();
        }
    }

    private void applyPower(int kind) {
        addScore(50);
        audio.play(RetroAudio.CATCH);
        toast = POWER_NAMES[kind];
        toastClock = 1.2f;
        if (kind == LIFE) {
            lives++;
            resetPower();
            return;
        }
        if (kind == MULTI || kind == MEGA) {
            if (power == CATCH) resetPower();
            splitBalls(kind == MEGA ? 8 : 4);
            return;
        }
        if (kind == SHIELD) {
            shieldReady = true;
            return;
        }
        if (kind == BOMB) { bombReady = true; return; }
        power = kind;
        float normalDuration = 20f;
        if (kind == LONG) paddleWidth = 67f;
        else paddleWidth = 46f;
        if (kind == SLOW) normalDuration = 16f;
        if (kind == GUN) { gunBullets = 3; normalDuration = 40f; }
        if (kind == LASER) normalDuration = 25f;
        if (kind == FLIP || kind == WRAP) normalDuration = 15f;
        if (kind == PIERCE) normalDuration = 12f;
        if (kind == BIG_BALL || kind == MAGNET) normalDuration = 18f;
        powerRemaining = GameplayRules.powerupDuration(difficulty, normalDuration);
    }

    private void splitBalls(int total) {
        if (balls.isEmpty() || phase != PLAY) return;
        Ball b = balls.get(0);
        float speed = ballSpeed();
        float baseAngle = (float)Math.atan2(b.vy, b.vx);
        int current = balls.size();
        for (int i = current; i < total; i++) {
            float spread = ((i - current) - (total - current - 1) / 2f) * .24f;
            float angle = baseAngle + spread;
            balls.add(new Ball(b.x, b.y, speed * (float)Math.cos(angle),
                    speed * (float)Math.sin(angle)));
        }
    }

    private float ballRadius() { return power == BIG_BALL ? 5.2f : 3f; }

    private void fireWeapon() {
        if (phase != PLAY || weaponCooldown > 0 || (power != LASER && power != GUN)) return;
        if (power == GUN && gunBullets == 0) return;
        if (power == GUN) {
            shots.add(new Shot(paddleX, paddleY - 5f, true));
            gunBullets--;
            weaponCooldown = .27f;
            if (gunBullets == 0) {
                resetPower(); // Gun is a three-shot consumable; never show a dead FIRE button.
                toast = "GUN EMPTY";
                toastClock = 1f;
                fireHeld = false;
            }
        } else {
            shots.add(new Shot(paddleX - paddleWidth * .32f, paddleY - 6f, false));
            shots.add(new Shot(paddleX + paddleWidth * .32f, paddleY - 6f, false));
            weaponCooldown = .24f;
        }
        audio.play(RetroAudio.SHOOT);
    }

    private void loseLife() {
        if (phase != PLAY) return;
        lives--;
        audio.play(RetroAudio.LOSE);
        shots.clear();
        drops.clear();
        resetTouchPointers();
        if (lives <= 0) {
            phase = OVER;
            return;
        }
        prepareNewBall();
        phase = READY;
        toast = "BALL LOST";
        toastClock = 1f;
    }

    /** Touches above the paddle never steer it; the FIRE zone has its own pointer. */
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        if (action == MotionEvent.ACTION_CANCEL) {
            resetTouchPointers();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int id = event.getPointerId(index);
            if (id == paddlePointerId) paddlePointerId = -1;
            if (id == firePointerId) {
                firePointerId = -1;
                fireHeld = false;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (paddlePointerId != -1) {
                int moveIndex = event.findPointerIndex(paddlePointerId);
                if (moveIndex >= 0) {
                    float x = (event.getX(moveIndex) - offsetX) / scale;
                    float y = (event.getY(moveIndex) - offsetY) / scale;
                    if (GameplayRules.paddleTouch(x, y, paddleY, worldHeight)) queuePaddleMove(x);
                }
            }
            return true;
        }
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN)
            return true;
        float x = (event.getX(index) - offsetX) / scale;
        float y = (event.getY(index) - offsetY) / scale;
        if (x < 0f || x > WIDTH || y < 0f || y > worldHeight) return true;
        int id = event.getPointerId(index);
        if (action == MotionEvent.ACTION_DOWN) {
            if (phase == MENU) {
                if (GameplayRules.backgroundToggleTouch(x, y, worldHeight, true)) {
                    switchBackgrounds();
                } else if (y >= worldHeight * .68f && y <= worldHeight * .83f) {
                    difficulty = (difficulty + 1) % 3;
                    prefs.edit().putInt("difficulty", difficulty).apply();
                    audio.play(RetroAudio.CATCH);
                } else beginGame();
                invalidate();
                return true;
            }
            // The tiny HUD BG switch never steers the paddle or presses Pause.
            if (GameplayRules.backgroundToggleTouch(x, y, worldHeight, false)) {
                switchBackgrounds();
                return true;
            }
            if (phase == PAUSED) {
                if (y > worldHeight * .57f) {
                    phase = MENU;
                    background = backgroundFor(true);
                } else phase = balls.isEmpty() ? READY : (balls.get(0).vx == 0 ? READY : PLAY);
                resetTouchPointers();
                resetFrameTime();
                invalidate();
                return true;
            }
            if (phase == OVER) {
                if (y >= worldHeight * .69f) {
                    phase = MENU; background = backgroundFor(true);
                } else beginGame();
                invalidate();
                return true;
            }
            if (phase == CLEAR) return true;
            if (x > 206f && y < 48f) {
                phase = PAUSED;
                resetTouchPointers();
                invalidate();
                return true;
            }
        }
        if (phase == PLAY && (power == GUN || power == LASER)
                && GameplayRules.fireTouch(x, y, worldHeight)) {
            firePointerId = id;
            fireHeld = true;
            fireWeapon();
            return true;
        }
        if ((phase == READY || phase == PLAY) && paddlePointerId == -1
                && GameplayRules.paddleTouch(x, y, paddleY, worldHeight)) {
            paddlePointerId = id;
            queuePaddleMove(x);
            if (phase == READY) launchBall();
        }
        return true;
    }

    private void resetTouchPointers() {
        paddlePointerId = -1;
        firePointerId = -1;
        fireHeld = false;
    }

    private void queuePaddleMove(float x) {
        float target = power == FLIP ? WIDTH - x : x;
        float delay = INPUT_DELAYS[difficulty];
        if (delay <= 0f) paddleTarget = target;
        else {
            pendingMoves.addLast(new PendingMove(target, timeSeconds + delay));
            while (pendingMoves.size() > 100) pendingMoves.removeFirst();
        }
    }

    private void drawCoverGutters(Canvas c) {
        c.drawColor(Color.rgb(22, 32, 48));
        p.setShader(null);
        p.setColor(Color.rgb(43, 65, 89));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, scale));
        float left = offsetX - 9f, right = offsetX + WIDTH * scale + 9f;
        c.drawLine(left, 0f, left, worldHeight * scale, p);
        c.drawLine(right, 0f, right, worldHeight * scale, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(176, 191, 209));
        p.setTextSize(Math.max(12f, scale * 8f));
        p.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("COVER DISPLAY  -  SAFE PLAYFIELD", getWidth() * .5f,
                Math.min(getHeight() - 12f, worldHeight * scale + 27f), p);
    }

    private void renderBackground(Canvas c) {
        p.setShader(null);
        p.setColor(Color.rgb(222, 224, 226));
        c.drawColor(Color.rgb(215, 216, 219));
        if (background != null) {
            src.set(0, 0, background.getWidth(), background.getHeight());
            dst.set(8, 8, WIDTH - 8, worldHeight - 8);
            p.setColor(Color.WHITE);
            c.drawBitmap(background, src, dst, p);
        } else {
            p.setColor(Color.rgb(223, 224, 228));
            c.drawRect(8, 8, WIDTH - 8, worldHeight - 8, p);
        }
        drawBorder(c);
    }

    private void drawBorder(Canvas c) {
        p.setShader(null);
        p.setColor(Color.rgb(20, 20, 20));
        c.drawRect(0, 0, WIDTH, 8, p);
        c.drawRect(0, worldHeight - 8, WIDTH, worldHeight, p);
        c.drawRect(0, 0, 8, worldHeight, p);
        c.drawRect(WIDTH - 8, 0, WIDTH, worldHeight, p);
        p.setColor(Color.rgb(244, 219, 20));
        c.drawRect(1, 1, WIDTH - 1, 6, p);
        c.drawRect(1, worldHeight - 6, WIDTH - 1, worldHeight - 1, p);
        c.drawRect(1, 1, 6, worldHeight - 1, p);
        c.drawRect(WIDTH - 6, 1, WIDTH - 1, worldHeight - 1, p);
        p.setColor(Color.BLACK);
        for (int j = -2; j <= (int)(worldHeight / 14f); j++) {
            float yy = j * 14f;
            Path slant = new Path();
            slant.moveTo(1, yy + 4); slant.lineTo(1, yy + 10);
            slant.lineTo(6, yy + 4); slant.lineTo(6, yy - 2); slant.close();
            c.drawPath(slant, p);
            Path other = new Path();
            other.moveTo(WIDTH - 6, yy + 4); other.lineTo(WIDTH - 6, yy + 10);
            other.lineTo(WIDTH - 1, yy + 4); other.lineTo(WIDTH - 1, yy - 2); other.close();
            c.drawPath(other, p);
        }
        for (int i = -2; i < 20; i++) {
            float xx = i * 14f;
            Path slant = new Path();
            slant.moveTo(xx, 6); slant.lineTo(xx + 6, 6);
            slant.lineTo(xx + 12, 1); slant.lineTo(xx + 6, 1); slant.close();
            c.drawPath(slant, p);
            Path other = new Path();
            other.moveTo(xx, worldHeight - 1); other.lineTo(xx + 6, worldHeight - 1);
            other.lineTo(xx + 12, worldHeight - 6); other.lineTo(xx + 6, worldHeight - 6);
            other.close(); c.drawPath(other, p);
        }
        p.setColor(Color.DKGRAY);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        c.drawRect(8, 8, WIDTH - 8, worldHeight - 8, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void renderGame(Canvas c) {
        p.setColor(Color.argb(190, 237, 239, 240));
        c.drawRect(9, 9, WIDTH - 9, 41, p);
        p.setColor(Color.rgb(55, 54, 55));
        label(c, "LEVEL " + (level + cycle * LevelData.FINAL_LEVEL), 16, 21, 10.5f,
                Color.rgb(34, 34, 37), Paint.Align.LEFT);
        label(c, "LIVES " + lives, 16, 35, 10.5f, Color.rgb(38, 38, 40), Paint.Align.LEFT);
        label(c, "SCORE " + score, WIDTH - 18f, 21, 10f,
                Color.rgb(34, 34, 37), Paint.Align.RIGHT);
        label(c, "II", WIDTH - 18f, 35, 11, Color.rgb(40, 40, 43), Paint.Align.RIGHT);
        drawBackgroundToggle(c, false);
        for (LevelData.Brick b : bricks) {
            if (!b.alive) continue;
            Bitmap img = b.strength == -1 ? brickSilver :
                    b.strength >= 4 ? brickFour : b.strength == 3 ? brickThree : b.strength == 2 ? brickTwo : brickRed;
            drawSprite(c, img, b.x, b.y, LevelData.BRICK_W, LevelData.BRICK_H,
                    b.strength == -1 ? Color.GRAY : Color.rgb(176, 18, 16));
        }
        for (Drop d : drops) drawCapsule(c, d);
        for (Shot s : shots) {
            p.setColor(s.gun ? Color.rgb(240, 181, 36) : Color.rgb(20, 71, 217));
            c.drawRoundRect(s.x - 1.3f, s.y - 7f, s.x + 1.3f, s.y + 2f, 1, 1, p);
        }
        drawSprite(c, paddleImage, paddleX - paddleWidth / 2f, paddleY,
                paddleWidth, PADDLE_HEIGHT + 2, Color.rgb(24, 68, 208));
        if (power == WRAP) {
            if (paddleX + paddleWidth/2 > WIDTH - 9) drawSprite(c, paddleImage,
                    paddleX - WIDTH - paddleWidth/2, paddleY, paddleWidth, PADDLE_HEIGHT + 2, Color.BLUE);
            if (paddleX - paddleWidth/2 < 9) drawSprite(c, paddleImage,
                    paddleX + WIDTH - paddleWidth/2, paddleY, paddleWidth, PADDLE_HEIGHT + 2, Color.BLUE);
        }
        for (Ball b : balls) drawBall(c, b.x, b.y);
        if (bombReady && phase != OVER) {
            label(c, "BOMB ARMED", WIDTH / 2f, worldHeight - 31f, 9,
                    Color.rgb(171, 53, 20), Paint.Align.CENTER);
            for (Ball ball : balls) {
                p.setColor(Color.argb(210, 251, 111, 18));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.65f);
                c.drawCircle(ball.x, ball.y, 5.8f, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
        if (explosionClock > 0f) {
            float progress = 1f - explosionClock / .65f;
            int opacity = (int)(210f * Math.max(0f, explosionClock / .65f));
            p.setColor(Color.argb(opacity, 248, 102, 15));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(.6f, 4f * (1f - progress)));
            c.drawCircle(explosionX, explosionY, 6f + 45f * progress, p);
            p.setColor(Color.argb((int)(opacity * .6f), 255, 208, 60));
            c.drawCircle(explosionX, explosionY, 12f + 29f * progress, p);
            p.setStyle(Paint.Style.FILL);
        }
        if (shieldReady) {
            p.setColor(Color.rgb(29, 97, 215));
            c.drawRoundRect(10, worldHeight - 17f, WIDTH - 10, worldHeight - 14f, 2f, 2f, p);
        }
        if (phase == READY) {
            label(c, "TAP PADDLE TO LAUNCH", WIDTH/2f, paddleY - 27,
                    12f, Color.rgb(44, 44, 53), Paint.Align.CENTER);
        }
        if (power == LASER || power == GUN) {
            p.setColor(Color.argb(205, 235, 237, 239));
            c.drawRoundRect(174, worldHeight - 118, 227, worldHeight - 73, 6, 6, p);
            p.setColor(Color.rgb(41, 81, 202));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.7f);
            c.drawRoundRect(174, worldHeight - 118, 227, worldHeight - 73, 6, 6, p);
            p.setStyle(Paint.Style.FILL);
            label(c, "FIRE", 200, worldHeight - 94, 10, Color.rgb(23, 58, 165), Paint.Align.CENTER);
            if (power == GUN) label(c, "x" + gunBullets, 200, worldHeight - 82, 8, Color.BLACK, Paint.Align.CENTER);
        }
        if (shieldReady) label(c, "SHIELD READY", 55f, worldHeight - 17, 8f,
                Color.rgb(29, 85, 172), Paint.Align.LEFT);
        if (power != -1) {
            label(c, POWER_NAMES[power] + (powerRemaining < 0f ? " INF" : " " + (int)Math.ceil(powerRemaining) + "s"),
                    WIDTH/2f, worldHeight - 17, 9, Color.rgb(46, 46, 48), Paint.Align.CENTER);
        }
        if (toastClock > 0 && !toast.isEmpty() && phase != READY) {
            label(c, toast, WIDTH/2f, worldHeight * .56f, 12, Color.rgb(54, 54, 62), Paint.Align.CENTER);
        }
    }

    private void drawBall(Canvas c, float x, float y) {
        p.setShader(new LinearGradient(x-3f, y-3f, x+3f, y+3f,
                new int[]{Color.WHITE, Color.rgb(120,128,142), Color.rgb(23,29,49)}, null, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, ballRadius(), p);
        p.setShader(null);
        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(.45f);
        c.drawCircle(x, y, ballRadius(), p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawCapsule(Canvas c, Drop d) {
        p.setColor(Color.rgb(24, 74, 203));
        c.drawRoundRect(d.x - 8.3f, d.y - 6.2f, d.x + 8.3f, d.y + 6.2f, 2, 2, p);
        p.setColor(Color.rgb(219, 232, 255));
        c.drawRoundRect(d.x - 6.8f, d.y - 4.7f, d.x + 6.8f, d.y + 4.7f, 1, 1, p);
        String letter = d.kind == LIFE ? "+" : d.kind == MEGA ? "8" :
                d.kind == SHIELD ? "#" : d.kind == BIG_BALL ? "O" :
                POWER_NAMES[d.kind].substring(0, 1);
        label(c, letter, d.x, d.y + 3.4f, 10, Color.rgb(13, 46, 133), Paint.Align.CENTER);
    }

    private void drawSprite(Canvas c, Bitmap image, float x, float y, float w, float h, int fallback) {
        if (image == null) {
            p.setColor(fallback);
            c.drawRect(x, y, x+w, y+h, p);
            return;
        }
        p.setColor(Color.WHITE);
        dst.set(x, y, x+w, y+h);
        src.set(0, 0, image.getWidth(), image.getHeight());
        c.drawBitmap(image, src, dst, p);
    }

    private void drawBackgroundToggle(Canvas c, boolean menu) {
        float left = menu ? 69f : 141f;
        float right = menu ? 229f : 204f;
        float centerY = menu ? worldHeight * .855f : 33f;
        float hh = menu ? 11f : 7f;
        p.setShader(null);
        p.setColor(Color.argb(225, 228, 232, 241));
        c.drawRoundRect(left, centerY - hh, right, centerY + hh, 4f, 4f, p);
        p.setColor(Color.rgb(39, 76, 170));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        c.drawRoundRect(left, centerY - hh, right, centerY + hh, 4f, 4f, p);
        p.setStyle(Paint.Style.FILL);
        label(c, (menu ? "BACKGROUNDS: " : "BG: ") + (newBackgrounds ? "NEW" : "CLASSIC"),
                (left + right) * .5f, centerY + (menu ? 3.5f : 2.8f),
                menu ? 10f : 7.5f, Color.rgb(26, 54, 116), Paint.Align.CENTER);
    }

    private void renderMenu(Canvas c) {
        p.setColor(Color.argb(60, 255, 255, 255));
        c.drawRect(9, 9, WIDTH - 9, worldHeight - 9, p);
        label(c, "THE HANDHELD CLASSIC REIMAGINED", WIDTH/2f, worldHeight * .18f, 8.5f,
                Color.rgb(70, 69, 76), Paint.Align.CENTER);
        labelOutlined(c, "BRICK", WIDTH / 2, worldHeight * .32f, 34);
        labelOutlined(c, "BREAKER", WIDTH / 2, worldHeight * .42f, 31);
        label(c, "R E M I X", WIDTH / 2f, worldHeight * .465f, 14f,
                Color.rgb(32, 72, 169), Paint.Align.CENTER);
        drawSprite(c, paddleImage, WIDTH/2 - 28, worldHeight*.50f, 56, 16, Color.BLUE);
        drawBall(c, WIDTH / 2f, worldHeight*.49f);
        drawMenuButton(c, "TAP TO PLAY", worldHeight * .615f);
        drawMenuButton(c, "DIFFICULTY: " + DIFFICULTIES[difficulty], worldHeight * .74f);
        label(c, "TAP DIFFICULTY TO CHANGE", WIDTH/2, worldHeight*.795f, 8.5f,
                Color.rgb(62, 62, 72), Paint.Align.CENTER);
        drawBackgroundToggle(c, true);
        label(c, "BEST " + highScore, WIDTH/2, worldHeight*.925f, 10,
                Color.rgb(76, 76, 78), Paint.Align.CENTER);
    }

    private void labelOutlined(Canvas c, String value, float x, float baseline, float size) {
        p.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.8f);
        p.setColor(Color.rgb(36, 24, 22));
        c.drawText(value, x, baseline, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(179, 35, 25));
        c.drawText(value, x, baseline, p);
    }

    private void drawMenuButton(Canvas c, String text, float centerY) {
        p.setColor(Color.rgb(16, 42, 115));
        c.drawRoundRect(31, centerY - 23, 209, centerY + 23, 5, 5, p);
        p.setColor(Color.rgb(58, 119, 246));
        c.drawRoundRect(34, centerY - 20, 206, centerY + 18, 4, 4, p);
        p.setColor(Color.rgb(187, 219, 255));
        c.drawRect(37, centerY - 17, 203, centerY - 13, p);
        label(c, text, WIDTH/2, centerY + 4.5f,
                text.startsWith("DIFFICULTY") ? 11.5f : 15,
                Color.WHITE, Paint.Align.CENTER);
    }

    private void renderPause(Canvas c) {
        overlay(c, "PAUSED", "TAP HERE TO RESUME", "TAP LOWER TO MENU");
    }
    private void renderOver(Canvas c) {
        overlay(c, "GAME OVER", "TAP TO TRY AGAIN", "TAP LOWER FOR MENU");
    }
    private void renderClear(Canvas c) {
        overlay(c, "LEVEL CLEAR", "SCORE " + score, "NEXT LEVEL...");
    }
    private void overlay(Canvas c, String title, String middle, String bottom) {
        p.setColor(Color.argb(150, 220, 223, 229));
        c.drawRect(9, 43, WIDTH - 9, worldHeight - 9, p);
        p.setColor(Color.argb(230, 242, 244, 247));
        c.drawRoundRect(16, worldHeight*.37f, WIDTH - 16, worldHeight*.70f, 6, 6, p);
        p.setColor(Color.rgb(39, 66, 146));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        c.drawRoundRect(16, worldHeight*.37f, WIDTH - 16, worldHeight*.70f, 6, 6, p);
        p.setStyle(Paint.Style.FILL);
        label(c, title, WIDTH/2, worldHeight*.46f, 20, Color.rgb(145, 34, 25), Paint.Align.CENTER);
        label(c, middle, WIDTH/2, worldHeight*.55f, 10, Color.rgb(36, 48, 67), Paint.Align.CENTER);
        label(c, bottom, WIDTH/2, worldHeight*.64f, 10, Color.rgb(36, 48, 67), Paint.Align.CENTER);
    }

    private void label(Canvas c, String text, float x, float y, float size, int color, Paint.Align align) {
        p.setShader(null);
        p.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        p.setTextAlign(align);
        p.setTextSize(size);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawText(text, x, y, p);
    }
}
