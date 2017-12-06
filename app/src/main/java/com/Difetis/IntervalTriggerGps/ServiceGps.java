package com.Difetis.IntervalTriggerGps;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class ServiceGps extends Service {

    protected static final int ACTIVE = 0;
    protected static final int PAUSED = 1;
    protected static final int INIT = 2;
    protected static final int D_RUN = 1;
    protected static final int D_NORUN = 0;
    protected static final int D_GPSOFF = 2;
    boolean m_bLog = false;
    Myobjlistener m_gpsListener;
    LocationManager locationManager = null;
    int m_iRefreshFreq = 5; // en m
    String m_kmlfile;
    String m_logfile;
    int m_iRun = 0;
    boolean m_bReload = false;
    protected boolean m_bPositionGPS = false;
    protected double m_dParcours = 0; // le parcours � effectuer en m
    int m_iStep = 5;
    int m_iLengthUnit = 1; // km
    int m_iSpeedUnit = 1; // km/h
    int m_iIntervalle = 1000; // tous les 1000 m
    int m_iLangue = 0; // langue de l'application par d�faut
    int m_iState = INIT;
    private Locale Applocale = null;
    ArrayList<String> m_csarPhoneNumber = new ArrayList<String>();
    protected boolean m_bNotifVisu = true;
    double _dGravityVector = 0;

    private static boolean isRunning = false;
    ArrayList<Messenger> mClients = new ArrayList<Messenger>(); // Keeps track of all current registered clients.
    int mValue = 0; // Holds last value set by a client.
    static final int MSG_REGISTER_CLIENT = 1;
    static final int MSG_UNREGISTER_CLIENT = 2;
    static final int MSG_SET_PARCOURS = 3;
    static final int MSG_SET_RUN = 4;
    static final int MSG_SET_STOP = 5;
    static final int MSG_GET_LONGUEUR = 6;
    static final int MSG_GET_GPSSTATUS = 7;
    final Messenger mMessenger = new Messenger(new IncomingHandler()); // Target we publish for clients to send messages to IncomingHandler.


    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    class IncomingHandler extends Handler { // Handler of incoming messages from clients.
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                case MSG_SET_PARCOURS:

                    break;
                case MSG_SET_RUN:

                    break;
                case MSG_SET_STOP:

                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private void sendDisplayToUI(int intvaluetosend) {
        for (int i = mClients.size() - 1; i >= 0; i--) {
            try {
                // Send data as an Integer
                //mClients.get(i).send(Message.obtain(null, MSG_SET_INT_VALUE, intvaluetosend, 0));

                //Send data as a String
                double dDisplayLength = Utilities.MetreToDisplay(m_iLengthUnit, m_gpsListener.m_longueur);
                String csUnit = Utilities.GetUnit(getResources(), m_iLengthUnit);

                String csLongueur = String.format(Locale.FRANCE, "%.2f %s", dDisplayLength, csUnit);
                Bundle b = new Bundle();
                b.putString("str1", csLongueur);
                Message msg = Message.obtain(null, MSG_GET_LONGUEUR);
                msg.setData(b);
                mClients.get(i).send(msg);

            } catch (RemoteException e) {
                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
    }

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Toast.makeText(this, "Service Created", 300).show();

        //*************ecouteur ou listener*********************
        m_gpsListener = new Myobjlistener();

        //--- on d�marre la localisation avec l'application
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if(checkPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {

            locationManager.addGpsStatusListener(m_gpsListener);

            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    m_iRefreshFreq * 1000,
                    5,
                    m_gpsListener);

        }
    }

    @Override
    public void onDestroy() {
        // si la course est toujours en route, on pr�vient que l'application s'arr�te

        // on ferme les fichiers avant destruction
        CloseLogFiles();

        super.onDestroy();

        Toast.makeText(this, "Service Destroy", 300).show();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Toast.makeText(this, "Service LowMemory", 300).show();
    }

    @Override
    public void onStart(Intent intent, int startId) {

        Toast.makeText(this, "Service start", 300).show();
        Notification notification = new Notification(R.drawable.icon,
                "Rolling text on statusbar", System.currentTimeMillis());

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, Main.class), PendingIntent.FLAG_UPDATE_CURRENT);


        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Toast.makeText(this, "task perform in service", 300).show();


        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, Main.class), PendingIntent.FLAG_UPDATE_CURRENT);

        return super.onStartCommand(intent, flags, startId);
    }


    private String FormatTime(int iTime) {

        int iHour = 0, iMinute = 0, iSeconde = 0;
        String csTime = "";

        // on converti ce temps en heure, minute, seconde
        iHour = iTime / 3600;

        iMinute = (iTime - (iHour * 3600)) / 60;

        iSeconde = iTime - (iHour * 3600) - (iMinute * 60);

        if (iHour > 0) {
            csTime = String.format(Applocale, " %d h", iHour);
        }
        if (iMinute > 0) {
            csTime += String.format(Applocale, " %d mn", iMinute);

        }
        if (iSeconde > 0) {
            csTime += String.format(Applocale, " %d s", iSeconde);
        }

        return csTime;
    }


    public boolean CloseLogFiles() {

        boolean bClose = false;
        long lTime = System.currentTimeMillis() / 1000;

        // on ferme le fichier kml
        bClose = CloseKml(lTime);

        bClose = CloseLog(lTime);

        return bClose;

    }


    public boolean CloseKml(long lTime) {

        boolean bClose = false;

        if (m_kmlfile != null && !m_kmlfile.isEmpty()) {
            m_kmlfile += "</coordinates></LineString></Placemark></Document></kml>";

            // puis on l'�crit sur la carte sd
            String kmlname = String.format(Applocale, "log%d.kml", lTime);
            File kmlfile = new File(Environment.getExternalStorageDirectory(), kmlname);
            FileOutputStream kmlfos;
            byte[] kmldata = m_kmlfile.getBytes();
            try {
                kmlfos = new FileOutputStream(kmlfile);
                kmlfos.write(kmldata);
                kmlfos.flush();
                kmlfos.close();

                bClose = true;
            } catch (FileNotFoundException e) {
                // handle exception
            } catch (IOException e) {
                // handle exception
            }
        }

        return bClose;
    }


    public boolean CloseLog(long lTime) {

        boolean bClose = false;

        if (m_logfile != null && !m_logfile.isEmpty()) {
            // puis on l'�crit sur la carte sd
            String filename = String.format(Applocale, "log%d.log", lTime);
            File file = new File(Environment.getExternalStorageDirectory(), filename);
            FileOutputStream fos;
            byte[] data = m_logfile.getBytes();
            try {
                fos = new FileOutputStream(file);
                fos.write(data);
                fos.flush();
                fos.close();
                bClose = true;
            } catch (FileNotFoundException e) {
                // handle exception
            } catch (IOException e) {
                // handle exception
            }

        }

        return bClose;
    }


    ///////////////////////// Sous classe dialogClickListener //////////////////
    private class Myobjlistener implements LocationListener, GpsStatus.Listener {
        public Double m_longueur = 0.0;
        private static final int MY_NOTIFICATION_ID = 1;
        private NotificationManager notificationManager;
        private Notification myNotification;
        Location m_OldLocation = null;
        public static final int OUT_OF_SERVICE = 0;
        public static final int TEMPORARILY_UNAVAILABLE = 1;
        public static final int AVAILABLE = 2;
        public ArrayList<Integer> m_iarStep = new ArrayList<Integer>();
        protected int m_iLastIntervalle = 0;
        protected int m_iGpsStatus = 0;
        protected int m_iStatus = 0;
        protected double m_dLattitude = 0;
        protected double m_dLongitude = 0;
        protected long _lFirstTime = 0;
        protected long _lLastTime = 0;
        private GpsStatus mStatus;
        private int m_iNbSat = 0;
        //protected double m_dOldLongeur = 0;

        public void setLongueur(Double longueur) {
            m_longueur = longueur;
        }

        @Override
        public void onProviderDisabled(String provider) {

            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                //On r�cup�re le bouton cr�� en XML gr�ce � son id
                // a remettre dans l'activity
                /*Button btnEnvoie = (Button)findViewById(R.id.envoyer);
		        
		        // mettre le texte "gps inactif"
		        if(btnEnvoie != null){
		        	btnEnvoie.setText(R.string.gpsisoff);
		        	m_iRun = D_GPSOFF;
		        }*/

            }
        }


        @Override
        public void onProviderEnabled(String provider) {

            if (provider.equals(LocationManager.GPS_PROVIDER)) {

                // a remettre dans l'activity
        		/*Button btnEnvoie = (Button)findViewById(R.id.envoyer);
		        
		        if(btnEnvoie != null){
		        	btnEnvoie.setText(R.string.envoyer);
		        	m_iRun = D_NORUN;
		        }*/

            }
        }


        @Override
        public void onStatusChanged(String provider, int status,
                                    Bundle extras) {

            m_iStatus = status;

            switch (status) {
                case OUT_OF_SERVICE:
                    Toast.makeText(ServiceGps.this, "outofservice", Toast.LENGTH_SHORT).show();
                    break;
                case TEMPORARILY_UNAVAILABLE:
                    Toast.makeText(ServiceGps.this, "temporaryunavailable", Toast.LENGTH_SHORT).show();
                    break;
                case AVAILABLE:
                    Toast.makeText(ServiceGps.this, "available", Toast.LENGTH_SHORT).show();
                    break;
            }

        }

        public void onGpsStatusChanged(int event) {
            mStatus = locationManager.getGpsStatus(mStatus);
            String csMessage = null;

            m_iGpsStatus = event;

            switch (event) {
                case GpsStatus.GPS_EVENT_STARTED:
                    // Do Something with mStatus info
                    csMessage = "GPS d�marr�";
                    break;

                case GpsStatus.GPS_EVENT_STOPPED:
                    // Do Something with mStatus info
                    csMessage = "GPS arret�";
                    break;

                case GpsStatus.GPS_EVENT_FIRST_FIX:
                    // Do Something with mStatus info
                    int iTime = mStatus.getTimeToFirstFix();

                    csMessage = String.format("FirstFix en %d ms", iTime);
                    break;

                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    // Do Something with mStatus info

                    // on remet a jour le nombre de sattelites
                    m_iNbSat = 0;

                    final Iterator<GpsSatellite> it = mStatus.getSatellites().iterator();
                    while (it.hasNext()) {
                        it.next();
                        m_iNbSat += 1;
                    }
                    ;


                    break;

            }

            if (csMessage != null) {
                Toast.makeText(ServiceGps.this, csMessage, Toast.LENGTH_SHORT).show();
            }
        }


        @Override
        public void onLocationChanged(Location location) {

            try {

                if (location == null) return;

                // si le fix provient d'autre chose que le gps, on ne prends pas
                if (!location.getProvider().equals(LocationManager.GPS_PROVIDER)) {

                    return;
                }

                double dLongueur = 0, dDisplayLength = 0;
                String csUnit;
            
         	/*if(m_iGpsStatus != AVAILABLE){
         		//On affiche un petit message d'erreur dans un Toast
				//Toast.makeText(Main.this, m_iGpsStatus, Toast.LENGTH_SHORT).show();
         	}*/

                // pour la premi�re donn�e , on initialise location et on sort
                if (m_OldLocation == null) {

                    // on ferme la dlg d'attente du gps
                    // a remettre dans l'activity
    	        /*Button btnEnvoie = (Button)findViewById(R.id.envoyer);
    	        
    	        if(btnEnvoie != null){
    	        	btnEnvoie.setText(R.string.run);
    	        }*/

                    m_OldLocation = location;
                    return;

                }

                // si l'utilisateur n'a pas appuy� sur start, on enregistre pas mais on met � jour la derni�re localisation pour le red�marrage
                if (m_iRun != D_RUN) {
                    m_OldLocation = location;
                    return;
                }

                if (m_OldLocation != null) {
                    dLongueur = location.distanceTo(m_OldLocation);
                }

                if (m_kmlfile == null || m_kmlfile.isEmpty()) {


                    // on cr�e le fichier kml
                    m_kmlfile = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>	<kml xmlns=\"http://www.opengis.net/kml/2.2\">\r\n";
                    m_kmlfile += "<Document><name>Mon parcours</name><description>date du jour</description>\r\n";
                    m_kmlfile += "<Style id=\"yellowLineGreenPoly\"><LineStyle> <color>7f00ffff</color>\r\n";
                    m_kmlfile += "<width>4</width> </LineStyle><PolyStyle><color>7f00ff00</color></PolyStyle>\r\n";
                    m_kmlfile += "</Style><Placemark><name>parcours du jour</name>\r\n";
                    m_kmlfile += "<description>Parcours genere par folome</description>\r\n";
                    m_kmlfile += "<styleUrl>#yellowLineGreenPoly</styleUrl><LineString><extrude>1</extrude>\r\n";
                    m_kmlfile += "<tessellate>1</tessellate><altitudeMode>absolute</altitudeMode><coordinates>\r\n";

                }

                if (m_logfile == null || m_logfile.isEmpty()) {
                    m_logfile = "longitude,latitude,longueur,status,precision,vitesse,nb satellites,statut gps,acceleration\r\n";
                }


                // on ne prends pas en compte si la longueur est nulle
                if (dLongueur > 0) {
                    m_longueur += Math.abs(dLongueur);
                } else {
                    return;

                }
            
            /*if (location.getAccuracy() > 50 && location.hasAccuracy()){
            	locationManager.removeUpdates(this);
            }*/

                // on stocke la derni�re longueur connue pour �viter les valeurs fantaisistes
                //m_dOldLongeur = dLongueur;

                // on r�cup�re la lattitude et la longitude
                if (m_bPositionGPS == true) {
                    m_dLattitude = location.getLatitude();
                    m_dLongitude = location.getLongitude();
                }

                // on stocke le trajet dans un fichier kml
                DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols();
                otherSymbols.setDecimalSeparator('.');
                DecimalFormat df = new DecimalFormat("#.######", otherSymbols);

                if (m_kmlfile != null || !m_kmlfile.isEmpty()) {
                    m_kmlfile += String.format("%s,%s\r\n", df.format(location.getLongitude()), df.format(location.getLatitude()));
                }

                if (m_logfile != null || !m_logfile.isEmpty()) {

                    m_logfile += String.format("%s,%s", df.format(location.getLongitude()), df.format(location.getLatitude()));

                    // on log aussi la longueur pour voir si elle reste coh�rente
                    if (dLongueur > 0) {
                        m_logfile += String.format(",%s", df.format(dLongueur));

                    }

                    // on log le status du gps
                    if (m_iStatus >= 0) {
                        m_logfile += String.format(",%d", m_iStatus);

                    }


                    // si on a une pr�cision, on la log
                    if (location.hasAccuracy()) {
                        m_logfile += String.format(",%s", df.format(location.getAccuracy()));
                    }

                    if (location.hasSpeed()) {
                        m_logfile += String.format(",%s", df.format(location.getSpeed()));
                    }


                    if (m_iNbSat > 0) {
                        m_logfile += String.format(",%d", m_iNbSat);

                    }

                    if (m_iGpsStatus > 0) {
                        m_logfile += String.format(",%d", m_iGpsStatus);

                    }

                    if (_dGravityVector > 0) {
                        m_logfile += String.format(",%.2f", _dGravityVector);

                    }


                    m_logfile += "\r\n";
                }

                // a remettre dans l'activity
			/*
            TextView Txt = (TextView)findViewById(R.id.length);
            EditText edDistance = (EditText)findViewById(R.id.EdDistance);
            String csParcours = edDistance.getText().toString();
            
            if(!csParcours.isEmpty()){
	            // on r�cup�re la distance � parcourir
	            double dParcours = Double.parseDouble(csParcours);
	            
	            m_dParcours = Utilities.DisplayToMetre(m_iLengthUnit,dParcours); 
            }
            
            dDisplayLength = Utilities.MetreToDisplay(m_iLengthUnit,m_longueur );
            csUnit = Utilities.GetUnit(getResources(),m_iLengthUnit);

            String csLongueur = String.format(Locale.FRANCE,"%.2f %s", dDisplayLength, csUnit);
            Txt.setText(csLongueur);    */

                m_OldLocation = location;

                int iStep = (int) (m_longueur / m_iIntervalle);

                // on envoie une seule fois le message pour l'intervalle donn�
                if (!m_iarStep.contains(iStep)) {


                    m_iarStep.add(iStep);
                }

            } catch (Exception e) {
                Utilities.writeIntoLog(Log.getStackTraceString(e));
            }

        }


    }


}
