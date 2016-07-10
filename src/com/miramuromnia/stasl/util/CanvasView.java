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

import static android.R.attr.angle;
import static android.content.ContentValues.TAG;

public class CanvasView extends View {

  private static final String TAG = "CanvasView";

  private Bitmap mBitmap;
  private Path[] mPath;
  Context context;
  private Paint mPaint;
  private static final float LINE_DIST = 250.0f;
  private Canvas mCanvas;
  private float mX;
  private float mY;

  public CanvasView(Context c, AttributeSet attrs) {
    super(c, attrs);
    context = c;

    // we set a new Path
    mPath = new Path[2];
    mPath[0] = new Path();
    mPath[1] = new Path();

    mX = 500.0f;
    mY = 250.0f;

    // and we set a new Paint with the desired attributes
    mPaint = new Paint();
    mPaint.setAntiAlias(true);
    mPaint.setColor(Color.RED);
    mPaint.setStyle(Paint.Style.STROKE);
    mPaint.setStrokeJoin(Paint.Join.ROUND);
    mPaint.setStrokeWidth(20);
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
    mPaint.setColor(Color.RED);
    canvas.drawPath(mPath[0], mPaint);
    mPaint.setColor(Color.GREEN);
    canvas.drawPath(mPath[1], mPaint);
    mPath[0].reset();
    mPath[1].reset();
  }

  public void updateAngles(double[] angles, int maxVoices) {
    for(int i = 0; i < maxVoices; i++) {
      double d = Math.PI * angles[i] / 180.0f;
      mPath[i].reset();
      mPath[i].moveTo(mX, mY);
      mPath[i].rLineTo((float) (LINE_DIST * Math.sin(d)), -(float) (LINE_DIST * Math.cos(d)));
    }
    invalidate();
  }
}
