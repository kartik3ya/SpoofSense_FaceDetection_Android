package com.spoofsense.facedetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class FaceOverlayView extends View {

    private Paint paint;

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Define the oval bounds
        float left = getWidth() * 0.2f;   // Increase to 20% from the left
        float top = getHeight() * 0.3f;   // Increase to 30% from the top
        float right = getWidth() * 0.8f;  // Decrease to 80% from the right
        float bottom = getHeight() * 0.7f; // Decrease to 70% from the bottom

        // Draw the oval
        canvas.drawOval(left, top, right, bottom, paint);
    }

    public float getOvalLeft() {
        return getWidth() * 0.1f;
    }

    public float getOvalTop() {
        return getHeight() * 0.2f;
    }

    public float getOvalRight() {
        return getWidth() * 0.9f;
    }

    public float getOvalBottom() {
        return getHeight() * 0.8f;
    }
}
