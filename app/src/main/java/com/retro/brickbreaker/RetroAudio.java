package com.retro.brickbreaker;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import java.io.IOException;

/** Tiny offline retro tones included as replaceable WAV assets. */
final class RetroAudio {
    static final int HIT = 0, BREAK = 1, CATCH = 2, LOSE = 3, SHOOT = 4, CLEAR = 5;
    private SoundPool pool;
    private final int[] ids = new int[6];

    RetroAudio(Context context) {
        try {
            AudioAttributes attr = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            pool = new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(attr).build();
            String[] names = {"hit", "break", "catch", "lose", "shoot", "clear"};
            for (int i = 0; i < names.length; i++) {
                ids[i] = pool.load(context.getAssets().openFd("sounds/" + names[i] + ".wav"), 1);
            }
        } catch (IOException | RuntimeException ex) {
            if (pool != null) pool.release();
            pool = null;
        }
    }

    void play(int sound) {
        if (pool != null && ids[sound] > 0) pool.play(ids[sound], .28f, .28f, 0, 0, 1f);
    }
    void release() { if (pool != null) { pool.release(); pool = null; } }
}
