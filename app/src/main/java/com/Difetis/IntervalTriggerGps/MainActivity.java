package com.Difetis.IntervalTriggerGps;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
    public final static String APP_PATH_SD_CARD = "/csvIntervalTrigger";
    Button stop;
    long MillisecondTime, StartTime, TimeBuff, LapTime, UpdateTime = 0L;
    Handler chronoHandler;
    Handler timeHandler;
    int Seconds, Minutes, MilliSeconds, CentiSeconds;
    float lastscreenBrightness;
    ListView lapList;
    String[] ListElements = new String[]{};
    List<String> ListElementsArrayList;
    ArrayAdapter<String> adapter;
    private TextView chrono;

    protected static final int ACTIVE = 0;
    protected static final int PAUSED = 1;
    protected static final int INIT = 2;
    protected static final int D_RUN = 1;
    protected static final int D_NORUN = 0;
    protected static final int D_GPSOFF = 2;
    boolean	m_bLog = false;
    int	m_iRun = 0;
    boolean m_bReload = false;
    protected boolean m_bPositionGPS = false;
    protected double	m_dParcours = 0; // le parcours à effectuer en m
    int m_iStep = 5;
    int	m_iLengthUnit = 1; // km
    int m_iRefreshFreq = 5;
    int	m_iSpeedUnit = 1; // km/h
    int m_iIntervalle = 1000; // tous les 1000 m
    int m_iLangue = 0; // langue de l'application par défaut
    int	m_iState = INIT;
    private Intent intentGps;
    double _dGravityVector = 0;

    Messenger mService = null;
    boolean mIsBound;
    final Messenger mMessenger = new Messenger(new IncomingHandler());


    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ServiceGps.MSG_GET_LONGUEUR:
                    break;
                case ServiceGps.MSG_GET_GPSSTATUS:
                    String str1 = msg.getData().getString("str1");
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    // callback class to update chrono
    public Runnable runnable = new Runnable() {

        public void run() {

            MillisecondTime = SystemClock.uptimeMillis() - LapTime;

            UpdateTime = TimeBuff + MillisecondTime;

            Seconds = (int) (UpdateTime / 1000);

            Minutes = Seconds / 60;

            Seconds = Seconds % 60;

            MilliSeconds = (int) (UpdateTime % 1000);

            CentiSeconds = MilliSeconds / 10;

            chrono.setText("" + String.format("%02d", Minutes) + ":"
                    + String.format("%02d", Seconds) + ":"
                    + String.format("%02d", CentiSeconds));

            chronoHandler.postDelayed(this, 0);
        }

    };
    private TextView actualTime;
    private Vibrator vibrator;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Settings.System.putInt(this.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 0);

        if(CheckIfServiceIsRunning() == false){
            // on demarre le service dès la création pour faire le fix
            intentGps=new Intent(this,ServiceGps.class);
            startService(intentGps);
        }

        m_iRun = D_NORUN;

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
                actualTime.setText(new SimpleDateFormat("HH:mm").format(new Date()));
                timeHandler.postDelayed(this, 1000);
            }
        }, 10);

        vibrator = (Vibrator) this.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);

        chronoHandler = new Handler();

        ListElementsArrayList = new ArrayList<String>(Arrays.asList(ListElements));

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
                    TimeBuff = 0L;
                    UpdateTime = 0L;
                    Seconds = 0;
                    Minutes = 0;
                    MilliSeconds = 0;

                    chrono.setText("00:00:00");

                    // save laps if any
                    SaveData();

                } else { // otherwise we stop it

                    TimeBuff += MillisecondTime;
                    chronoHandler.removeCallbacks(runnable);
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


    }


    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            try {
                Message msg = Message.obtain(null, ServiceGps.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
            mService = null;
        }
    };

    private boolean CheckIfServiceIsRunning() {

        boolean bServiceRunning = true;

        //If the service is running when the activity starts, we want to automatically bind to it.
        if (ServiceGps.isRunning()) {
            doBindService();
        }else{
            bServiceRunning = false;
        }

        return bServiceRunning;
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

        // record start time timestamp
        StartTime = SystemClock.uptimeMillis();
        // laptime and starttime are equal on first lap
        LapTime = StartTime;
        // launching callback
        chronoHandler.postDelayed(runnable, 0);
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

        // if chrono is off we start it
        if (StartTime == 0L) {
            StartChrono();
        } else { // otherwise it's a lap start
            NewLap();
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

        // we have to remove callbacks before exiting
        chronoHandler.removeCallbacks(runnable);
        timeHandler.removeCallbacks(null);
        super.onDestroy();


    }



}
