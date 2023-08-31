package com.movesense.samples.dataloggersample;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.MdsSubscription;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import io.reactivex.disposables.Disposable;

public class DataLoggerActivity extends AppCompatActivity
        implements
        AdapterView.OnItemClickListener,
        Spinner.OnItemSelectedListener
{
    private static final String URI_MDS_LOGBOOK_ENTRIES = "suunto://MDS/Logbook/{0}/Entries";
    private static final String URI_MDS_LOGBOOK_DATA= "suunto://MDS/Logbook/{0}/ById/{1}/Data";

    private static final String URI_LOGBOOK_ENTRIES = "suunto://{0}/Mem/Logbook/Entries";
    private static final String URI_DATALOGGER_STATE = "suunto://{0}/Mem/DataLogger/State";
    private static final String URI_DATALOGGER_CONFIG = "suunto://{0}/Mem/DataLogger/Config";
    public static final String URI_EVENTLISTENER = "suunto://MDS/EventListener";
    private static final String URI_LOGBOOK_DATA = "/Mem/Logbook/byId/{0}/Data";

    static DataLoggerActivity s_INSTANCE = null;
    private static final String LOG_TAG = DataLoggerActivity.class.getSimpleName();

    public static final String SERIAL = "serial";
    String connectedSerial;

    public DataLoggerState mDLState;
    private String mDLConfigPath;
    private TextView mDataLoggerStateTextView;

    // The delimiter between different addresses when recording multiple streams
    private static final String delimiter = ", ";
    private boolean isLogging = false;

    private String currentConfigPath;

    private ListView mLogEntriesListView;
    private static ArrayList<MdsLogbookEntriesResponse.LogEntry> mLogEntriesArrayList = new ArrayList<>();
    ArrayAdapter<MdsLogbookEntriesResponse.LogEntry> mLogEntriesArrayAdapter;

    public static final String SCHEME_PREFIX = "suunto://";

    private Mds getMDS() {return MainActivity.mMds;}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        s_INSTANCE = this;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_datalogger);

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        // Init state UI
        mDataLoggerStateTextView = (TextView)findViewById(R.id.textViewDLState);

        // Init Log list
        mLogEntriesListView = (ListView)findViewById(R.id.listViewLogbookEntries);
        mLogEntriesArrayAdapter = new ArrayAdapter<MdsLogbookEntriesResponse.LogEntry>(this,
                android.R.layout.simple_list_item_1, mLogEntriesArrayList);
        mLogEntriesListView.setAdapter(mLogEntriesArrayAdapter);
        mLogEntriesListView.setOnItemClickListener(this);

        Spinner pathSpinner = (Spinner)findViewById(R.id.path_spinner);
        pathSpinner.setOnItemSelectedListener(this);
        //mPathSelectionSetInternally = false and pathSpinner.setSelection came from here...


        // Find serial in opening intent
        Intent intent = getIntent();
        connectedSerial = intent.getStringExtra(SERIAL);

        // Set title to serial number of currently device
        this.setTitle("Device: " + connectedSerial);

        // Set top text displaying connected device
//        TextView connectedDeviceText = (TextView)findViewById(R.id.connectedDeviceText);
//        String connectedDeviceTextString = "Connected to: " + connectedSerial;
//        connectedDeviceText.setText(connectedDeviceTextString);

        fetchDataLoggerConfig();

        // Popup based on mDLConfig value after fetchDataConfig()
//        if (mDLConfigPath == null) {
//            Toast.makeText(DataLoggerActivity.this, "mDLConfig is null after fetchDataLoggerConfig().", Toast.LENGTH_SHORT).show();
//        }
//        else {
//            Toast.makeText(DataLoggerActivity.this, "mDLConfig has value after fetchDataLoggerConfig():" + mDLConfigPath, Toast.LENGTH_SHORT).show();
//        }

        updateDataLoggerUI();

        // If the device is not logging, the path is set on create, otherwise the path is left alone.
        if (isLogging) { mPathSelectionSetInternally = true; }
        else { mPathSelectionSetInternally = false; }
