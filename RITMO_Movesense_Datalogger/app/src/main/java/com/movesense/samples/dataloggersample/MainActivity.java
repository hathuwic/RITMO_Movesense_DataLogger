package com.movesense.samples.dataloggersample;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.movesense.mds.Mds;
import com.movesense.mds.MdsConnectionListener;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsResponseListener;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;

import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemLongClickListener, AdapterView.OnItemClickListener  {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int MY_PERMISSIONS_REQUEST = 1;

    // MDS singleton
    static Mds mMds;
    public static final String URI_CONNECTEDDEVICES = "suunto://MDS/ConnectedDevices";
    public static final String URI_EVENTLISTENER = "suunto://MDS/EventListener";
    public static final String SCHEME_PREFIX = "suunto://";
    private static final String URI_TIME = "suunto://{0}/Time";

    // BleClient singleton
    static private RxBleClient mBleClient;

    //
    // UI
    private ListView mScanResultListView;
    private static ArrayList<MyScanResult> mScanResArrayList = new ArrayList<>();
    ArrayAdapter<MyScanResult> mScanResArrayAdapter;

    private boolean isConnecting = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init Scan UI
        mScanResultListView = (ListView)findViewById(R.id.listScanResult);
        mScanResArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, mScanResArrayList);
        mScanResultListView.setAdapter(mScanResArrayAdapter);
        mScanResultListView.setOnItemLongClickListener(this);
        mScanResultListView.setOnItemClickListener(this);

        // Set scanResultText to default value
        TextView scanListResult = (TextView)findViewById(R.id.scanListText);
        scanListResult.setText("Select a device to connect to...");

        // Make sure we have all the permissions this app needs
        requestNeededPermissions();

        // Initialize Movesense MDS library
        initMds();
    }

    private RxBleClient getBleClient() {
        // Init RxAndroidBle (Ble helper library) if not yet initialized
        if (mBleClient == null)
        {
            mBleClient = RxBleClient.create(this);
        }

        return mBleClient;
    }

    private void initMds() {
        if (mMds == null) {
            mMds = Mds.builder().build(this);
        }
    }

    void requestNeededPermissions()
    {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

            // No explanation needed, we can request the permissions.
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                MY_PERMISSIONS_REQUEST);
        }
    }

    Disposable mScanSubscription;
    public void onScanClicked(View view) {
        findViewById(R.id.buttonScan).setVisibility(View.GONE);
        findViewById(R.id.buttonScanStop).setVisibility(View.VISIBLE);

        TextView scanListText = (TextView)findViewById(R.id.scanListText);
        scanListText.setText("Select a device to connect to...");

        // Disconnect from any already connected devices so they show in the list, otherwise notify no devices connected
        if (mScanResArrayList.size() != 0 || mScanResArrayList != null) {
            for (MyScanResult device : mScanResArrayList) {
                if (device != null && device.connectedSerial != null) {
                    Log.i(LOG_TAG, "Disconnecting from BLE device: " + device.macAddress);
                    mMds.disconnect(device.macAddress);
                    Toast.makeText(MainActivity.this, "Disconnected from: " + device.connectedSerial, Toast.LENGTH_SHORT).show();
                }
            }
        }
        else {
            Toast.makeText(MainActivity.this, "No devices to disconnect.", Toast.LENGTH_LONG).show();
        }

        // Start with empty list
        mScanResArrayList.clear();
        mScanResArrayAdapter.notifyDataSetChanged();

        mScanSubscription = getBleClient().scanBleDevices(
                new ScanSettings.Builder()
                        // .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // change if needed
                        // .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // change if needed
                        .build()
                // add filters if needed
        )

                .subscribe(
                        scanResult -> {
                            Log.d(LOG_TAG,"scanResult: " + scanResult);
                            // Toast.makeText(MainActivity.this, "scanResult: " + scanResult, Toast.LENGTH_LONG).show();

                            // Process scan result here. filter movesense devices.
                            if (scanResult.getBleDevice()!=null &&
                                    scanResult.getBleDevice().getName() != null &&
                                    scanResult.getBleDevice().getName().startsWith("Movesense")) {

                                // Replace if exists already, add otherwise
                                MyScanResult msr = new MyScanResult(scanResult);
                                if (mScanResArrayList.contains(msr))
                                    mScanResArrayList.set(mScanResArrayList.indexOf(msr), msr);
                                else
                                    // Should add newly found devices to the end of the list
                                    mScanResArrayList.add(mScanResArrayList.size(), msr);

                                mScanResArrayAdapter.notifyDataSetChanged();
                            }
                        },
                        throwable -> {
                            Log.e(LOG_TAG,"scan error: " + throwable);
//                            Toast.makeText(this, "Scan error: " + throwable, Toast.LENGTH_LONG).show();
                            // Handle an error here.

                            // Re-enable scan buttons, just like with ScanStop
                            onScanStopClicked(null);
                        }
                );
    }

    public void onScanStopClicked(View view) {
        if (mScanSubscription != null)
        {
            mScanSubscription.dispose();
            mScanSubscription = null;
        }

        findViewById(R.id.buttonScan).setVisibility(View.VISIBLE);
        findViewById(R.id.buttonScanStop).setVisibility(View.GONE);
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= mScanResArrayList.size())
            return;

        MyScanResult device = mScanResArrayList.get(position);
        // If the device not already connected via Bluetooth
        if (!device.isConnected()) {
            // Stop scanning
            onScanStopClicked(null);

            // Mark as currently connecting to a sensor and disable scan button
            if (!isConnecting) {
                isConnecting = true;
                findViewById(R.id.buttonScan).setEnabled(false);
            }

            // Update scanListText
            TextView scanListText = (TextView)findViewById(R.id.scanListText);
            String scanListTextString = "Connecting to device: " +
                    device.name;    //toString().substring(device.toString().indexOf("se ")+3, device.toString().indexOf(" ["));
            scanListText.setText(scanListTextString);

            // And connect to the device
            connectBLEDevice(device);
        }
        // If the device is already connected via Bluetooth
