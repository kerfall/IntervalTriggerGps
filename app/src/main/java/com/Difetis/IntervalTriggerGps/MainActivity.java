package com.Difetis.IntervalTriggerGps;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;


public class MainActivity extends Activity implements AdapterView.OnItemClickListener {

    // chrono datas
    public final static String APP_PATH_SD_CARD = "/csvIntervalTrigger";
    Button stop;
    long MillisecondTime, StartTime, LapTime = 0L;
    Handler chronoHandler;
    Handler timeHandler;
    int Seconds, Minutes, MilliSeconds, CentiSeconds;
    float lastscreenBrightness;
    ListView lapList;

    List<String> ListLaps;
    List<String> ListElementsArrayList;
    List<String> ListGpsArrayList;
    ArrayAdapter<String> adapter;
    private TextView chrono;
    String _currentDate;

    // gps data
    String _startTime;

    // GPSTracker service class
    GPSTracker gps;

    // callback class to update chrono
    public Runnable runnable = new Runnable() {

        public void run() {

            // update data every second while initializing gps
            int postDelay = 1000;

            // if chrono not started, then we are maybe waiting for gps
            if (StartTime == 0L) {

                if (gps != null) {

                    ListElementsArrayList.clear();
                    String latitude = String.format("Latitude %f", gps.getLatitude());
                    String longitude = String.format("Longitude %f", gps.getLongitude());
                    String accuracy = String.format("Accuracy %f", gps.getAccuracy());
                    //String speed = String.format("Speed %f", gps.getSpeed());
                    //String satellites = String.format("Speed %d", gps.getSatellites());

                    // add gps information to list
                    ListElementsArrayList.add(latitude);
                    ListElementsArrayList.add(longitude);
                    ListElementsArrayList.add(accuracy);
                    //ListElementsArrayList.add(speed);
                    //ListElementsArrayList.add(satellites);

                    adapter.notifyDataSetChanged();

                    if (gps.isGPSReady()) {
                        chrono.setText("Ready");

                    } else {
                        // add an animation effect on waiting for gps
                        String message = chrono.getText().toString();

                        if (message.startsWith("Search") && message.length() < 9) {
                            message += ".";
                        } else {
                            message = "Search";
                        }
                        chrono.setText(message);

                    }
                } else {

                    chrono.setText("GPS Out");
                }
            } else {

                // if GPS service is launched and has changed, we record a trackpoint
                if (gps != null && gps.isGPSUpdated()) {
                    String currentTime = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());

                    String gpsTrckpt = String.format("<trkpt lat=\"%.7f\" lon=\"%.7f\"><time>%sT%sZ</time></trkpt>\\n",
                            gps.getLatitude(),
                            gps.getLongitude(), _currentDate, currentTime);

                    if (ListGpsArrayList != null) {
                        ListGpsArrayList.add(gpsTrckpt);
                    }

                    // don't write the same point twice
                    gps.setGPSUpdated(false);
                }
                MillisecondTime = SystemClock.uptimeMillis() - LapTime;

                DisplayTime(MillisecondTime);

                // update data every 1/100 seconds is enough
                postDelay = 10;
            }


            chronoHandler.postDelayed(this, postDelay);
        }

    };
    private TextView actualTime;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Settings.System.putInt(this.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 0);

        setContentView(R.layout.activity_main);
        chrono = (TextView) findViewById(R.id.Chrono);
        stop = (Button) findViewById(R.id.buttonStop);
        lapList = (ListView) findViewById(R.id.lapList);

        lapList.setOnItemClickListener(this);
        actualTime = (TextView) findViewById(R.id.ActualTime);

        timeHandler = new Handler(getMainLooper());
        timeHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                String displayTime = new SimpleDateFormat("HH:mm").format(new Date());

                // launch a timer that updates every seconds
                actualTime.setText(displayTime);

                // at midnight, udpate current date
                if (_currentDate == null || _currentDate.isEmpty() || displayTime == "00:00") {
                    _currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                }

                timeHandler.postDelayed(this, 1000);
            }
        }, 1000);

        vibrator = (Vibrator) this.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);

        chronoHandler = new Handler();

        ListElementsArrayList = new ArrayList<String>();

        ListGpsArrayList = new ArrayList<String>();

        adapter = new ArrayAdapter<String>(MainActivity.this,
                R.layout.listlayout,
                ListElementsArrayList
        );

        lapList.setAdapter(adapter);

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // if the chrono stopped, we reset it
                if (StartTime == 0L) {

                    LapTime = 0L;
                    MillisecondTime = 0L;
                    Seconds = 0;
                    Minutes = 0;
                    MilliSeconds = 0;

                    chrono.setText("00:00:00");

                    // save laps if any
                    SaveData();

                } else { // otherwise we stop it

                    // save the last lap
                    NewLap();

                    // stop chrono
                    chronoHandler.removeCallbacks(runnable);

                    // display total time
                    MillisecondTime = SystemClock.uptimeMillis() - StartTime;
                    DisplayTime(MillisecondTime);

                    StartTime = 0L;
                    stop.setText("Reset");

                    // no need to keep screen on
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                    // restoring brightness to default
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = lastscreenBrightness;
                    getWindow().setAttributes(lp);

                }

                // short vibration to assert the click was recorded
                vibrator.vibrate(50);


            }
        });


        // create gps service
        gps = new GPSTracker(MainActivity.this);

        // update diplay
        chronoHandler.postDelayed(runnable, 0);

        // keep screen on for gps search
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    }

    public void DisplayTime(long MillisecondTime) {

        Seconds = (int) (MillisecondTime / 1000);

        Minutes = Seconds / 60;

        Seconds = Seconds % 60;

        MilliSeconds = (int) (MillisecondTime % 1000);

        CentiSeconds = MilliSeconds / 10;

        chrono.setText("" + String.format("%02d", Minutes) + ":"
                + String.format("%02d", Seconds) + ":"
                + String.format("%02d", CentiSeconds));
    }

    public void SaveData() {

        if (!ListElementsArrayList.isEmpty()) {
            // generate the path to save csv files
            String csvPath = Environment.getExternalStorageDirectory().getAbsolutePath() + APP_PATH_SD_CARD;

            // create it if it's missing
            File csvDir = new File(csvPath);
            if (!csvDir.exists()) {
                csvDir.mkdirs();
            }

            // save the csv file
            if (csvDir != null) {
                WriteCsvFile(csvDir);
                if (ListGpsArrayList != null && !ListGpsArrayList.isEmpty()) {
                    WriteGpxFile(csvDir);
                }
            }

            Toast.makeText(getApplicationContext(), "File saved", Toast.LENGTH_LONG).show();

            // then empty lap list
            ListElementsArrayList.clear();
            adapter.notifyDataSetChanged();

        }

    }

    public void WriteCsvFile(File sd) {
        if (sd != null) {

            // creating a unique name
            String formattedDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String csvFilePath = "run" + formattedDate + ".csv";

            // creating the file
            File csvFile = new File(sd.getAbsolutePath(), csvFilePath);

            try {

                if (csvFile != null) {

                    int i = 0;
                    String body = "lap;time\n";

                    // foreach lap we add a line
                    for (String lap :
                            ListElementsArrayList) {
                        i++;
                        String line = String.valueOf(i) + ";" + lap + "\n";
                        body += line;
                    }

                    FileWriter writer = new FileWriter(csvFile);
                    if (writer != null) {
                        writer.append(body);
                        writer.flush();
                        writer.close();
                    }

                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

    public void WriteGpxFile(File sd) {

        if (sd != null) {

            // creating a unique name
            String formattedDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String gpxFilePath = "run" + formattedDate + ".gpx";

            // creating the file
            File gpxFile = new File(sd.getAbsolutePath(), gpxFilePath);

            try {

                if (gpxFile != null) {

                    int i = 0;
                    String body = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\\n";
                    body += "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"IntervalTriggerGps\" version=\"1.1\" \\n";
                    body += "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \\n";
                    body += "xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\\n";
                    body += "<metadata>\\n";
                    body += "<name>" + gpxFilePath + "</name>\\n";
                    body += "<link href=\"\">\\n";
                    body += "<text>IntervalTriggerGps</text>\\n";
                    body += "</link>\\n";
                    body += String.format("<time>%s</time>\\n", _startTime);
                    body += "</metadata>\\n";
                    body += "<trk>\\n";
                    body += "<trkseg>\\n";
                    // foreach Trackpoint we add a line
                    for (String gpsTrkpt :
                            ListGpsArrayList) {
                        body += gpsTrkpt;
                    }


                    body += " </trkseg>\\n";
                    body += "</trk>\\n";
                    body += "</gpx>";

                    FileWriter writer = new FileWriter(gpxFile);
                    if (writer != null) {
                        writer.append(body);
                        writer.flush();
                        writer.close();
                    }

                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }


    public void onItemClick(AdapterView<?> l, View v, int position, long id) {

        // if chrono is stopped and listview contains data we save it
        if (StartTime == 0L && !ListElementsArrayList.isEmpty()) {
            SaveData();
        } else { // otherwise we start a new lap
            NewLap();
        }

        // vibration to assert the click was recorded
        vibrator.vibrate(50);

    }

    public void StartChrono() {

        // put brightness to minimum, will be restored on stop
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lastscreenBrightness = lp.screenBrightness;
        lp.screenBrightness = 0;
        getWindow().setAttributes(lp);

        // keep screen on until we stop
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // clear any data present in list
        ListElementsArrayList.clear();
        adapter.notifyDataSetChanged();

        // record start time timestamp
        StartTime = SystemClock.uptimeMillis();
        // laptime and starttime are equal on first lap
        LapTime = StartTime;

        // reset length data
        if (gps != null) {
            gps.setLength(0);
            String currentTime = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());

            _startTime = String.format("%sT%sZ", _currentDate, currentTime);
        }

        // launching callback
        chronoHandler.postDelayed(runnable, 10);
    }

    public void NewLap() {

        // stop callback time to reset the lap
        chronoHandler.removeCallbacks(runnable);

        // display laptime in toast
        Toast.makeText(getApplicationContext(), chrono.getText().toString(), Toast.LENGTH_LONG).show();
        // last lap is added first
        ListElementsArrayList.add(0, chrono.getText().toString());
        adapter.notifyDataSetChanged();

        // resetting laptime and restart callback
        LapTime = SystemClock.uptimeMillis();
        chronoHandler.postDelayed(runnable, 0);

    }

    public void onChronoClick(View v) {


        // check if GPS enabled
        if (gps.canGetLocation()) {

            // if gps is ready we can start running
            if (gps.isGPSReady()) {
                // if chrono is off we start it
                if (StartTime == 0L) {
                    StartChrono();
                } else { // otherwise it's a lap start
                    NewLap();
                }
            }


        } else {
            // can't get location
            // GPS or Network is not enabled
            // Ask user to enable GPS/network in settings
            gps.showSettingsAlert();
        }


        // vibration to assert the click was recorded
        vibrator.vibrate(50);

        stop.setText("Stop");
    }

    /* Checks if external storage is available for read and write */
    // move this class to a static utilities class
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    @Override
    protected void onDestroy() {

        gps.stopUsingGPS();
        gps.onDestroy();

        // we have to remove callbacks before exiting
        chronoHandler.removeCallbacks(runnable);
        timeHandler.removeCallbacks(null);
        super.onDestroy();


    }


}
