package com.ethankgordon.stasl.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.*;
import android.os.Bundle;
import android.widget.TextView;
import com.ethankgordon.stasl.R;
import com.ethankgordon.stasl.util.CanvasView;
import org.jtransforms.fft.FloatFFT_1D;
import java.util.*;

import android.support.v4.app.ActivityCompat;
import android.widget.Toast;

public class MainActivity extends Activity {

  private final static String TAG = MainActivity.class.getSimpleName();

  private static final int RECORDER_SAMPLERATE = 88200;
  private static final int PLAYBACK_SAMPLERATE = 44100;
  private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
  private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

  private final double SPD_OF_SOUND = 0.3432; // (mm/us)
  private final double MIC_DISTANCE = 142.4; // (mm)

  private final int PERM_REQ_REC_AUDIO = 123;

  private double[] mAngles;

  private final double ALPHA = 0.05;

  private AudioRecord recorder = null;
  private Thread recordingThread = null;
  private boolean isRecording = false;
  private TextView outView = null;

  private ArrayList<Short> playRaw = null;
  private ArrayList<Short> playPro = null;

  private CanvasView mCanvasView = null;

  private float mOut = 0.0f;

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String permissions[], int[] grantResults) {
    switch (requestCode) {
      case PERM_REQ_REC_AUDIO: {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

          // permission was granted, yay! Do the
          // contacts-related task you need to do.

          startRecording();

        } else {

          // permission denied, boo! Disable the
          // functionality that depends on this permission.
          Context context = getApplicationContext();
          CharSequence text = "Audio permission required! Please restart app.";
          int duration = Toast.LENGTH_SHORT;
          Toast toast = Toast.makeText(context, text, duration);
          toast.show();
        }
      }
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    outView = (TextView) findViewById(R.id.xcc);

    playRaw = new ArrayList<>();
    playPro = new ArrayList<>();

    mCanvasView = (CanvasView) findViewById(R.id.canvas);
    mAngles = new double[MAX_VOICES];
  }

  @Override
  public void onResume() {
    super.onResume();
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
              PERM_REQ_REC_AUDIO);
    } else {
      startRecording();
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    // ignore orientation change
    super.onConfigurationChanged(newConfig);
  }

  @Override
  public void onPause() {
    super.onPause();
    stopRecording();
  }

  int BufferElements2Rec = 2048; // want to play 2048 (2K) since 2 bytes we use only 1024
  int BytesPerElement = 4; // 2 bytes in 16bit format * 2 samples per element

  private void startRecording() {

    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
        RECORDER_SAMPLERATE, RECORDER_CHANNELS,
        RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);

    recorder.startRecording();
    isRecording = true;
    recordingThread = new Thread(new Runnable() {
      public void run() {
        processAudio();
      }
    }, "AudioRecorder Thread");
    recordingThread.start();
  }

  private final float XCC_CUTOFF = 1000000000f;
  private final int MAX_VOICES = 1;
  private final int MAX_RANGE = 5;
  private class Pair implements Comparable<Pair> {
    Integer mIndex;
    Float mVal;

    private Pair(int index, float data) {
      mIndex = index;
      mVal = data;
    }

    public int compareTo(Pair other) {
      return other.mVal.compareTo(this.mVal);
    }
  }
  private ArrayList<Integer> karensFunc(float[] data) {
    ArrayList<Pair> candidates = new ArrayList<>();

    for (int i = 0; i < data.length; i++) {
      if(data[i] < XCC_CUTOFF) continue;
      if(i > 60 && i < data.length - 60) continue;
      boolean flag = true;
      for(int j = 1; j <= MAX_RANGE; j++) {
        if (data[i] < data[(i + j) % data.length]) { flag = false; break; }
        if (data[i] < data[(i + data.length - j) % data.length]) { flag = false; break; }
      }
      if(!flag) continue;
      candidates.add(new Pair(i, data[i]));
    }

    Collections.sort(candidates);

    ArrayList<Integer> res = new ArrayList<>();
    int max = Math.min(MAX_VOICES, candidates.size());
    for(int i = 0; i < max; i++) {
      res.add(candidates.get(i).mIndex);
    }

    return res;
  }



  private ArrayList<Integer> getTimes(short[] leftData, short[] rightData) {
    FloatFFT_1D fftDo = new FloatFFT_1D(leftData.length);
    float[] leftFFT = new float[leftData.length * 2];
    float[] rightFFT = new float[rightData.length * 2];
    float[] outFFT = new float[rightData.length * 2];

    float leftMean = 0.0f;
    float rightMean = 0.0f;
    for(int i = 0; i < leftData.length; i++) {
      leftMean += leftData[i];
      rightMean += rightData[i];
    }
    leftMean /= leftData.length;
    rightMean /= rightData.length;

    for(int i = 0; i < leftData.length; i++) {
      leftFFT[i] = leftData[i] - leftMean;
      rightFFT[i] = rightData[i] - rightMean;
    }

    fftDo.realForwardFull(leftFFT);
    fftDo.realForwardFull(rightFFT);
    for(int i = 0; i < outFFT.length / 2; i++) {
      // Real Part
      outFFT[2*i] = leftFFT[2*i]*rightFFT[2*i] + leftFFT[2*i + 1]*rightFFT[2*i+1];

      // Imaginary Part
      outFFT[2*i + 1] = rightFFT[2*i]*leftFFT[2*i+1] - leftFFT[2*i]*rightFFT[2*i+1];
    }
    fftDo.complexInverse(outFFT, true);

    float[] data = new float[outFFT.length / 2];
    for(int i = 0; i < outFFT.length; i+=2) {
      data[i/2] = outFFT[i];
    }

    float mean = 0.0f;
    for(float f : data) {
      mean += f;
    }
    mean /= (float)(data.length);

    for(int i= 0; i < data.length; i++) {
      data[i] -= mean;
    }

    final float[] finalData = data;

    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        float newMean = 0.0f;
        for(float f : finalData) {
          newMean += f;
        }
        newMean /= finalData.length;
        String myString = "Mean: " + Math.round(newMean);
        outView.setText(myString);
      }
    });

    return karensFunc(data);
  }

  private void processAudio() {
    short sData[] = new short[BufferElements2Rec];
    playRaw.clear();
    playPro.clear();

    while (isRecording) {
      // gets the voice output from microphone to byte format

      int readSize = recorder.read(sData, 0, BufferElements2Rec);
      if(readSize < 1) continue;

      short[] leftData = new short[readSize / 2];
      short[] rightData = new short[readSize / 2];

      for(int i = 0; i < readSize/2; i++) {
        leftData[i] = sData[i*2];
        rightData[i] = sData[i*2 + 1];
      }



      ArrayList<Integer> outPre = getTimes(leftData, rightData);
      ArrayList<Integer> outPost = new ArrayList<>();
      for(Integer i : outPre) {
        if(i >= 0) {
          if(i > 512) i -= 1024;
          outPost.add(i);
        }
      }

      Collections.sort(outPost);

      final int maxVoices = outPost.size();

      for(int i = 0; i < outPost.size(); i++) {

        double dt = outPost.get(i) * 11.34; // 88.2kHz -> 11.34us
        double angle = dt * SPD_OF_SOUND / MIC_DISTANCE;
        if (angle > 1.0) angle = 1.0;
        if (angle < -1.0) angle = -1.0;

        angle = Math.asin(angle) * 180.0 / Math.PI;

        mAngles[i] = angle * ALPHA + (1.0 - ALPHA) * mAngles[i];
      }

      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          mCanvasView.updateAngles(mAngles, maxVoices);
        }
      });
    }
  }



  private void stopRecording() {
    // stops the recording activity
    if (null != recorder) {
      isRecording = false;
      recorder.stop();
      recorder.release();
      recorder = null;
      recordingThread = null;
    }
  }

}
