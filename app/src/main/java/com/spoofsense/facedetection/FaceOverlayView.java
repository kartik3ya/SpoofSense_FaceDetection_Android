package com.spoofsense.facedetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.View;

public class FaceOverlayView extends View {

    private Paint paint;
    private Paint paintYellow;
    private Path ovalPath;

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paintYellow = new Paint();
        paintYellow.setColor(Color.YELLOW);
        paintYellow.setStyle(Paint.Style.STROKE);
        paintYellow.setStrokeWidth(5);

        // Initialize paint for the white overlay
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);

        // Initialize path for the oval
        ovalPath = new Path();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
//        // Define the oval bounds
//        float left = getWidth() * 0.2f;   // Increase to 20% from the left
//        float top = getHeight() * 0.3f;   // Increase to 30% from the top
//        float right = getWidth() * 0.8f;  // Decrease to 80% from the right
//        float bottom = getHeight() * 0.7f; // Decrease to 70% from the bottom
//
//        // Draw the oval
//        canvas.drawOval(left, top, right, bottom, paint);

        // Set up the area for the transparent oval
//        float left = getWidth() * 0.2f;   // Increase to 20% from the left
//        float top = getHeight() * 0.3f;   // Increase to 30% from the top
//        float right = getWidth() * 0.8f;  // Decrease to 80% from the right
//        float bottom = getHeight() * 0.7f; // Decrease to 70% from the bottom


//        // Set up the area for the transparent oval
        float left = getWidth() * 0.2f;   // Increase to 10% from the left
        float top = getHeight() * 0.2f;  // Increase to 20% from the top
        float right = getWidth() * 0.8f;  // Decrease to 80% from the right
        float bottom = getHeight() * 0.6f; // Decrease to 60% from the bottom (near top)

        // Reset the path
        ovalPath.reset();
        ovalPath.addOval(left, top, right, bottom, Path.Direction.CW);

        // Draw the white background
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        // Draw the oval
        canvas.drawOval(left, top, right, bottom, paintYellow);

        // Set the Xfermode to make the oval transparent
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        // Draw the transparent oval
        canvas.drawPath(ovalPath, paint);

        // Reset Xfermode after drawing
        paint.setXfermode(null);
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
