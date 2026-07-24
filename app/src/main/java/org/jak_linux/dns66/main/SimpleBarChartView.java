/* Copyright (C) 2026 ZbogomReklame
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny, dependency-free bar chart, used on the Start tab to show how many
 * domains were blocked per day over the last week.
 */
public class SimpleBarChartView extends View {

    private List<Long> values = new ArrayList<>();
    private final Paint barPaint = new Paint();
    private final Paint textPaint = new Paint();

    public SimpleBarChartView(Context context) {
        super(context);
        init();
    }

    public SimpleBarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint.setAntiAlias(true);
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(getResources().getDisplayMetrics().density * 12);
        textPaint.setTextAlign(Paint.Align.CENTER);
        setContentDescription("");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setValues(List<Long> values, int barColor, int textColor) {
        this.values = values;
        barPaint.setColor(barColor);
        textPaint.setColor(textColor);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values == null || values.isEmpty())
            return;

        int width = getWidth();
        int height = getHeight();
        int count = values.size();
        if (width <= 0 || height <= 0 || count == 0)
            return;

        long max = 1;
        for (Long v : values) {
            if (v != null && v > max)
                max = v;
        }

        float labelSpace = textPaint.getTextSize() * 1.4f;
        float chartHeight = height - labelSpace;
        float slotWidth = (float) width / count;
        float barWidth = slotWidth * 0.6f;

        for (int i = 0; i < count; i++) {
            long v = values.get(i) == null ? 0 : values.get(i);
            float barHeight = (v / (float) max) * chartHeight;
            float left = i * slotWidth + (slotWidth - barWidth) / 2f;
            float right = left + barWidth;
            float top = chartHeight - barHeight;
            float bottom = chartHeight;
            canvas.drawRect(left, top, right, bottom, barPaint);
        }
    }
}
