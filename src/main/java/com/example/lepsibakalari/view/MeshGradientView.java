package com.example.lepsibakalari.view;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;
import java.util.Random;

public class MeshGradientView extends View {

    private final Paint basePaint = new Paint();
    private final Random random = new Random();
    
    private static final int ORB_COUNT = 5;
    private final Orb[] orbs = new Orb[ORB_COUNT];
    private final ArgbEvaluator colorEvaluator = new ArgbEvaluator();
    
    private long startTime;
    private ValueAnimator colorAnimator;

    private static class Orb {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Matrix matrix = new Matrix();
        Shader shader;
        float x, y, radius;
        int currentColor;
        int targetColor;
        float phaseOffset;
        float movementScale;
    }

    public MeshGradientView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        basePaint.setColor(0xFF000000);
        for (int i = 0; i < ORB_COUNT; i++) {
            orbs[i] = new Orb();
            orbs[i].currentColor = getRandomVibrantColor();
            orbs[i].targetColor = getRandomVibrantColor();
            orbs[i].phaseOffset = random.nextFloat() * (float)Math.PI * 2;
            orbs[i].movementScale = 0.5f + random.nextFloat();
        }
        startColorCycle();
    }

    private int getRandomVibrantColor() {
        float[] hsv = new float[3];
        hsv[0] = random.nextInt(360);
        hsv[1] = 0.7f + random.nextFloat() * 0.3f;
        hsv[2] = 0.6f + random.nextFloat() * 0.3f;
        return Color.HSVToColor(hsv);
    }

    private void startColorCycle() {
        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.setDuration(8000); // 8 seconds to morph
        colorAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            for (Orb orb : orbs) {
                int col = (int) colorEvaluator.evaluate(fraction, orb.currentColor, orb.targetColor);
                updateOrbShader(orb, col);
            }
            invalidate();
        });
        colorAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                for (Orb orb : orbs) {
                    orb.currentColor = orb.targetColor;
                    orb.targetColor = getRandomVibrantColor();
                }
                colorAnimator.start();
            }
        });
        startTime = System.currentTimeMillis();
        colorAnimator.start();
    }

    private void updateOrbShader(Orb orb, int color) {
        if (orb.radius <= 0) return;
        int transparentColor = color & 0x00FFFFFF;
        int[] colors = {color, transparentColor};
        orb.shader = new RadialGradient(0, 0, orb.radius, colors, null, Shader.TileMode.CLAMP);
        orb.paint.setShader(orb.shader);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        for (int i = 0; i < ORB_COUNT; i++) {
            Orb orb = orbs[i];
            orb.radius = w * (0.6f + random.nextFloat() * 0.6f);
            orb.x = random.nextFloat() * w;
            orb.y = random.nextFloat() * h;
            updateOrbShader(orb, orb.currentColor);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, basePaint);

        long elapsed = System.currentTimeMillis() - startTime;
        
        for (int i = 0; i < ORB_COUNT; i++) {
            Orb orb = orbs[i];
            if (orb.shader == null) continue;

            double time = (double) elapsed / 10000.0 + orb.phaseOffset;
            float moveX = (float) Math.sin(time * orb.movementScale) * (w * 0.15f);
            float moveY = (float) Math.cos(time * 0.8 * orb.movementScale) * (h * 0.15f);
            float scale = 1.0f + (float) Math.sin(time * 0.5) * 0.1f;

            orb.matrix.reset();
            orb.matrix.setScale(scale, scale);
            orb.matrix.postTranslate(orb.x + moveX, orb.y + moveY);
            orb.shader.setLocalMatrix(orb.matrix);
            
            canvas.drawRect(0, 0, w, h, orb.paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (colorAnimator != null) colorAnimator.cancel();
    }
}
