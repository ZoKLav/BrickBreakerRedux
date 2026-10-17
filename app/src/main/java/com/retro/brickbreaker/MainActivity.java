package com.retro.brickbreaker;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/** No XML layout, Kotlin, libraries, or network permissions: one custom Canvas view. */
public final class MainActivity extends Activity {
    private BrickGameView gameView;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        gameView = new BrickGameView(this);
        setContentView(gameView);
    }

    @Override protected void onPause() {
        if (gameView != null) gameView.pauseForBackground();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.resetFrameTime();
    }

    @Override protected void onDestroy() {
        if (gameView != null) gameView.releaseAudio();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (gameView != null) gameView.handleBack();
        else super.onBackPressed();
    }
}
