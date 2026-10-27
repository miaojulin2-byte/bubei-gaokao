package cn.local.gaokao;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Small, dependency-free bar chart for the stats page. */
final class WenyanBarChart extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String[] labels = new String[0];
    private int[] learn = new int[0];
    private int[] review = new int[0];
    private boolean dual = true;

    private static final int ORANGE = 0xffff7900;
    private static final int PURPLE = 0xff8a7bd8;
    private static final int MUTED = 0xff7a8580;
    private static final int GRID = 0x12000000;

    WenyanBarChart(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setData(String[] labels, int[] learn, int[] review, boolean dual) {
        this.labels = labels == null ? new String[0] : labels;
        this.learn = learn == null ? new int[0] : learn;
        this.review = review == null ? new int[0] : review;
        this.dual = dual;
        invalidate();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    float barWidthFor(float contentWidth){return Math.min(dp(10),contentWidth/7f*.22f);}

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (labels.length == 0) return;
        float width = getWidth(), height = getHeight();
        float left = dp(6), right = width - dp(6), top = dp(26), bottom = height - dp(24);
        int maximum = 1;
        for (int value : learn) maximum = Math.max(maximum, value);
        for (int value : review) maximum = Math.max(maximum, value);
        float slot = (right - left) / labels.length;
        float barWidth = barWidthFor(right-left);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(GRID);
        for (int i = 0; i <= 3; i++) {
            float y = bottom - (bottom - top) * i / 3f;
            canvas.drawLine(left, y, right, y, paint);
        }
        // Light vertical grid lines, one per day/week slot.
        paint.setColor(0x0a000000);
        for (int i = 0; i <= labels.length; i++) {
            float x = left + slot * i;
            canvas.drawLine(x, top, x, bottom, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < labels.length; i++) {
            float cx = left + slot * (i + .5f);
            if (dual) {
                drawBar(canvas, cx - (barWidth + dp(4)) / 2, learnAt(i), maximum, top, bottom, barWidth, ORANGE);
                drawBar(canvas, cx + (barWidth + dp(4)) / 2, reviewAt(i), maximum, top, bottom, barWidth, PURPLE);
                drawCount(canvas, cx - (barWidth + dp(4)) / 2, learnAt(i), maximum, top, bottom);
                drawCount(canvas, cx + (barWidth + dp(4)) / 2, reviewAt(i), maximum, top, bottom);
            } else {
                drawBar(canvas, cx, learnAt(i), maximum, top, bottom, barWidth, ORANGE);
                drawCount(canvas, cx, learnAt(i), maximum, top, bottom);
            }
            paint.setColor(MUTED);
            paint.setTextSize(dp(9));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(labels[i], cx, bottom + dp(16), paint);
        }
    }

    private int learnAt(int i) {
        return i < learn.length ? learn[i] : 0;
    }

    private int reviewAt(int i) {
        return i < review.length ? review[i] : 0;
    }

    private void drawBar(Canvas canvas, float cx, int value, int maximum, float top,
                         float bottom, float width, int color) {
        if (value <= 0) return;
        float h = Math.max(dp(2), (bottom - top) * value / maximum);
        paint.setColor(color);
        canvas.drawRoundRect(new RectF(cx - width / 2, bottom - h, cx + width / 2, bottom),
                width / 2, width / 2, paint);
    }

    private void drawCount(Canvas canvas, float cx, int value, int maximum, float top, float bottom) {
        if (value <= 0) return;
        float h = Math.max(dp(2), (bottom - top) * value / maximum);
        paint.setColor(MUTED);
        paint.setTextSize(dp(9));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.valueOf(value), cx, bottom - h - dp(3), paint);
    }
}
