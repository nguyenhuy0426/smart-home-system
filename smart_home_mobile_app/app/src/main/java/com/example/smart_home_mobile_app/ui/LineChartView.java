package com.example.smart_home_mobile_app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Minimal line chart that reproduces the former Compose Canvas temperature graph. */
public class LineChartView extends View {
    private final List<Double> values = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LineChartView(Context context) {
        this(context, null);
    }

    public LineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3f));
        TypedValue typedValue = new TypedValue();
        if (getContext().getTheme().resolveAttribute(
                androidx.appcompat.R.attr.colorPrimary, typedValue, true)) {
            paint.setColor(typedValue.data);
        } else {
            paint.setColor(Color.parseColor("#FF396A65"));
        }
    }

    public void setValues(List<Double> newValues) {
        values.clear();
        if (newValues != null) {
            values.addAll(newValues);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values.isEmpty()) {
            return;
        }
        double min = values.get(0);
        double max = values.get(0);
        for (double value : values) {
            if (value < min) min = value;
            if (value > max) max = value;
        }
        double range = Math.max(max - min, 1.0);
        float width = getWidth();
        float height = getHeight();
        Path path = new Path();
        for (int index = 0; index < values.size(); index++) {
            float x = values.size() == 1 ? width / 2f : width * index / (values.size() - 1);
            float y = height - (float) ((values.get(index) - min) / range * height);
            if (index == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, paint);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
