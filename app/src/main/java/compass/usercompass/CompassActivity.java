package compass.usercompass;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

public class CompassActivity extends AppCompatActivity implements SensorEventListener {
    public LocationManager mLocationManager;
    private MyLocationListener mLocationListener;
    public Location userLastLocation;
    public Location targetLocation;
    public Location currentLocation;
    private EditText longitude;
    private EditText latitude;
    public static final String FIXED = "FIXED";
    private static final int LOCATION_MIN_TIME = 30 * 1000;
    private static final int LOCATION_MIN_DISTANCE = 10;
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private float[] orientation = new float[3];
    private float[] rotation = new float [9];
    private float[] smoothed = new float [3];
    private SensorManager sensorManager;
    private Sensor sensorMagnetic;
    private Sensor sensorGravity;
    private GeomagneticField geomagneticField;
    private double bearing = 0;
    private NeedleView needleView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compass);

        // create target location
        targetLocation = new Location("");
        targetLocation.setLatitude(0);
        targetLocation.setLongitude(0);

        // create rotating view
        needleView = (NeedleView) findViewById(R.id.needle);

        // add flag to keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // create latitude and longitude EditTexts with listeners
        longitude = (EditText) findViewById(R.id.LONGITUDE);
        longitude.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //nop
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //nop
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if ( longitude.getText() != null && longitude.getText().toString().length() != 0 ) {
                    targetLocation.setLongitude(Double.parseDouble(longitude.getText().toString()));
                }
            }
        });
        latitude = (EditText) findViewById(R.id.LATITUDE);
        longitude.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //nop
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //nop
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if ( latitude.getText() != null && latitude.getText().toString().length() != 0 ) {
                    targetLocation.setLatitude(Double.parseDouble(latitude.getText().toString()));
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        // initialization
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorGravity = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMagnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mLocationListener = new MyLocationListener();
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_MIN_TIME,
                LOCATION_MIN_DISTANCE,
                mLocationListener);

        // register listeners
        sensorManager.registerListener(this, sensorGravity, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, sensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);

        // retrieve current location
        mLocationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, mLocationListener, Looper.getMainLooper());
        userLastLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        if (userLastLocation != null) {
            currentLocation = userLastLocation;
        } else {
            // try with network provider
            Location networkLocation = mLocationManager
                    .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (networkLocation != null) {
                currentLocation = networkLocation;
            } else {
                currentLocation = new Location(FIXED);
                currentLocation.setAltitude(1);
                currentLocation.setLatitude(1);
                currentLocation.setLongitude(1);
            }

            onLocationChanged(currentLocation);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        // stop listening
        sensorManager.unregisterListener(this, sensorGravity);
        sensorManager.unregisterListener(this, sensorMagnetic);
        mLocationManager.removeUpdates(mLocationListener);
    }

    public void onLocationChanged(Location location) {
        geomagneticField = new GeomagneticField(
                (float) location.getLatitude(),
                (float) location.getLongitude(),
                (float) location.getAltitude(),
                System.currentTimeMillis());
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean accelOrMagnetic = false;

        // get accelerometer data
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // use low pass filter to make data smoothed
            smoothed = LowPassFilter.filter(event.values, gravity);
            gravity[0] = smoothed[0];
            gravity[1] = smoothed[1];
            gravity[2] = smoothed[2];
            accelOrMagnetic = true;

        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            smoothed = LowPassFilter.filter(event.values, geomagnetic);
            geomagnetic[0] = smoothed[0];
            geomagnetic[1] = smoothed[1];
            geomagnetic[2] = smoothed[2];
            accelOrMagnetic = true;

        }

        // set matrix with gravity and magnetic data
        SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic);
        // get orientation
        SensorManager.getOrientation(rotation, orientation);
        // east degrees of true North
        bearing = orientation[0];
        // convert from radians to degrees
        bearing = Math.toDegrees(bearing);

        // fix difference between true North and magnetic North
        if (geomagneticField != null) {
            bearing += geomagneticField.getDeclination();
        }

        // calculate bearing according to target location
        if (targetLocation.getLatitude() != 0 && targetLocation.getLongitude() != 0){
            bearing = currentLocation.bearingTo(targetLocation) - bearing;
        }

        // bearing must be in 0-360
        if (bearing < 0) {
            bearing += 360;
        }

        // update compass view
        needleView.setBearing((float) bearing);

        if (accelOrMagnetic) {
            needleView.postInvalidate();
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD
                && accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            // nop
        }
    }
}

