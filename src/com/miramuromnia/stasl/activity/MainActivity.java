package com.miramuromnia.stasl.activity;

import android.app.Activity;
import android.os.Bundle;
import com.miramuromnia.stasl.R;

public class MainActivity extends Activity {

  private final static String TAG = MainActivity.class.getSimpleName();

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    getActionBar().setTitle("Stasl");
  }

}
