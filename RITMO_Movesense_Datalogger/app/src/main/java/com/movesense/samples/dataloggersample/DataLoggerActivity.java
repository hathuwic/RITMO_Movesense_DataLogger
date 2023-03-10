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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsResponseListener;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
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

    static DataLoggerActivity s_INSTANCE = null;
    private static final String LOG_TAG = DataLoggerActivity.class.getSimpleName();

    public static final String SERIAL = "serial";
    String connectedSerial;

    public DataLoggerState mDLState;
    private String mDLConfigPath;
    private TextView mDataLoggerStateTextView;

    // The delimiter between different addresses when recording multiple streams
    private static final String delimiter = ":";
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
                      new DataLoggerConfig.DataEntry(mDLConfigPath.substring(mDLConfigPath.indexOf(delimiter) + 1))};
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
        fetchLogEntry(entry.id);
    }

    // Gets the log entry to write to file
    private void fetchLogEntry(final int id) {
        findViewById(R.id.headerProgress).setVisibility(View.VISIBLE);
        // GET the /MDS/Logbook/Data proxy
        String logDataUri = MessageFormat.format(URI_MDS_LOGBOOK_DATA, connectedSerial, id);
        final Context me = this;
        final long logGetStartTimestamp = new Date().getTime();

        getMDS().get(logDataUri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(final String data) {
                final long logGetEndTimestamp = new Date().getTime();
                // Actually gets the Logbook entry
                MdsLogbookEntriesResponse.LogEntry entry = findLogEntry(id);
                final float speedKBps = (float)entry.size / (logGetEndTimestamp-logGetStartTimestamp) / 1024.0f * 1000.f;

                Log.i(LOG_TAG, "GET Log Data successful. size: " + entry.size + ", speed: " + speedKBps);
//                Toast.makeText(DataLoggerActivity.this,
//                        "GET Log Data successful. size: " + entry.size + ", speed: " + speedKBps,
//                        Toast.LENGTH_SHORT).show();

                // Creating sample of data for presentation in message box
                String snippet;
                if (data.length() > 0) { snippet = data.substring(0,25); }
                else { snippet = "Data does not contain data: " + data.length(); }

                // Building string for message in message box
                final String message = new StringBuilder()
                        .append("Downloaded log #").append(id).append(" from Movesense ").append(connectedSerial).append(".")
                        .append("\n").append("Size: ").append(entry.size).append(" bytes")
                        .append("\n").append("Speed: ").append(speedKBps).append(" kB/s")
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
                                        saveLogToFile(filename, data);
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

    private String mDataToWriteFile;
    private static int CREATE_FILE = 1;
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == CREATE_FILE
                && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            Uri uri = null;
            if (resultData != null) {
//                Toast.makeText(DataLoggerActivity.this, "resultData != null: " + resultData, Toast.LENGTH_SHORT).show();
                uri = resultData.getData();
                writeDataToFile(uri, mDataToWriteFile);
                mDataToWriteFile = null;
            }
        }
    }
    private void writeDataToFile(Uri uri, String data)
    {
        // Save data to the file
        Log.d(LOG_TAG, "Writing data to uri: " + uri);
        // Toast.makeText(DataLoggerActivity.this, "Writing data to uri: " + uri, Toast.LENGTH_LONG).show();
        // Toast.makeText(DataLoggerActivity.this, data.substring(0,25), Toast.LENGTH_LONG).show();

        try
        {
            OutputStream out = getContentResolver().openOutputStream(uri);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(out);

            // Write in pieces in case the file is big
            final int BLOCK_SIZE= 4096;
            for (int startIdx=0;startIdx<data.length();startIdx+=BLOCK_SIZE) {
                int endIdx = Math.min(data.length(), startIdx + BLOCK_SIZE);
                myOutWriter.write(data.substring(startIdx, endIdx));
            }

            myOutWriter.flush();
            myOutWriter.close();

            out.flush();
            out.close();
        }
        catch (IOException e)
        {
            Log.e(LOG_TAG, "File write failed: ", e);
        }

        // re-scan files so that they get visible in Windows
        //MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
    }

    private void saveLogToFile(String filename, String data) {
        mDataToWriteFile = data;
//        Toast.makeText(DataLoggerActivity.this, "mDataToWriteFile in saveLogToFile(): " + mDataToWriteFile.substring(0,100), Toast.LENGTH_SHORT).show();
        // Add extension to filename if it doesn't have yet
        if (!filename.endsWith(".json"))
        {
            filename = filename + ".json";
        }

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
