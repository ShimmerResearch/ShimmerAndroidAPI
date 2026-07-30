package com.shimmerresearch.shimmerspeedtest;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import android.view.Menu;
import android.view.MenuItem;

import com.clj.fastble.BleManager;
import com.shimmerresearch.androidradiodriver.Shimmer3BleAndroidRadioByteCommunication;
import com.shimmerresearch.androidradiodriver.Shimmer3RAndroidRadioByteCommunication;
import com.shimmerresearch.androidradiodriver.ShimmerSerialPortAndroid;
import com.shimmerresearch.androidradiodriver.VerisenseBleAndroidRadioByteCommunication;
import com.shimmerresearch.comms.SerialPortByteCommunication;
import com.shimmerresearch.comms.TestRadioSerialPort;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.shimmer3.communication.SpeedTestProtocol;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private static final int PERMISSION_REQUEST_BACKGROUND_LOCATION = 1;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 2;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED
    ||ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN}, 2);
            } else if(Build.VERSION.SDK_INT >=Build.VERSION_CODES.M){
                if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    if (this.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                        if (this.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                            builder.setTitle("This app needs background location access");
                            builder.setMessage("Please grant location access so this app can detect beacons in the background.");
                            builder.setPositiveButton(android.R.string.ok, null);
                            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @TargetApi(23)
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                            PERMISSION_REQUEST_BACKGROUND_LOCATION);
                                }

                            });
                            builder.show();
                        }
                        else {
                            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                            builder.setTitle("Functionality limited");
                            builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons in the background.  Please go to Settings -> Applications -> Permissions and grant background location access to this app.");
                            builder.setPositiveButton(android.R.string.ok, null);
                            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                }

                            });
                            builder.show();
                        }

                    }
                }else {
                    if (!this.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                PERMISSION_REQUEST_FINE_LOCATION);
                    }
                }

            }

        }


        BleManager.getInstance().init(getApplication());
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });




    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}