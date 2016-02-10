package compass.usercompass;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;

public class MyLocationListener implements LocationListener {

    @Override
    public void onLocationChanged(final Location location) {
        Log.i("TEST Location", "TEST Location changed to: " + location.toString());
    }

    public void onProviderDisabled(String provider)
    {
        Log.i("TEST Provider", "TEST Provider disabled" + provider);
    }

    public void onProviderEnabled(String provider)
    {
        Log.i("TEST Provider", "TEST Provider enabled " + provider);
    }

    public void onStatusChanged(String provider, int status, Bundle extras)
    {
        Log.i("TEST Location", "TEST Location status changed to: " + status);
    }
}