//        pathSpinner.setSelection(0);

        refreshLogList();
    }

    // Just updating the UI, no functionality
    private void updateDataLoggerUI() {
        Log.d(LOG_TAG, "updateDataLoggerUI() state: " + mDLState + ", path: " + mDLConfigPath);

        mDataLoggerStateTextView.setText(mDLState != null ? mDLState.toString() : "--");

        // Updates displayed current config path on DataLogger UI update, from currentConfigPath
        // variable set in fetchDataLoggerConfig() and configureDataLogger()
        TextView currentConfigPathTextView = (TextView)findViewById(R.id.currentConfigPathText);
        String currentConfigPathText = "Current path: " + currentConfigPath;
        currentConfigPathTextView.setText(currentConfigPathText);

        // Update current log number based on length of mLogEntriesArrayList
        // TextView currentLog = (TextView)findViewById(R.id.textViewCurrentLogID);
        // currentLog.setText(mLogEntriesArrayList.toArray().length);

        // Enables/disables start and stop buttons based on data logging state and whether there is a valid config path.
        findViewById(R.id.buttonStartLogging).setEnabled((mDLState != null && mDLConfigPath != null));
        findViewById(R.id.buttonStopLogging).setEnabled(mDLState != null);

        // Setting buttons as visible/invisible and enabled/disabled
        if (mDLState != null) {
            if (mDLState.content == 2) {
                findViewById(R.id.buttonStartLogging).setVisibility(View.VISIBLE);
                findViewById(R.id.buttonStopLogging).setVisibility(View.GONE);
//                Toast.makeText(DataLoggerActivity.this, "Start visible, Stop gone: " + mDLConfigPath, Toast.LENGTH_SHORT).show();
            }
            if (mDLState.content == 3) {
                findViewById(R.id.buttonStopLogging).setVisibility(View.VISIBLE);
                findViewById(R.id.buttonStartLogging).setVisibility(View.GONE);
//                Toast.makeText(DataLoggerActivity.this, "Stop visible, Start gone.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Configuration of the DataLogger - this needs a list to log multiple sensors?
    private void configureDataLogger() {
        // Access the DataLogger/Config
        String configUri = MessageFormat.format(URI_DATALOGGER_CONFIG, connectedSerial);
//        Toast.makeText(DataLoggerActivity.this, "configUri: " + configUri, Toast.LENGTH_SHORT).show();

        currentConfigPath = mDLConfigPath;

        // Create the config object
        DataLoggerConfig.DataEntry[] entries;
        if (mDLConfigPath.contains(delimiter)) {
            // Attempting to add capacity for more than 2 delimited addresses (max of 4)
//            String[] entriesListStrings = mDLConfigPath.split(delimiter,0);
//                for (String entryString : entriesListStrings) {
//                    entries = entries.add(new DataLoggerConfig[]{new DataLoggerConfig.DataEntry(entryString)});
//                }
            entries = new DataLoggerConfig.DataEntry[]{new DataLoggerConfig.DataEntry(mDLConfigPath.substring(0, mDLConfigPath.indexOf(delimiter))),
                      new DataLoggerConfig.DataEntry(mDLConfigPath.substring(mDLConfigPath.indexOf(delimiter) + delimiter.length()))};
        }

        else {
            entries = new DataLoggerConfig.DataEntry[]{new DataLoggerConfig.DataEntry(mDLConfigPath)};
        }

//        Toast.makeText(DataLoggerActivity.this, "entries: " + entries, Toast.LENGTH_SHORT).show();

        DataLoggerConfig config = new DataLoggerConfig(new DataLoggerConfig.Config(new DataLoggerConfig.DataEntries(entries)));
        // Toast.makeText(DataLoggerActivity.this, "config: " + config, Toast.LENGTH_LONG).show();

        String jsonConfig = new Gson().toJson(config, DataLoggerConfig.class);

        Log.d(LOG_TAG, "Config request: " + jsonConfig);
//        Toast.makeText(DataLoggerActivity.this, "Config request: " + jsonConfig, Toast.LENGTH_SHORT).show();

        // Method .put actually configures the sensor
        getMDS().put(configUri, jsonConfig, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "PUT config successful: " + data);
//                Toast.makeText(DataLoggerActivity.this, "PUT config SUCCESSFUL: " + data, Toast.LENGTH_SHORT).show();
                Toast.makeText(DataLoggerActivity.this, "Config path updated.", Toast.LENGTH_LONG).show();
                updateDataLoggerUI();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "PUT DataLogger/Config returned error: " + e);
                Toast.makeText(DataLoggerActivity.this, "PUT DataLogger/Config returned ERROR: " + e, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchDataLoggerState() {
        // Access the DataLogger/State
        String stateUri = MessageFormat.format(URI_DATALOGGER_STATE, connectedSerial);

        getMDS().get(stateUri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "GET state successful: " + data);
//                Toast.makeText(DataLoggerActivity.this, "In fetchDataLoggerState: GET state successful:" + data, Toast.LENGTH_LONG).show();

                mDLState = new Gson().fromJson(data, DataLoggerState.class);

                // Changes boolean logging based on state returned from function
                if (mDLState.content == 2) { isLogging = false; }
                else if (mDLState.content == 3) { isLogging = true; }

                // data.substring(data.indexOf(" ")+1).startsWith("3")

                // Checking change made to isLogging boolean in fetchDataLoggerState
//                Toast.makeText(DataLoggerActivity.this, "isLogging after checking mDLState: " + isLogging
//                        + ", mDLState.content: " + mDLState.content, Toast.LENGTH_LONG).show();

                // Updates UI elements accordingly
                updateDataLoggerUI();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "GET DataLogger/State returned error: " + e);
            }
        });
    }

    private boolean mPathSelectionSetInternally = false;
    private void fetchDataLoggerConfig() {
        // Access the DataLogger/State
        String stateUri = MessageFormat.format(URI_DATALOGGER_CONFIG, connectedSerial);
        mDLConfigPath = null;

        getMDS().get(stateUri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "GET DataLogger/Config successful: " + data);

                DataLoggerConfig config = new Gson().fromJson(data, DataLoggerConfig.class);
//                Spinner spinner = (Spinner)findViewById(R.id.path_spinner);

                // Prevents selection triggering configureDataLogger()
                mPathSelectionSetInternally = true;

//                assert config.content != null;
//                for (DataLoggerConfig.DataEntry de : config.content.dataEntries.dataEntry)
//                {
//                    Log.d(LOG_TAG, "DataEntry: " + de.path);
//                    Toast.makeText(DataLoggerActivity.this, "DataEntry path in fetchDataLoggerConfig(): " + de.path, Toast.LENGTH_LONG).show();
//
//                    String dePath = de.path;
//
//                    if (dePath.contains("{"))
//                    {
//                        dePath = dePath.substring(0,dePath.indexOf('{'));
//                        Log.d(LOG_TAG, "dePath: " + dePath);
//
//                    }
//                    // Start searching for item from 1 since 0 is the default text for empty selection
//                    for (int i=1; i<spinner.getAdapter().getCount(); i++)
//                    {
//                        String path = spinner.getItemAtPosition(i).toString();
//                        Log.d(LOG_TAG, "spinner.path["+ i+"]: " + path);
//                        // Match the beginning (skip the part with samplerate parameter)
////                        if (path.toLowerCase().contains(dePath.toLowerCase()))
////                        {
////                            Log.d(LOG_TAG, "mPathSelectionSetInternally to #"+ i);
////                            Toast.makeText(DataLoggerActivity.this, "mPathSelectionSetInternally to #"+ i, Toast.LENGTH_LONG).show();
////
////                            spinner.setSelection(i);
////                            mDLConfigPath = path;
////                            break;
////                        }
////                        else
//
//                        if (path.toLowerCase().startsWith(dePath.toLowerCase()))
//                        {
//                            mPathSelectionSetInternally = true;
//                            Log.d(LOG_TAG, "mPathSelectionSetInternally to #"+ i);
//                            Toast.makeText(DataLoggerActivity.this, "mPathSelectionSetInternally to #"+ i, Toast.LENGTH_LONG).show();
//
//                            spinner.setSelection(i);
//                            mDLConfigPath = path;
//                            // Toast.makeText(DataLoggerActivity.this, "mDLConfigPath in fetchDataLoggerConfig(): " + mDLConfigPath, Toast.LENGTH_LONG).show();
//                            break;
//                        }
//                    }
//                }
//
////                Toast.makeText(DataLoggerActivity.this, paths, Toast.LENGTH_SHORT).show();

                // If no match found, set to first item (/Meas/IMU9/208)
//                if (mDLConfigPath == null)
//                {
//                    Log.d(LOG_TAG, "no match found, set to first item");
//                    Toast.makeText(DataLoggerActivity.this, "No config match found, set to first item.", Toast.LENGTH_LONG).show();
//
//                    spinner.setSelection(0);
//                }

                // Creating path of current logging path by looping through dataEntries

                assert config.content != null;
                if (config.content.dataEntries.dataEntry != null) {
                    currentConfigPath = ""; // Resets currentConfigPath string if an update is to be made
                    for (DataLoggerConfig.DataEntry de : config.content.dataEntries.dataEntry) {
                        currentConfigPath = currentConfigPath + de.path + delimiter;
                        mDLConfigPath = currentConfigPath;
                    }
//                    Toast.makeText(DataLoggerActivity.this, "Config data in config. Updating currentConfigPath. mDLConfigPath: "
//                            + mDLConfigPath, Toast.LENGTH_LONG).show();
                }
                else {
//                    Toast.makeText(DataLoggerActivity.this, "No config data in config. NOT updating currentConfigPath.", Toast.LENGTH_LONG).show();
                }

                // Fetching DataLogger state
                fetchDataLoggerState();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "GET DataLogger/Config returned error: " + e);
