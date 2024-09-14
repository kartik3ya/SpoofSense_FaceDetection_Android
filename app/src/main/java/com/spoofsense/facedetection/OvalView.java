package com.spoofsense.facedetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class OvalView extends View {

    private Paint paint;

    public OvalView(Context context) {
        super(context);
        init();
    }

    public OvalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OvalView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.GREEN); // Change color as needed
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8); // Set stroke width
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Draw oval
        float left = getWidth() * 0.1f;
        float top = getHeight() * 0.2f;
        float right = getWidth() * 0.9f;
        float bottom = getHeight() * 0.8f;
        canvas.drawOval(left, top, right, bottom, paint);
    }
}
