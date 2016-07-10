package com.miramuromnia.stasl.util;

import android.util.Log;
import android.view.MotionEvent;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import static android.content.ContentValues.TAG;

public class CanvasView extends View {

  private static final String TAG = "CanvasView";

  private Bitmap mBitmap;
  private Path mPath;
  Context context;
  private Paint mPaint;
  private static final float LINE_DIST = 250.0f;
  private double mTargetAngle;
  private Canvas mCanvas;
  private float mX;
  private float mY;

  public CanvasView(Context c, AttributeSet attrs) {
    super(c, attrs);
    context = c;

    // we set a new Path
    mPath = new Path();

    mX = 500.0f;
    mY = 250.0f;

    // and we set a new Paint with the desired attributes
    mPaint = new Paint();
    mPaint.setAntiAlias(true);
    mPaint.setColor(Color.RED);
    mPaint.setStyle(Paint.Style.STROKE);
    mPaint.setStrokeJoin(Paint.Join.ROUND);
    mPaint.setStrokeWidth(10);

    mTargetAngle = 0.0;
  }

  // override onSizeChanged
  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);

    // your Canvas will draw onto the defined Bitmap
    mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    mCanvas = new Canvas(mBitmap);

    mX = (float)(w) / 2.0f;
    mY = (float)(h);
  }

  // override onDraw
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    Log.d(TAG, "Drawing");
    // draw the mPath with the mPaint on the canvas when onDraw
    canvas.drawPath(mPath, mPaint);
  }

  public void updateAngle(double angle) {
    mPath.reset();
    mPath.moveTo(mX, mY);

    mTargetAngle = Math.PI * angle / 180.0f;
    mPath.rLineTo((float)(LINE_DIST*Math.sin(mTargetAngle)), -(float)(LINE_DIST*Math.cos(mTargetAngle)));
    invalidate();
  }
}
