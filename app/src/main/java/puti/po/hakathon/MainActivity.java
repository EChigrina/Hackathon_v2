package puti.po.hakathon;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.Image;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapFragment;
import com.here.android.mpa.mapping.MapMarker;
import com.here.android.mpa.mapping.MapState;
import com.here.android.mpa.mapping.PositionIndicator;
import com.here.android.mpa.search.ErrorCode;
import com.here.android.mpa.search.GeocodeRequest;
import com.here.android.mpa.search.GeocodeRequest2;
import com.here.android.mpa.search.GeocodeResult;
import com.here.android.mpa.search.ResultListener;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PositioningManager.OnPositionChangedListener{

    final static int REQUEST_CODE_ASK_PERMISSIONS = 1;

    static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    Map map;
    MapFragment mapFragment;

    // positioning manager instance
    PositioningManager mPositioningManager;

    EditText etFindPlace;
    GeoPosition currentPosition = null;
    MapMarker currentLocationMarker = null;
    Context context;
    WiFiClient WiFiClient;
    SharedPreferences sPref;
    BroadcastReceiver newDriverBroadcastReceiver;
    BroadcastReceiver newPassengerBroadcastReceiver;
    BroadcastReceiver connectedBroadcastReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);        checkPermissions();
    }

    private void initialize() {
        mapFragment = (MapFragment)
                getFragmentManager().findFragmentById(R.id.mapfragment);
        context = this;
        etFindPlace = findViewById(R.id.etFindPlace);
        etFindPlace.setOnKeyListener(new View.OnKeyListener()
              {
                  public boolean onKey(View v, int keyCode, KeyEvent event)
                  {
                      if(event.getAction() == KeyEvent.ACTION_DOWN &&
                              (keyCode == KeyEvent.KEYCODE_ENTER))
                      {
                            geoCodeRequest(etFindPlace.getText().toString());
                          return true;
                      }
                      return false;
                  }
              });
        sPref = getPreferences(MODE_PRIVATE);
        if(sPref.contains("IP")) {
            String ip = sPref.getString("IP", "");
            WiFiClient = new WiFiClient(this, ip);
            Toast.makeText(this, ip, Toast.LENGTH_SHORT).show();
        }
        connectToWiFiNetwork();
        mapFragment.init(new OnEngineInitListener() {
            @Override
            public void onEngineInitializationCompleted(OnEngineInitListener.Error error) {
                if (error == OnEngineInitListener.Error.NONE) {
                    // retrieve a reference of the map from the map fragment
                    map = mapFragment.getMap();
                    // Set the map center coordinate to the Vancouver region (no animation)
                    map.setCenter(new GeoCoordinate(49.196261, -123.004773, 0.0),
                            Map.Animation.NONE);
                    // Set the map zoom level to the average between min and max (no animation)
                    map.setZoomLevel((map.getMaxZoomLevel() + map.getMinZoomLevel()) / 2);
                    mPositioningManager = PositioningManager.getInstance();
                    mPositioningManager.addListener(new WeakReference<PositioningManager.OnPositionChangedListener>(
                            MainActivity.this));
                    // start position updates, accepting GPS, network or indoor positions
                    if (mPositioningManager.start(PositioningManager.LocationMethod.GPS_NETWORK)) {
                        //mapFragment.getPositionIndicator().setVisible(true);


                        Toast.makeText(MainActivity.this, "PositioningManager.start", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "PositioningManager.start: failed, exiting", Toast.LENGTH_LONG).show();
                        finish();
                    }
                } else {
                    Log.e("ERROR", "Cannot initialize MapFragment (" + error.toString() + ")");
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.miEnterIP:
                AlertDialog.Builder alert = new AlertDialog.Builder(this);

                alert.setTitle("Введите IP-адрес сервера");
                final EditText input = new EditText(this);
                alert.setView(input);

                alert.setPositiveButton("Принять", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String value = input.getText().toString();
                        if(WiFiClient == null || WiFiClient.connected == false) {
                            WiFiClient = new WiFiClient(context, value);
                            WiFiClient.run();
                            sPref = getPreferences(MODE_PRIVATE);
                            SharedPreferences.Editor ed = sPref.edit();
                            ed.putString("IP", value);
                            ed.apply();
                        }
                    }
                });

                alert.setNegativeButton("Отменить", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });

                alert.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private boolean haveNetworkConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private void connectToWiFiNetwork() {
        if(!haveNetworkConnection()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Отсутствует подключение к сети")
                    .setCancelable(false)
                    .setPositiveButton("Включить Wi-Fi", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                        }
                    })
                    .setNegativeButton("Выход", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            finishAndRemoveTask();
                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
        else {
            registerBroadcastReceivers();
            if(WiFiClient != null) {
                WiFiClient.run();
            }
        }
    }

    private void registerBroadcastReceivers() {
        newPassengerBroadcastReceiver = createNewPassengerBroadcastReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                newPassengerBroadcastReceiver,
                new IntentFilter("newPassenger"));

        newDriverBroadcastReceiver = createNewDriverBroadcastReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                newDriverBroadcastReceiver,
                new IntentFilter("newDriver"));

        connectedBroadcastReceiver = connectedBroadcastReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                connectedBroadcastReceiver,
                new IntentFilter("Connected"));
    }

    private BroadcastReceiver createNewPassengerBroadcastReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.hasExtra("passengerLocation")) {
                    String passengerLocation = intent.getStringExtra("passengerLocation");
                }
                if(intent.hasExtra("passengerDestination")) {
                    String passengerDestination = intent.getStringExtra("passengerDestination");
                }
            }
        };
    }

    private BroadcastReceiver createNewDriverBroadcastReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.hasExtra("driverLocation")) {
                    String driverLocation = intent.getStringExtra("driverLocation");
                }
                if(intent.hasExtra("driverDestination")) {
                    String driverDestination = intent.getStringExtra("driverDestination");
                }
            }
        };
    }

    private BroadcastReceiver connectedBroadcastReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(WiFiClient != null && WiFiClient.connected)
                {

                }
            }
        };
    }

    private void geoCodeRequest(String query) {
        if(currentPosition == null) {
            return;
        }
        GeocodeRequest2 geocodeRequest = new GeocodeRequest2(query);
        geocodeRequest.setSearchArea(currentPosition.getCoordinate(), 5000);
        geocodeRequest.execute(new ResultListener<List<GeocodeResult>>() {
            @Override
            public void onCompleted(List<GeocodeResult> results, ErrorCode errorCode) {
                if (errorCode == ErrorCode.NONE) {
                    /*
                     * From the result object, we retrieve the location and its coordinate and
                     * display to the screen. Please refer to HERE Android SDK doc for other
                     * supported APIs.
                     */
                    StringBuilder sb = new StringBuilder();
                    for (GeocodeResult result : results) {
                        sb.append(result.getLocation().getCoordinate().toString());
                        sb.append("\n");
                    }
                    etFindPlace.setText(sb.toString());
                    // Toast.makeText(context, sb.toString(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "ERROR:Geocode Request returned error code:" + errorCode, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

  /*  @Override
    protected void onPause() {
        super.onPause();
        if (mPositioningManager != null) {
            mPositioningManager.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPositioningManager != null) {
            mPositioningManager.start(PositioningManager.LocationMethod.GPS_NETWORK_INDOOR);
        }
    }*/

    protected void checkPermissions() {
        final List<String> missingPermissions = new ArrayList<String>();
        // check all required dynamic permissions
        for (final String permission : REQUIRED_SDK_PERMISSIONS) {
            final int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            // request all missing permissions
            final String[] permissions = missingPermissions
                    .toArray(new String[missingPermissions.size()]);
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS);
        } else {
            final int[] grantResults = new int[REQUIRED_SDK_PERMISSIONS.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            onRequestPermissionsResult(REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
                    grantResults);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                for (int index = permissions.length - 1; index >= 0; --index) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                        // exit the app if one permission is not granted
                        Toast.makeText(this, "Required permission '" + permissions[index]
                                + "' not granted, exiting", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                }
                // all permissions were granted
                initialize();
                break;
        }
    }
    int first = 1;
    @Override
    public void onPositionUpdated(PositioningManager.LocationMethod locationMethod, GeoPosition geoPosition, boolean b) {
        if(currentPosition == null) {
            currentPosition = geoPosition;
        }
        else {
         /*   GeoCoordinate newCoordinate = geoPosition.getCoordinate();
            GeoCoordinate currentCoordinate = currentPosition.getCoordinate();
            double _lathitude = newCoordinate.getLatitude();
            double _longitude = newCoordinate.getLongitude();

            double _shLathitude = new BigDecimal(_lathitude)
                    .setScale(4, RoundingMode.FLOOR).doubleValue();
            double _shLongitude = new BigDecimal(_longitude)
                    .setScale(4, RoundingMode.FLOOR).doubleValue();

            double shLathitude = new BigDecimal(currentCoordinate.getLatitude())
                    .setScale(4, RoundingMode.FLOOR).doubleValue();
            double shLongitude = new BigDecimal(currentCoordinate.getLongitude())
                    .setScale(4, RoundingMode.FLOOR).doubleValue();

            if(_shLathitude == shLathitude && _shLongitude == shLongitude) {
                GeoCoordinate geoCoordinate = geoPosition.getCoordinate();
                Toast.makeText(context, String.valueOf(geoCoordinate.getLatitude()) +
                                String.valueOf(geoCoordinate.getLongitude()),
                        Toast.LENGTH_SHORT).show();
                map.setCenter(geoCoordinate,
                        Map.Animation.NONE);
                return;
            }
            else {
                currentPosition = geoPosition;
            }*/
        }
        GeoCoordinate geoCoordinate = geoPosition.getCoordinate();
        if(first == 1) {
            map.setCenter(geoCoordinate,
                    Map.Animation.NONE);
            first++;
        }

        Image marker_img = new Image();
        try {
            marker_img.setImageResource(R.drawable.circle);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(currentLocationMarker != null) {
            map.removeMapObject(currentLocationMarker);
        }
        // create a MapMarker centered at current location with png image.
        currentLocationMarker = new MapMarker(currentPosition.getCoordinate(), marker_img);
        // add a MapMarker to current active map.
        map.addMapObject(currentLocationMarker);
    }

    @Override
    public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod, PositioningManager.LocationStatus locationStatus) {
        Toast.makeText(context, "onPositionUpdated", Toast.LENGTH_SHORT).show();
    }
}
