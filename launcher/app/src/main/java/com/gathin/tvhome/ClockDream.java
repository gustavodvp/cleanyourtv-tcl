package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import java.io.File;
import java.util.Arrays;
import java.util.Random;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow.
 * Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in.
 * No network: cheap enough for a 1 GB TV.
 * When the theme pushed screensaver*.jpg pictures, they replace the glow: one at a time, slowly
 * zooming and panning, cross-fading to the next every few minutes. Only the shown picture (and the
 * one fading in, for the length of the fade) is in memory, decoded at screen size in RGB_565.
 */
public class ClockDream extends DreamService {

    private static final long MOVE_MS = 60_000L;
    /** How long each picture stays; it drifts for the whole time, then fades into the next. */
    private static final long PICTURE_MS = 180_000L;
    private static final long FADE_MS = 2_500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View content;
    private GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    private File[] pictures = new File[0];
    private ImageView front, back;
    private int pictureIndex;

    private final Runnable nextPicture = new Runnable() {
        @Override
        public void run() {
            showPicture(pictureIndex + 1);
            handler.postDelayed(this, PICTURE_MS);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            if (pictures.length == 0) shiftGlow();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);

        content = findViewById(R.id.dreamContent);
        View ambient = findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);

        File dir = Config.dir(this);
        File[] found = dir != null ? dir.listFiles((d, name) -> name.startsWith("screensaver") && name.endsWith(".jpg")) : null;
        if (found != null && found.length > 0) {
            Arrays.sort(found);
            pictures = found;
            pictureIndex = random.nextInt(found.length) - 1;   // showPicture(+1) starts at a random one
            front = findViewById(R.id.dreamPictureA);
            back = findViewById(R.id.dreamPictureB);
            ambient.setVisibility(View.GONE);
            findViewById(R.id.dreamDim).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.postDelayed(tick, MOVE_MS);
        if (pictures.length > 0) content.post(nextPicture);   // after layout, so the drift knows the size
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(nextPicture);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(nextPicture);
        for (ImageView v : new ImageView[]{front, back}) {
            if (v != null) {
                v.animate().cancel();
                v.setImageDrawable(null);
            }
        }
        super.onDetachedFromWindow();
    }

    /** Fades the next picture in over the current one and lets it drift slowly until the next change. */
    private void showPicture(int index) {
        for (int tries = 0; tries < pictures.length; tries++) {
            int i = Math.floorMod(index + tries, pictures.length);
            Bitmap bmp = decode(pictures[i]);
            if (bmp == null) continue;
            pictureIndex = i;
            final ImageView in = back, out = front;
            in.animate().cancel();
            in.setImageBitmap(bmp);
            // Ken Burns: start slightly zoomed in and travel towards a random corner for the picture's time.
            // The zoom always leaves more spare picture on each side ((scale - 1) / 2) than the travel, so no edge shows.
            float zoom = 1.14f;
            float dx = (random.nextBoolean() ? 1 : -1) * 0.03f * in.getWidth();
            float dy = (random.nextBoolean() ? 1 : -1) * 0.025f * in.getHeight();
            in.setScaleX(1.08f);
            in.setScaleY(1.08f);
            in.setTranslationX(-dx);
            in.setTranslationY(-dy);
            in.setAlpha(0f);
            in.bringToFront();
            findViewById(R.id.dreamDim).bringToFront();
            content.bringToFront();
            in.animate().alpha(1f).setDuration(FADE_MS).setInterpolator(null).withEndAction(() -> {
                out.animate().cancel();
                out.setAlpha(0f);
                out.setImageDrawable(null);   // only one picture in memory between fades
                in.animate().scaleX(zoom).scaleY(zoom).translationX(dx).translationY(dy)
                        .setDuration(PICTURE_MS - FADE_MS).setInterpolator(new LinearInterpolator()).start();
            }).start();
            front = in;
            back = out;
            return;
        }
    }

    /** Decodes a picture no larger than the screen, without alpha: about 4 MB at 1080p. */
    private Bitmap decode(File file) {
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), opts);
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= dm.widthPixels && opts.outHeight / (sample * 2) >= dm.heightPixels) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            return null;   // a bad picture is skipped; the clock still runs
        }
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
