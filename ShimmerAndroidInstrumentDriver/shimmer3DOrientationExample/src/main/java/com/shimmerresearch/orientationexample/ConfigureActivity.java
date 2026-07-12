package com.shimmerresearch.orientationexample;


import java.lang.ref.WeakReference;

import android.app.Activity;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;

import com.shimmerresearch.shimmer3dexample.R;

public class ConfigureActivity extends AppCompatActivity{
	// Return Intent extra
	public static String mDone = "Done";

	CheckBox cboxEnableLowPowerMag;
	CheckBox cboxEnableGyroOnTheFlyCal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);

    	// Setup the window	    
    	setContentView(R.layout.configure);
    	
    	Bundle extras = getIntent().getExtras();
    	
    	cboxEnableLowPowerMag = (CheckBox) findViewById(R.id.checkBoxLowPowerMag);
    	cboxEnableLowPowerMag.setChecked(extras.getBoolean("LowPowerMag"));
    	cboxEnableLowPowerMag.setOnCheckedChangeListener(new OnCheckedChangeListener(){

			@Override
			public void onCheckedChanged(CompoundButton arg0, boolean enable) {
				// TODO Auto-generated method stub
				Intent intent = new Intent();
   	            intent.putExtra("Command","Mag");
   	            intent.putExtra("Enable",enable);
   	            // Set result and finish this Activity
   	            setResult(Activity.RESULT_OK, intent);
   	            finish();
			}
    		
    	});
    	
    	cboxEnableGyroOnTheFlyCal = (CheckBox) findViewById(R.id.checkBoxGyroOnTheFlyCal);
    	cboxEnableGyroOnTheFlyCal.setChecked(extras.getBoolean("GyroOnTheFlyCal"));
    	cboxEnableGyroOnTheFlyCal.setOnCheckedChangeListener(new OnCheckedChangeListener(){

			@Override
			public void onCheckedChanged(CompoundButton arg0, boolean enable) {
				// TODO Auto-generated method stub
				Intent intent = new Intent();
   	            intent.putExtra("Command","Gyro");
   	            intent.putExtra("Enable",enable);
   	            // Set result and finish this Activity
   	            setResult(Activity.RESULT_OK, intent);
   	            finish();
			}
    		
    	});
    	
	   
    }
    
    
   
    
	@Override
	public void onPause() {
		super.onPause();
		Log.d("Shimmer","On Pause");
		//finish();
		
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d("Shimmer","On Resume");

	}
	/*
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
	*/
}