//                Toast.makeText(DataLoggerActivity.this, "GET DataLogger/Config returned error: " + e, Toast.LENGTH_LONG).show();
                fetchDataLoggerState();
            }
        });
    }

    // Actually starts/stops logging
    private void setDataLoggerState(final boolean bStartLogging) {
        // Access the DataLogger/State
        String stateUri = MessageFormat.format(URI_DATALOGGER_STATE, connectedSerial);
        final Context me = this;
        int newState = bStartLogging ? 3 : 2;
        String payload = "{\"newState\":" + newState + "}";
        getMDS().put(stateUri, payload, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "PUT DataLogger/State state successful: " + data);
//                Toast.makeText(DataLoggerActivity.this, "PUT DataLogger/State state successful: "
//                        + newState, Toast.LENGTH_SHORT).show();

                mDLState.content = newState;
                updateDataLoggerUI();
                // Update log list if we stopped
                if (!bStartLogging)
                {
                    refreshLogList();
                    isLogging = false;

                }
                else
                {
                    isLogging = true;
                }

//                Toast.makeText(DataLoggerActivity.this, "isLogging: " + isLogging, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "PUT DataLogger/State returned error: " + e);
//                Toast.makeText(DataLoggerActivity.this, "PUT DataLogger/State returned error: " + e, Toast.LENGTH_LONG).show();

                if (e.getStatusCode()==423 && bStartLogging) {
                    // Handle "LOCKED" from NAND variant
                    new AlertDialog.Builder(me)
                            .setTitle("DataLogger Error")
                            .setMessage("Can't start logging due to error 'locked'. Possibly too low battery on the sensor.")
                            .show();

                }

            }
        });
    }

    public void onStartLoggingClicked(View view) {
        setDataLoggerState(true);
    }

    public void onStopLoggingClicked(View view) {
        setDataLoggerState(false);
    }

    private void refreshLogList() {
        // Access the /Logbook/Entries
        String entriesUri = MessageFormat.format(URI_MDS_LOGBOOK_ENTRIES, connectedSerial);

        getMDS().get(entriesUri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "GET LogEntries successful: " + data);

                MdsLogbookEntriesResponse entriesResponse = new Gson().fromJson(data, MdsLogbookEntriesResponse.class);
                findViewById(R.id.buttonRefreshLogs).setEnabled(true);

                mLogEntriesArrayList.clear();
                for (MdsLogbookEntriesResponse.LogEntry logEntry : entriesResponse.logEntries) {
                    Log.d(LOG_TAG, "Entry: " + logEntry);
                    mLogEntriesArrayList.add(logEntry);
                }
                mLogEntriesArrayAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "GET LogEntries returned error: " + e);
            }
        });
    }

    private MdsLogbookEntriesResponse.LogEntry findLogEntry(final int id)
    {
        MdsLogbookEntriesResponse.LogEntry entry = null;
        for (MdsLogbookEntriesResponse.LogEntry e : mLogEntriesArrayList) {
            if ((e.id == id)) {
                entry = e;
                break;
            }
        }
        return entry;
    }

    public void onRefreshLogsClicked(View view) {
        refreshLogList();
    }

    public void onEraseLogsClicked(View view) {
        AlertDialog.Builder builder;
        builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("Erase Logs")
                .setMessage("Are you sure you want to wipe all logbook entries?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with delete
                        eraseAllLogs();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void eraseAllLogs() {
        // Access the Logbook/Entries resource
        String entriesUri = MessageFormat.format(URI_LOGBOOK_ENTRIES, connectedSerial);

        findViewById(R.id.buttonStartLogging).setEnabled(false);
        findViewById(R.id.buttonStopLogging).setEnabled(false);
        findViewById(R.id.buttonRefreshLogs).setEnabled(false);

        getMDS().delete(entriesUri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "DELETE LogEntries successful: " + data);
                refreshLogList();
                updateDataLoggerUI();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "DELETE LogEntries returned error: " + e);
                refreshLogList();
                updateDataLoggerUI();
            }
        });
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG,"onDestroy()");

        // Leave datalogger logging
        DataLoggerActivity.s_INSTANCE = null;

        super.onDestroy();
    }

    // Run when item selected from spinner
    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        Log.d(LOG_TAG, "Path selected: " + adapterView.getSelectedItem().toString() + ", i: "+ i);

        // if (adapterView.getSelectedItem().toString() == "Select a path...") { return; }

        // Printout about path type
        if (adapterView.getSelectedItem().toString().contains(delimiter)) {
            mDLConfigPath = (i==0) ? null : adapterView.getSelectedItem().toString();
            // Toast.makeText(DataLoggerActivity.this, "List entry selected", Toast.LENGTH_LONG).show();
        }
        else {
            mDLConfigPath = (i==0) ? null : adapterView.getSelectedItem().toString();
            // Toast.makeText(DataLoggerActivity.this, "String entry selected", Toast.LENGTH_LONG).show();
        }

        // MAIN BODY OF FUNCTION
        // If not clicked on spinner, return
        if (adapterView != findViewById(R.id.path_spinner)) { return; }
        // If clicked on spinner but already logging, return
        else {
            mPathSelectionSetInternally = false;

            if (adapterView == findViewById(R.id.path_spinner) && isLogging && adapterView.getSelectedItemPosition()>0) {
//                Toast.makeText(DataLoggerActivity.this, "Not calling configureDataLogger() as logging. isLogging: " + isLogging, Toast.LENGTH_LONG).show();
                Toast.makeText(DataLoggerActivity.this, "Device is currently logging. Stop logging before changing path.", Toast.LENGTH_LONG).show();
                return;
            }

            // If clicked on spinner entry and device is not logging
            else {
                // Only update config if UI selection was not set by the code (result of GET /Config)
                if (//mDLConfigPath != null &&
                        //!mPathSelectionSetInternally &&
                                adapterView.getSelectedItemPosition()>0)
                {

                    Log.d(LOG_TAG, "Calling configureDataLogger:" + mDLConfigPath);
//                    Toast.makeText(DataLoggerActivity.this, "Calling configureDataLogger():" + mDLConfigPath, Toast.LENGTH_SHORT).show();
                    configureDataLogger();
                }
                // If selected the first entry i.e., Select a path...
                else if (adapterView.getSelectedItemPosition()==0) { return; }
                // If error...
                else {
//                    Toast.makeText(DataLoggerActivity.this, "Didn't meet a condition in bottom if/else in onItemSelected(). mPathSelectionSetInternally: "
//                            + mPathSelectionSetInternally + " mDLConfigPath: " + mDLConfigPath, Toast.LENGTH_LONG).show();
                }


            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        Log.i(LOG_TAG, "Nothing selected");
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent != findViewById(R.id.listViewLogbookEntries))
            return;

        MdsLogbookEntriesResponse.LogEntry entry = mLogEntriesArrayList.get(position);


        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this)
                .setTitle("Choose Download Format: Json/RAW")
                .setMessage("Json uses a lot of RAM on phone (may crash if runs out), RAW you need to convert with sbem2json or your own parser afterwards.")
                .setPositiveButton("Json", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                fetchLogEntry(entry.id, false);
                            }
                        }
                )
                .setNeutralButton("RAW", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                fetchLogEntry(entry.id, true);
                            }
                        }
                );
        alertDialogBuilder.show();
    }

    private void fetchLogEntry(final int id, boolean bRAW) {
        findViewById(R.id.headerProgress).setVisibility(View.VISIBLE);

        final MdsLogbookEntriesResponse.LogEntry entry = findLogEntry(id);

        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setIndeterminate(!bRAW);

        if (!bRAW) {
            fetchLogWithMDSProxy(id, entry);
        }
        else {
            fetchLogWithLogbookDataSub(id, entry);
        }
    }

    private MdsSubscription dataSub;
    private boolean bAlreadyLogSaved = false;
    // Gets the logbook as RAW sbem format
    private void fetchLogWithLogbookDataSub(int id, MdsLogbookEntriesResponse.LogEntry entry) {
        // GET the /Mem/Logbook/ direct url
        String logDataResourceUri = MessageFormat.format(URI_LOGBOOK_DATA, id);
        StringBuilder sb = new StringBuilder();
        String strContract = sb.append("{\"Uri\": \"").append(connectedSerial).append(logDataResourceUri).append("\"}").toString();
        Log.d(LOG_TAG, strContract);

        final Context me = this;
        final long logGetStartTimestamp = new Date().getTime();
        final long totalBytes = entry.size;
        dataSub = null;
        bAlreadyLogSaved = false;
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setProgress(0);

        final String filename =new StringBuilder()
                .append("MovesenseLog_").append(id).append(" ")
                .append(entry.getDateStr()).append(".sbem").toString();

        File tempFile = null;
        try {
            tempFile = File.createTempFile("MovesenseSBEMLog", ".sbem", getExternalCacheDir());
            Log.d(LOG_TAG, "tempFile: " + tempFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(LOG_TAG,"Error creating temp file", e);
            return;
        }
        try {
            final FileOutputStream fos = new FileOutputStream(tempFile);

            File finalTempFile = tempFile;
            dataSub = getMDS().subscribe(URI_EVENTLISTENER, strContract, new MdsNotificationListener() {
                private long receivedBytes=0;
                @Override
                public void onNotification(String dataJson) {
                    Log.d(LOG_TAG,"DataNotification Json: " + dataJson);
                    Gson gson = new Gson();
                    Map map = gson.fromJson(dataJson, Map.class);
                    Map body = (Map)map.get("Body");
                    long startOffset = ((Double)body.get("offset")).longValue();
                    ArrayList<Double> dataArray =(ArrayList<Double>) body.get("bytes");
                    if (startOffset > totalBytes)
                    {
                        // Some data was skipped, show error and unsubscribe
                        Log.e(LOG_TAG, "DATA SKIPPED. finishing...");
                        dataSub.unsubscribe();
                        findViewById(R.id.headerProgress).setVisibility(View.GONE);
                    }
                    if (dataArray.size() == 0) {
                        // Close file
                        try {
                            fos.close();
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "Error closing temp file", e);
                        }

                        if (bAlreadyLogSaved)
                            return;

                        // Log end marker. Finish and show save dialog
                        dataSub.unsubscribe();

                        findViewById(R.id.headerProgress).setVisibility(View.GONE);
                        final long logGetEndTimestamp = new Date().getTime();
                        final float speedKBps = (float) entry.size / (logGetEndTimestamp-logGetStartTimestamp) / 1024.0f * 1000.f;
                        Log.i(LOG_TAG, "GET Log Data succesful. size: " + entry.size + ", speed: " + speedKBps);

                        // Building string for message in message box
                        final String message = new StringBuilder()
                                .append("Downloaded log #").append(id).append(" from Movesense ").append(connectedSerial).append(" as RAW.")
                                .append("\n").append("\n").append("Size: ").append(entry.size).append(" bytes")
                                .append("\n").append("Speed: ").append(speedKBps).append(" kB/s")
                                .append("\n").append("\n").append("File will be saved in the location you choose.")
                                .toString();

                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(me)
                                .setTitle("Save Log Data to Device")
                                .setMessage(message).setPositiveButton("Save to Device", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                saveLogToFile_SBEM(finalTempFile, filename);
                                            }
                                        }
                                );

                        findViewById(R.id.headerProgress).setVisibility(View.GONE);
                        alertDialogBuilder.show();

                        bAlreadyLogSaved = true;
                    }
                    else
                    {

                        try {
                            for(Double d : dataArray)
                            {
                                byte b = (byte)d.intValue();
                                fos.write(b);
                            }
                        } catch (IOException e) {
                            Log.e(LOG_TAG,"Error writing data to file", e);
                            dataSub.unsubscribe();
                            findViewById(R.id.headerProgress).setVisibility(View.GONE);
                            return;
                        }
                        receivedBytes += dataArray.size();
                        long percent = receivedBytes * 100 / totalBytes;
                        progressBar.setProgress((int)percent);
                    }
                }

                @Override
                public void onError(MdsException e) {
                    Log.e(LOG_TAG, "GET Log Data returned error: " + e);
                    findViewById(R.id.headerProgress).setVisibility(View.GONE);
                }
            });
        } catch (IOException e) {
            Log.e(LOG_TAG,"Error writing to temp file", e);
            return;
        }
    }

    // COPIED FROM UPDATED DATALOGGER SOURCE CODE v1.4
    private void fetchLogWithMDSProxy(int id, MdsLogbookEntriesResponse.LogEntry entry) {
        // GET the /MDS/Logbook/Data proxy
        String logDataUri = MessageFormat.format(URI_MDS_LOGBOOK_DATA, connectedSerial, id);
        final Context me = this;
        final long logGetStartTimestamp = new Date().getTime();

        final String filename =new StringBuilder()
                .append("MovesenseLog_").append(id).append(" ")
                .append(entry.getDateStr()).append(".json").toString();

        // MDS stores downloaded files in android Files-dir
        final File tempFile = new File(this.getFilesDir(), filename);

        // Use ToFile parameter to save directly to file and do streaming json conversion (saves memory)
        final String strGetLogDataParameters = "{\"ToFile\":\"" + filename + "\"}";

        getMDS().get(logDataUri, strGetLogDataParameters, new MdsResponseListener() {
            @Override
            public void onSuccess(final String data) {
                final long logGetEndTimestamp = new Date().getTime();
                final float speedKBps = (float) entry.size / (logGetEndTimestamp-logGetStartTimestamp) / 1024.0f * 1000.f;
                Log.i(LOG_TAG, "GET Log Data successful. size: " + entry.size + ", speed: " + speedKBps);

                // Building string for message in message box
                final String message = new StringBuilder()
                        .append("Downloaded log #").append(id).append(" from Movesense ").append(connectedSerial).append(" as JSON.")
                        .append("\n").append("\n").append("Size: ").append(entry.size).append(" bytes")
                        .append("\n").append("Speed: ").append(speedKBps).append(" kB/s")
                        .append("\n").append("\n").append("File will be saved in the location you choose.")
                        .toString();

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(me)
                        .setTitle("Save Log Data to Device")
                        .setMessage(message).setPositiveButton("Save to Device", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        saveLogToFile_Json(tempFile, filename);
                                    }
                                }
                        );

                findViewById(R.id.headerProgress).setVisibility(View.GONE);
                alertDialogBuilder.show();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "GET Log Data returned error: " + e);
                Toast.makeText(DataLoggerActivity.this, "In fetchLogEntry(): GET Log Data returned error: " + e, Toast.LENGTH_LONG).show();
                findViewById(R.id.headerProgress).setVisibility(View.GONE);
            }
        });
    }

