package me.jonkeller.codetheroad;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.text.DateFormat;

import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.Query;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class MapsActivity extends ActionBarActivity
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "CodeTheRoad";
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    Location mCurrentLocation;
    public static final int MAP_ZOOM_LEVEL = 7;
    public static final long  UPDATE_INTERVAL_IN_MS = 120000;
    public static final long FASTEST_UPDATE_INTERVAL_IN_MS = UPDATE_INTERVAL_IN_MS / 4;
    protected LocationRequest mLocationRequest;
    private boolean mRequestingLocationUpdates = false;
    String mLastUpdateTime;
    Firebase myFirebaseRef;
    private boolean mResolvingError = false;
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private static final String DIALOG_ERROR = "dialog_error";
    List<Marker> markerList = new ArrayList<>();
    String startLoggingTime;
    int REQUEST_PLACE_PICKER = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Firebase.setAndroidContext(this);
        myFirebaseRef = new Firebase("https://intense-inferno-7848.firebaseio.com/");
        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();
        buildGoogleApiClient();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_maps_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
            case R.id.action_search:
                try {
                    PlacePicker.IntentBuilder intentBuilder = new PlacePicker.IntentBuilder();
                    Intent intent = intentBuilder.build(this);

                    // Start the Intent by requesting a result, identified by a request code.
                    startActivityForResult(intent, REQUEST_PLACE_PICKER);
                } catch (GooglePlayServicesRepairableException e) {
                    GooglePlayServicesUtil.getErrorDialog(e.getConnectionStatusCode(), this, 0);
                } catch (GooglePlayServicesNotAvailableException e) {
                    Toast.makeText(this, "Google Play Services is not available.", Toast.LENGTH_LONG).show();
                }
                return true;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_PLACE_PICKER) {
            // This result is from the PlacePicker dialog.

            if (resultCode == Activity.RESULT_OK) {
       /* User has picked a place, extract data.
          Data is extracted from the returned intent by retrieving a Place
          object from the PlacePicker.
         */
                final Place place = PlacePicker.getPlace(data, this);

         /* A Place object contains details about that place, such as its name,
         address and phone number. Extract the name, address, phone number,
         place ID and place types.
          */
                final CharSequence name = place.getName();
                final CharSequence address = place.getAddress();
                final CharSequence phone = place.getPhoneNumber();
                final String placeId = place.getId();
                String attribution = PlacePicker.getAttributions(data);
                if(attribution == null){
                    attribution = "";
                }

                // Update data on map
                mMap.addMarker(new MarkerOptions()
                                .position(place.getLatLng())
                                .title(name.toString())
                );

                Map mLocation = new HashMap();
                mLocation.put("timestamp", mLastUpdateTime);

                Map  mCoordinate = new HashMap();
                mCoordinate.put("latitude", place.getLatLng().latitude);
                mCoordinate.put("longitude", place.getLatLng().longitude);
                mLocation.put("location", mCoordinate);

                mLocation.put("place_id", place.getId());
                myFirebaseRef.push().setValue(mLocation);
                                // Print data to debug log
                Log.d(TAG, "Place selected: " + placeId + " (" + name.toString() + ")");


            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
        // END_INCLUDE(activity_result)
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        LatLng busLocation = new LatLng(37.783879, -122.401254);

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(busLocation, 12));
        mMap.addMarker(new MarkerOptions()
                .position(busLocation)
                .title("Code the Road Bus")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.bus)));
    }

    @Override
    public void onConnected(Bundle bundle) {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(mLastLocation.getLatitude(),
                        mLastLocation.getLongitude()),
                MAP_ZOOM_LEVEL));
    }

    @Override
    public void onConnectionSuspended(int i) {
        System.out.println(":(");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the 'Handle Connection Failures' section.
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            // Show dialog using GooglePlayServicesUtil.getErrorDialog()
            System.out.println(":( :( :( :( ");
            //showErrorDialog(result.getErrorCode());
            mResolvingError = true;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = new Date();
        mLastUpdateTime = dateFormat.format(date).toString();

        saveToFirebase();

        // Retrieve saved locations and draw as marker on map
        drawLocations();

        // Update UI to draw bread crumb with the latest bus location.
        mMap.clear();

        LatLng mLatlng = new LatLng(mCurrentLocation.getLatitude(),
                mCurrentLocation.getLongitude());
        MarkerOptions mMarkerOption = new MarkerOptions()
                .position(mLatlng)
                .title(mLastUpdateTime)
        .icon(BitmapDescriptorFactory.fromResource(R.drawable.code_the_road_small));

        Marker mMarker = mMap.addMarker(mMarkerOption);
    }

    private void saveToFirebase() {
        Map mLocations = new HashMap();
        mLocations.put("timestamp", mLastUpdateTime);
        Map  mCoordinate = new HashMap();
        mCoordinate.put("latitude", mCurrentLocation.getLatitude());
        mCoordinate.put("longitude", mCurrentLocation.getLongitude());
        mLocations.put("location", mCoordinate);
        myFirebaseRef.push().setValue(mLocations);
    }

    private void drawLocations() {
        // Get only latest logged locations - since 'START' button clicked
        Query queryRef = myFirebaseRef.orderByChild("timestamp").startAt(startLoggingTime);
        // Add listener for a child added at the data at this location
        queryRef.addChildEventListener(new ChildEventListener() {
            LatLngBounds bounds;
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Map  data = (Map ) dataSnapshot.getValue();
                String timestamp = (String) data.get("timestamp");
                // Get recorded latitude and longitude
                Map  mCoordinate = (HashMap)data.get("location");
                double latitude = (double) (mCoordinate.get("latitude"));
                double longitude = (double) (mCoordinate.get("longitude"));

                // Create LatLng for each locations
                LatLng mLatlng = new LatLng(latitude, longitude);

                // Make sure the map boundary contains the location
                builder.include(mLatlng);
                bounds = builder.build();

                // Add a marker for each logged location
                MarkerOptions mMarkerOption = new MarkerOptions()
                        .position(mLatlng)
                        .title(timestamp)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.code_the_road_small));
                Marker mMarker = mMap.addMarker(mMarkerOption);
                markerList.add(mMarker);

                // Zoom map to the boundary that contains every logged location
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,
                        MAP_ZOOM_LEVEL));
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }

            //Override other abstract methods for addChildEventListener below
        });
    }
    public void startLogging(View view) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (mGoogleApiClient.isConnected() && !mRequestingLocationUpdates) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = new Date();
            startLoggingTime = dateFormat.format(date).toString();
            mRequestingLocationUpdates = true;
            startLocationUpdates();
        }
    }

    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    public void stopLogging(View view) {
        if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates) {
            mRequestingLocationUpdates = false;
            stopLocationUpdates();
        }
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        FragmentManager fm = getFragmentManager();
        dialogFragment.show(fm, "errordialog");
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed() {
        mResolvingError = false;
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() { }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GooglePlayServicesUtil.getErrorDialog(errorCode,
                    this.getActivity(), REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((MapsActivity)getActivity()).onDialogDismissed();
        }
    }}