//        else if (device.isConnected()) {
//            // Update scanListText
//            TextView scanListText = (TextView)findViewById(R.id.scanListText);
//            String scanListTextString = "Rejoining device: " + device.name;
//            scanListText.setText(scanListTextString);
//
//            // Open the DataLoggerActivity
////            Toast.makeText(MainActivity.this, "Device already connected: " + device.connectedSerial, Toast.LENGTH_LONG).show();
//            Intent intent = new Intent(MainActivity.this, DataLoggerActivity.class);
//            intent.putExtra(DataLoggerActivity.SERIAL, device.connectedSerial);
//            startActivity(intent);
//        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= mScanResArrayList.size())
            return false;

        MyScanResult device = mScanResArrayList.get(position);

        Log.i(LOG_TAG, "Disconnecting from BLE device: " + device.macAddress);
        mMds.disconnect(device.macAddress);
//        Toast.makeText(MainActivity.this, "Disconnected from: " + device.connectedSerial, Toast.LENGTH_LONG).show();
        device.markDisconnected();

        return true;
    }

    private void connectBLEDevice(MyScanResult device) {
        // Disables scan button when connecting to a device


        RxBleDevice bleDevice = getBleClient().getBleDevice(device.macAddress);
        final Activity me = this;
        Log.i(LOG_TAG, "Connecting to BLE device: " + bleDevice.getMacAddress());
        mMds.connect(bleDevice.getMacAddress(), new MdsConnectionListener() {

            @Override
            public void onConnect(String s) {
                Log.d(LOG_TAG, "onConnect:" + s);
            }

            @Override
            public void onConnectionComplete(String macAddress, String serial) {
                for (MyScanResult sr : mScanResArrayList) {
                    if (sr.macAddress.equalsIgnoreCase(macAddress)) {
                        sr.markConnected(serial);
                        break;
                    }
                }
                mScanResArrayAdapter.notifyDataSetChanged();

                // Set sensor clock
                setCurrentTimeToSensor(serial);

                // Re-enables scan button once connected to device
                if (isConnecting) {
                    isConnecting = false;
                    findViewById(R.id.buttonScan).setEnabled(true);
                }

                // Open the DataLoggerActivity
//                Toast.makeText(MainActivity.this, "Starting to open DataLogger activity.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(me, DataLoggerActivity.class);
                intent.putExtra(DataLoggerActivity.SERIAL, serial);
                startActivity(intent);
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "onError:" + e);

                showConnectionError(e);
            }

            @Override
            public void onDisconnect(String bleAddress) {
                Log.d(LOG_TAG, "onDisconnect: " + bleAddress);
                for (MyScanResult sr : mScanResArrayList) {
                    if (bleAddress.equals(sr.macAddress)) {
                        // Unsubscribe all from possible
                        if (sr.connectedSerial != null &&
                                DataLoggerActivity.s_INSTANCE != null &&
                                sr.connectedSerial.equals(DataLoggerActivity.s_INSTANCE.connectedSerial)) {
                            DataLoggerActivity.s_INSTANCE.finish();
                        }
                        sr.markDisconnected();
                    }
                }
                mScanResArrayAdapter.notifyDataSetChanged();
            }
        });
    }


    private void setCurrentTimeToSensor(String serial) {
        String timeUri = MessageFormat.format(URI_TIME, serial);
        String payload = "{\"value\":" + (new Date().getTime() * 1000) + "}";
        mMds.put(timeUri, payload, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "PUT /Time successful: " + data);
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "PUT /Time returned error: " + e);
            }
        });

    }

    private void showConnectionError(MdsException e) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Connection Error:")
                .setMessage(e.getMessage());

        builder.create().show();
    }

}
