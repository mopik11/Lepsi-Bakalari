package com.example.lepsibakalari.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Custom View pro Liquid Glass mesh gradient pozadí.
 * Dva velké měkké ovály (modrý nahoře, fialový dole) s černým středem a jemnými světelnými okraji.
 */
public class MeshGradientView extends View {

    private Paint topOvalPaint;
    private Paint bottomOvalPaint;
    private Paint basePaint;

    public MeshGradientView(Context context) {
        super(context);
        init();
    }

    public MeshGradientView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MeshGradientView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        topOvalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bottomOvalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setColor(0xFF000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        float cx = w * 0.5f;
        float radius = Math.max(w, h) * 0.85f;

        // Horní modrý ovál - světle modrá (#EBF7FD) → středně modrá (#007BFF) → tmavě modrá (#000080) → černá
        float topCy = h * 0.2f;
        int[] topColors = {
                0xFF000000,  // střed - černá
                0xFF000080,  // tmavě modrá
                0xFF007BFF,  // středně modrá
                0xFFADD8E6,  // světle modrá okraj (svítící linka)
                0xFFEBF7FD   // horní světle modrá
        };
        float[] topPositions = {0f, 0.25f, 0.5f, 0.75f, 1f};
        RadialGradient topGradient = new RadialGradient(cx, topCy, radius, topColors, topPositions, Shader.TileMode.CLAMP);
        topOvalPaint.setShader(topGradient);

        // Dolní fialovo-hnědý ovál - béžovo-růžová (#FDF0E7) → levandulová (#DDA0DD) → fialová (#4B0082) → černá
        float bottomCy = h * 0.9f;
        int[] bottomColors = {
                0xFF000000,  // střed - černá
                0xFF4B0082,  // tmavě fialová
                0xFF6A5ACD,  // středně fialová
                0xFFE6B8C2,  // světle béžovo-růžová (svítící okraj)
                0xFFFDF0E7   // dolní světle béžová
        };
        float[] bottomPositions = {0f, 0.25f, 0.5f, 0.75f, 1f};
        RadialGradient bottomGradient = new RadialGradient(cx, bottomCy, radius, bottomColors, bottomPositions, Shader.TileMode.CLAMP);
        bottomOvalPaint.setShader(bottomGradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, basePaint);
        canvas.drawRect(0, 0, w, h, bottomOvalPaint);
        canvas.drawRect(0, 0, w, h, topOvalPaint);
    }
}
