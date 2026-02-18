package com.example.lepsibakalari.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class LiquidLoadingView extends View {
    private Paint paint;
    private Path path;
    private float progress = 0f;
    private long startTime;

    public LiquidLoadingView(Context context) {
        super(context);
        init();
    }

    public LiquidLoadingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        path = new Path();
        startTime = System.currentTimeMillis();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        long elapsed = System.currentTimeMillis() - startTime;
        float waveOffset = (elapsed % 2000) / 2000f; // 0 to 1
        float waveOffset2 = (elapsed % 3000) / 3000f; // Secondary wave

        int w = getWidth();
        int h = getHeight();

        path.reset();
        float centerY = h / 2f;
        float amplitude = h / 6f;

        // Create dual-wave liquid effect
        path.moveTo(0, h);
        for (int x = 0; x <= w; x += 3) {
            float wave1 = (float) Math.sin((x / (float) w * 3 * Math.PI) + (waveOffset * 2 * Math.PI));
            float wave2 = (float) Math.sin((x / (float) w * 5 * Math.PI) + (waveOffset2 * 2 * Math.PI));
            float y = centerY + (wave1 * amplitude * 0.7f) + (wave2 * amplitude * 0.3f);
            path.lineTo(x, y);
        }
        path.lineTo(w, h);
        path.close();

        // Draw orb background
        paint.setAlpha(40);
        canvas.drawCircle(w / 2f, h / 2f, h / 2.5f, paint);

        // Draw liquid waves
        paint.setAlpha(180);
        canvas.drawPath(path, paint);

        // Draw subtle highlight
        paint.setAlpha(60);
        canvas.drawCircle(w / 2f, h / 3f, h / 4f, paint);
        paint.setAlpha(255);

        invalidate();
    }
}
