package com.miramuromnia.stasl.activity;

import android.app.Activity;
import android.content.Context;
import android.media.*;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.miramuromnia.stasl.R;
import org.jtransforms.fft.DoubleFFT_1D;
import org.jtransforms.fft.FloatFFT_1D;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

import static java.util.Arrays.copyOf;

public class MainActivity extends Activity {

  private final static String TAG = MainActivity.class.getSimpleName();

  private static final int RECORDER_SAMPLERATE = 88200;
  private static final int PLAYBACK_SAMPLERATE = 44100;
  private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
  private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

  private final double SPD_OF_SOUND = 0.3432; // (mm/us)
  private final double MIC_DISTANCE = 142.4; // (mm)

  private final double ALPHA = 0.05;

  private final String AUDIO_RAW = "audio_raw.pcm";
  private final String AUDIO_PRO = "audio_processed.pcm";

  private AudioRecord recorder = null;
  private Thread recordingThread = null;
  private boolean isRecording = false;
  private TextView outView = null;
  private TextView leftMagView = null;
  private TextView rightMagView = null;
  private TextView xccMagView = null;

  private ArrayList<Short> playRaw = null;
  private ArrayList<Short> playPro = null;

  private float mOut = 0.0f;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    outView = (TextView) findViewById(R.id.xcc);
    rightMagView = (TextView) findViewById(R.id.rightMag);
    leftMagView = (TextView) findViewById(R.id.leftMag);
    xccMagView = (TextView) findViewById(R.id.xccMag);

    playRaw = new ArrayList<>();
    playPro = new ArrayList<>();
  }

  public void onPlay(View view) {
    Thread playThread = new Thread(new Runnable() {
      public void run() {
        playAudio(view);
      }
    }, "AudioPlay Thread");
    playThread.start();
  }

  private void playAudio(View view) {
    Log.d(TAG, "Playback...");
    ArrayList<Short> data = (view.getId() == R.id.PlayPro) ? playPro : playRaw;
    short[] shorts = new short[data.size()];
      for(int i = 0; i < data.size(); i++) {
        shorts[i] = data.get(i);
      }

    Log.d(TAG, "Number of Samples: " + shorts.length);

      AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
          AudioFormat.CHANNEL_CONFIGURATION_MONO,
          AudioFormat.ENCODING_PCM_16BIT, shorts.length * 2,
          AudioTrack.MODE_STATIC);
      at.write(shorts, 0, shorts.length);
      at.play();
  }

  public void onRecClick(View view) {
    Button btn = (Button) view;
    Button play = (Button) findViewById(R.id.Play);
    Button playPro = (Button) findViewById(R.id.PlayPro);

    if(isRecording) {
      btn.setText("Record");
      play.setEnabled(true);
      playPro.setEnabled(true);
      stopRecording();
    } else {
      btn.setText("Stop");
      play.setEnabled(false);
      playPro.setEnabled(false);
      startRecording();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
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


  //convert short to byte
  float xccMax = 0.0f;
  private int getTime(short[] leftData, short[] rightData) {
    FloatFFT_1D fftDo = new FloatFFT_1D(leftData.length);
    float[] leftFFT = new float[leftData.length * 2];
    float[] rightFFT = new float[rightData.length * 2];
    float[] outFFT = new float[rightData.length * 2];
    for(int i = 0; i < leftData.length; i++) {
      leftFFT[i] = leftData[i];
      rightFFT[i] = rightData[i];
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

    int ret = 0;
    float max = 0.0f;

    for(int i = 0; i < outFFT.length; i+=2) {
      if(Math.abs(outFFT[i]) > max) {
        max = Math.abs(outFFT[i]);
        ret = i/2;
      }
    }

    xccMax = max;

    return ret;
  }



  private double angleAvg = 0.0f;
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



      int outPre = getTime(leftData, rightData);
      if(outPre > 512) outPre -= 1024;

      double dt = outPre * 11.34; // 88.2kHz -> 11.34us
      double angle = dt * SPD_OF_SOUND / MIC_DISTANCE;
      if(angle > 1.0) angle = 1.0;
      if(angle < -1.0) angle = -1.0;

      angle = Math.asin(angle) * 180.0 / Math.PI;

      if(xccMax > 1000000000f) {
        angleAvg = angle * ALPHA  + (1.0 - ALPHA) * angleAvg;
      }

      final double finAngle = angleAvg;

      runOnUiThread(new Runnable() {
        @Override
        public void run() {

          outView.setText("Angle: " + Math.round(finAngle));
          xccMagView.setText("XCC: " + xccMax);
        }
      });

      // Write Raw to ArrayList
      for(int i = 0; i < leftData.length; i += 2) {
        playRaw.add(leftData[i]);
        int index = (i+outPre);
        index = (index < 0) ? index + rightData.length : index%rightData.length;
        if(i-outPre > 0 && i-outPre < rightData.length)
          playPro.add((short)(((int)leftData[i] + (int)rightData[i-outPre]) / 2));
        else playPro.add(leftData[i]);
      }



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