/*    // OLD VERSION: Gets the log entry to write to file
    private void fetchLogWithMDSProxyOLD(int id, MdsLogbookEntriesResponse.LogEntry entry) {
        findViewById(R.id.headerProgress).setVisibility(View.VISIBLE);
        // GET the /MDS/Logbook/Data proxy
        String logDataUri = MessageFormat.format(URI_MDS_LOGBOOK_DATA, connectedSerial, id);
        // Toast.makeText(DataLoggerActivity.this, "logDataUri: " + logDataUri, Toast.LENGTH_SHORT).show();
        final Context me = this;
        final long logGetStartTimestamp = new Date().getTime();

        // HERE is where it actually triggers pulling data from the sensor i.e., where the issue is.
        getMDS().get(logDataUri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(final String data) {
                // Print to check if data has been retrieved by this point - YES IT HAS
                Toast.makeText(DataLoggerActivity.this, data.substring(0,100), Toast.LENGTH_SHORT).show();

                final long logGetEndTimestamp = new Date().getTime();
                // Actually gets the Logbook entry for checking size, download speed, etc.
                MdsLogbookEntriesResponse.LogEntry entry = findLogEntry(id);
                final float speedKBps = ((float)entry.size / 1024) / ((float)(logGetEndTimestamp-logGetStartTimestamp) / 1000);
                final float downloadTime = (float)(logGetEndTimestamp - logGetStartTimestamp) / 1000;

                // Has downloaded the data from sensor by here
                Log.i(LOG_TAG, "GET Log Data successful. size: " + entry.size + ", speed: " + speedKBps);
                Toast.makeText(DataLoggerActivity.this,
                        "GET Log Data successful. size: " + entry.size + ", speed: " + speedKBps,
                        Toast.LENGTH_SHORT).show();

                // Creating sample of data for presentation in message box
                String snippet;
                if (data.length() > 0) { snippet = data.substring(0,25); }
                else { snippet = "Data variable does not contain any data: " + data.length(); }

                // Building string for message in message box
                final String message = new StringBuilder()
                        .append("Downloaded log #").append(id).append(" from Movesense ").append(connectedSerial).append(".")
                        .append("\n").append("\n").append("Size: ").append(entry.size).append(" bytes")
                        .append("\n").append("Speed: ").append(speedKBps).append(" kB/s")
                        .append("\n").append("Downloaded in: ").append(downloadTime).append(" s")
                        .append("\n").append("Data snippet: ").append(snippet)
                        .append("\n").append("\n").append("File will be saved in the location you choose.")
                        .toString();

                // Building filename
                final String filename = new StringBuilder()
                        .append("RITMO_MovesenseLog_ID-").append(id).append("_")
                        .append(connectedSerial).append("_")
                        .append(entry.getDateStr()).toString();

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(me)
                        .setTitle("Save Log Data to Device")
                        .setMessage(message).setPositiveButton("Save to Device", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        saveLogToFile_Json(filename, data);
                                    }
                                }
                        );

                findViewById(R.id.headerProgress).setVisibility(View.GONE);
                alertDialogBuilder.show();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "GET Log Data returned error: " + e);
                Toast.makeText(DataLoggerActivity.this, "In fetchLogEntry(): GET Log Data returned error: " + e, Toast.LENGTH_LONG).show();
                findViewById(R.id.headerProgress).setVisibility(View.GONE);
            }
        });
    }*/

    private File mDataFileToCopy;
    private static int CREATE_FILE = 1;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == CREATE_FILE
                && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected. The original filename is in mDataFilenameToWriteFile
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                copyFileToFileUri(uri, mDataFileToCopy);
                mDataFileToCopy = null;

            }
        }
    }
    private void copyFileToFileUri(Uri outputUri, File inputFile)
    {
        // Save data to the selected output file
        Log.d(LOG_TAG, "Copying file data from " + inputFile.getAbsolutePath() + " to uri: " + outputUri);

        try
        {
            OutputStream outputStream = getContentResolver().openOutputStream(outputUri);

            InputStream inputStream = new FileInputStream(inputFile);
            // Write in pieces in case the file is big
            final int BLOCK_SIZE= 4096;
            byte buffer[] = new byte[BLOCK_SIZE];
            int length;
            int total=0;
            while((length=inputStream.read(buffer)) > 0) {
                outputStream.write(buffer,0,length);
                total += length;
                Log.d(LOG_TAG, "Bytes written: " + total);
            }

            outputStream.close();
            inputStream.close();

        } catch (IOException e) {
            Log.e(LOG_TAG, "error in creating a file:", e);
            e.printStackTrace();
        }
        finally {
            inputFile.delete();
        }
    }

    private void saveLogToFile_SBEM(File file, String filename) {
        mDataFileToCopy = file;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octetstream");
        intent.putExtra(Intent.EXTRA_TITLE, filename);

        startActivityForResult(intent, CREATE_FILE);
    }

    private void saveLogToFile_Json(File file, String filename) {
        mDataFileToCopy = file;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, filename);

        startActivityForResult(intent, CREATE_FILE);
    }

    public void onCreateNewLogClicked(View view) {
        createNewLog();
    }

    private void createNewLog() {
        // Access the Logbook/Entries resource
        String entriesUri = MessageFormat.format(URI_LOGBOOK_ENTRIES, connectedSerial);

        getMDS().post(entriesUri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "POST LogEntries successful: " + data);
                IntResponse logIdResp = new Gson().fromJson(data, IntResponse.class);

                TextView tvLogId = (TextView)findViewById(R.id.textViewCurrentLogID);
                tvLogId.setText("" + logIdResp.content);
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "POST LogEntries returned error: " + e);
                TextView tvLogId = (TextView)findViewById(R.id.textViewCurrentLogID);
                tvLogId.setText("##");
            }
        });

    }
}
