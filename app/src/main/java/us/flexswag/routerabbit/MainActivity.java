package us.flexswag.routerabbit;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final int DRAW_OVER_OTHER_APP_PERMISSION = 123;
    private String TAG = MainActivity.class.getSimpleName();
    private ProgressDialog pDialog;
    private ListView lv;
    private ClipboardManager mClipboardManager;
    private String mPreviousText = "";
    static boolean bHasClipChangedListener = false;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private boolean selfDeleteAfterClick = false;
    private int previousClickedOnStopNumber = 0;
    private String lastClickedAddressValue = "";
    private int notificationId = 1;
    private String CHANNEL_ID = "aChannelID";

    ArrayList<String> objectsToRemove;
    String[] objectBatch = new String[8];
    ArrayList<HashMap<String, String>> contactList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        createNotificationChannel();

        mClipboardManager =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (mClipboardManager != null) {
            RegPrimaryClipChanged();
        }

        //listener on changed sort order preference:
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //prefs.edit().putInt("prevClickedStopNum", previousClickedOnStopNumber).apply();

        contactList = new ArrayList<>();
        objectsToRemove = new ArrayList<>();
        lv = (ListView) findViewById(R.id.list);
        selfDeleteAfterClick = prefs.getBoolean("self_delete", false);
        previousClickedOnStopNumber = prefs.getInt("prevClickedStopNum", 0);
        lastClickedAddressValue = prefs.getString("lastClickedAddress", "");

        jSWrite();
        new GetContacts().execute();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //getObjectBatch();
                //String nextStopToMaps = getNextStop();
                showNotification();
            }
        });
        FloatingActionButton fabRefresh = (FloatingActionButton) findViewById(R.id.fabRefresh);
        fabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshView();
            }
        });

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                String s = ((TextView) view.findViewById(R.id.stopNumber)).getText().toString();
                if (position != 0){
                    previousClickedOnStopNumber = Integer.valueOf(s);

                    TextView mAddressText = view.findViewById(R.id.address);
                    mAddressText.setTextColor(Color.GRAY);
                    lastClickedAddressValue = mAddressText.getText().toString();

                    prefs.edit().putString("lastClickedAddress", lastClickedAddressValue).apply();
                    prefs.edit().putInt("prevClickedStopNum", previousClickedOnStopNumber).apply();

                    showNotification();
                    //Toast.makeText(MainActivity.this, "lastAddressClicked: " + lastClickedAddressValue, Toast.LENGTH_SHORT).show();
                    showAlertDialogButtonClicked(view);
                } else {
                    if (!lastClickedAddressValue.equals("")){
                        showAlertDialogButtonClicked(view);
                    }
                }
            }
        });

        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(final AdapterView<?> parent, final View view, int position, long id) {

                AlertDialog.Builder adb=new AlertDialog.Builder(MainActivity.this);
                adb.setTitle("Delete?");
                adb.setMessage("Are you sure you want to delete this Log");
                adb.setNegativeButton("Cancel", null);
                adb.setPositiveButton("Ok", new AlertDialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        objectsToRemove.add(0, ((TextView) view.findViewById(R.id.objectId)).getText().toString());
                        try {
                            jsRemove(objectsToRemove);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }});
                adb.show();

                return true;
            }
        });

        prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

                if(key.equals("self_delete")){
                    selfDeleteAfterClick = prefs.getBoolean("self_delete", false);
                    Toast.makeText(MainActivity.this, String.format("Value: %s", selfDeleteAfterClick), Toast.LENGTH_SHORT).show();
                }

            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "name";
            String description = "description";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    public void showNotification(){
        String nextStop = getNextStop();
        if (nextStop.equals("")){
            nextStop = "No more new stops. All Done!";
        }
        Intent intent = new Intent(this, MyBroadcastReceiver.class);
        intent.putExtra("nextStop", nextStop);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent routePendingIntent =
                PendingIntent.getBroadcast(this.getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_directions_car)
                .setContentTitle("Next Stop")
                .setContentText(nextStop)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(R.drawable.ic_navigate_next, "Route",
                        routePendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(notificationId, mBuilder.build());
    }

    public void getObjectBatch(){
        try{
            StringBuilder strBuilder = new StringBuilder();
            JSONObject jsonObj = new JSONObject(readJSONFromAsset());
            // Getting JSON Array node
            JSONArray contacts = jsonObj.getJSONArray("stops");

            for(int n = 0; n < contacts.length(); n++)
            {
                JSONObject object = contacts.getJSONObject(n);
                String sObj = object.getString("address");
                if (n <= 8){
                    objectBatch[n] = sObj;
                }
            }
            for (String anObjectBatch : objectBatch) {
                if (anObjectBatch != null){
                    strBuilder.append(anObjectBatch);
                }
            }
            Toast.makeText(this, "objBatch = " + strBuilder.toString(), Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getTotalNumberOfStops(){
        String numberOfStops = "";
        try {
            JSONObject jsonObj = new JSONObject(readJSONFromAsset());
            // Getting JSON Array node
            JSONArray contacts = jsonObj.getJSONArray("stops");

            numberOfStops = Integer.toString(contacts.length());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return numberOfStops;
    }

    public void routeFromFabClick(final String s){
        // setup the alert builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Map");

        // add a list
        String[] mapApps = {"Waze", "Google Maps"};
        builder.setItems(mapApps, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: {
                        try
                        {
                            // Launch Waze to look for address:
                            String url = "https://waze.com/ul?q=" + s;
                            Intent intent = new Intent( Intent.ACTION_VIEW, Uri.parse( url ) );
                            startActivity( intent );
                            Toast.makeText(MainActivity.this, "Hopping to Waze!", Toast.LENGTH_SHORT).show();
                        }
                        catch ( ActivityNotFoundException ex  )
                        {
                            // If Waze is not installed, open it in Google Play:
                            Intent intent = new Intent( Intent.ACTION_VIEW, Uri.parse( "market://details?id=com.waze" ) );
                            startActivity(intent);
                        }
                        break;
                    }
                    case 1: {
                        Toast.makeText(MainActivity.this, "Hopping to Google Maps!", Toast.LENGTH_SHORT).show();
                        Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + s);
                        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                        mapIntent.setPackage("com.google.android.apps.maps");
                        startActivity(mapIntent);
                        break;
                    }
                }
                if (selfDeleteAfterClick){
                    removeObjectFromFabClick();
                }
            }
        });

        // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showAlertDialogButtonClicked(final View view) {

        // setup the alert builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Map");

        // add a list
        String[] mapApps = {"Waze", "Google Maps"};
        builder.setItems(mapApps, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: {
                        try
                        {
                            // Launch Waze to look for address:
                            String addressToMaps = ((TextView) view.findViewById(R.id.address)).getText().toString();
                            String url = "https://waze.com/ul?q=" + addressToMaps;
                            Intent intent = new Intent( Intent.ACTION_VIEW, Uri.parse( url ) );
                            startActivity( intent );
                            Toast.makeText(MainActivity.this, "Hopping to Waze!", Toast.LENGTH_SHORT).show();
                        }
                        catch ( ActivityNotFoundException ex  )
                        {
                            // If Waze is not installed, open it in Google Play:
                            Intent intent = new Intent( Intent.ACTION_VIEW, Uri.parse( "market://details?id=com.waze" ) );
                            startActivity(intent);
                        }
                        break;
                    }
                    case 1: {
                        Toast.makeText(MainActivity.this, "Hopping to Google Maps!", Toast.LENGTH_SHORT).show();
                        String addressToMaps = ((TextView) view.findViewById(R.id.address)).getText().toString();
                        Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + addressToMaps);
                        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                        mapIntent.setPackage("com.google.android.apps.maps");
                        startActivity(mapIntent);
                        break;
                    }
                }
                if (selfDeleteAfterClick){
                    objectsToRemove.add(0, ((TextView) view.findViewById(R.id.objectId)).getText().toString());
                    try {
                        jsRemove(objectsToRemove);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void jsRemove(ArrayList<String> data) throws JSONException {
        JSONObject jsonObj = new JSONObject(readJSONFromAsset());
        // Getting JSON Array node
        JSONArray contacts = jsonObj.getJSONArray("stops");
        JSONObject innerObj = new JSONObject();

        for(int i = 0 ; i < contacts.length(); i++){
            if(contacts.getJSONObject(i).get("objectId").equals(data.get(0)))
                //Toast.makeText(this, "Found it: " + i, Toast.LENGTH_SHORT).show();
                if (i == 0){
                    innerObj.put("objectId", "0Reserved");
                    innerObj.put("stopNumber", "");
                    innerObj.put("address", "");

                    contacts.put(0, innerObj);
                } else {
                        contacts.remove(i);
                    }
            //Toast.makeText(this, contacts.toString(), Toast.LENGTH_SHORT).show();
            JSONObject newJsonObject = new JSONObject(jsonObj.toString());

            try {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput("routes.json", Context.MODE_PRIVATE));
                outputStreamWriter.write(newJsonObject.toString());
                outputStreamWriter.flush();
                outputStreamWriter.close();
            }
            catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }
        }
        Toast.makeText(this, "Log Deleted.", Toast.LENGTH_SHORT).show();
        refreshView();
    }

    private void removeObjectFromFabClick(){
        try {
            JSONObject jsonObj = new JSONObject(readJSONFromAsset());
            // Getting JSON Array node
            JSONArray contacts = jsonObj.getJSONArray("stops");
            JSONObject innerObj = new JSONObject();

            String stopNumberToRemove = String.valueOf(previousClickedOnStopNumber);

            for(int i = 0 ; i < contacts.length(); i++) {
                if (contacts.getJSONObject(i).get("stopNumber").equals(stopNumberToRemove)){

                    if (i == 0){
                        innerObj.put("objectId", "0Reserved");
                        innerObj.put("stopNumber", "");
                        innerObj.put("address", "");

                        contacts.put(0, innerObj);
                    } else {
                        contacts.remove(i);
                    }

                    JSONObject newJsonObject = new JSONObject(jsonObj.toString());
                    try {
                        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput("routes.json", Context.MODE_PRIVATE));
                        outputStreamWriter.write(newJsonObject.toString());
                        outputStreamWriter.flush();
                        outputStreamWriter.close();
                    }
                    catch (IOException e) {
                        Log.e("Exception", "File write failed: " + e.toString());
                    }
                }
            }
        } catch (final JSONException e) {
            Log.e(TAG, "Json parsing error: " + e.getMessage());
        }
        refreshView();
    }

    public String getNextStop(){
        String s = "";
        try {
            JSONObject jsonObj = new JSONObject(readJSONFromAsset());
            // Getting JSON Array node
            JSONArray contacts = jsonObj.getJSONArray("stops");

            String nextStopNumber = String.valueOf(previousClickedOnStopNumber + 1);

            for(int i = 0 ; i < contacts.length(); i++) {
                if (contacts.getJSONObject(i).get("stopNumber").equals(nextStopNumber)){
                    s = contacts.getJSONObject(i).get("address").toString();
                }
            }
        } catch (final JSONException e) {
            Log.e(TAG, "Json parsing error: " + e.getMessage());
        }
        if (!s.equals("")){
            previousClickedOnStopNumber += 1;
        }
        return s;
    }

    public void jSWrite(){
        JSONObject obj = new JSONObject();
        JSONArray list = new JSONArray();
        JSONObject innerObj = new JSONObject();
        File file = getBaseContext().getFileStreamPath("routes.json");

        if(file.exists()){
            //Toast.makeText(this, "File Exist", Toast.LENGTH_LONG).show();
        } else {
            try {
                innerObj.put("objectId", "0Reserved");
                innerObj.put("stopNumber", "");
                innerObj.put("address", "");

            } catch (JSONException e) {
                e.printStackTrace();
            }
            list.put(innerObj);

            try {
                obj.put("stops", list);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput("routes.json", Context.MODE_PRIVATE));
                outputStreamWriter.write(obj.toString());
                outputStreamWriter.flush();
                outputStreamWriter.close();
            }
            catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }
        }
    }

    public String readJSONFromAsset() {
        String json = null;
        try {
            InputStream is = this.openFileInput("routes.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

    public void refreshView(){
        this.recreate();
    }

    public void writeNewObjects(String s){
        Random rand = new Random();

        int n = rand.nextInt(50) + 1;
        String uniqueID = String.valueOf(n * 3);

        try{
            JSONObject jsonObj = new JSONObject(readJSONFromAsset());
            // Getting JSON Array node
            JSONArray contacts = jsonObj.getJSONArray("stops");
            //JSONArray list = new JSONArray();
            JSONObject innerObj = new JSONObject();
            String stopNum = Integer.toString(contacts.length());

            try {
                innerObj.put("objectId", uniqueID);
                innerObj.put("stopNumber", stopNum);
                innerObj.put("address", s);

            } catch (JSONException e) {
                e.printStackTrace();
            }
            contacts.put(innerObj);

            JSONObject newJsonObject = new JSONObject(jsonObj.toString());
            //Toast.makeText(this, newJsonObject.toString(), Toast.LENGTH_SHORT).show();

            try {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput("routes.json", Context.MODE_PRIVATE));
                outputStreamWriter.write(newJsonObject.toString());
                outputStreamWriter.flush();
                outputStreamWriter.close();
            } catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }

        } catch (final JSONException e) {
            Log.e(TAG, "Json parsing error: " + e.getMessage());
        }
        RegPrimaryClipChanged();
    }

    private void RegPrimaryClipChanged(){
        if(!bHasClipChangedListener){
            mClipboardManager.addPrimaryClipChangedListener(mOnPrimaryClipChangedListener);
            bHasClipChangedListener = true;
        }
    }
    private void UnRegPrimaryClipChanged(){
        if(bHasClipChangedListener){
            mClipboardManager.removePrimaryClipChangedListener(mOnPrimaryClipChangedListener);
            bHasClipChangedListener = false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_undo) {
            undoLastObjectDelete();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void undoLastObjectDelete(){
        try{
            JSONObject jsonObj = new JSONObject(readJSONFromAsset());
            // Getting JSON Array node
            JSONArray contacts = jsonObj.getJSONArray("stops");
            //JSONArray list = new JSONArray();
            JSONObject innerObj = new JSONObject();

            try {
                innerObj.put("objectId", "0Reserved");
                if (!lastClickedAddressValue.equals("")){
                    innerObj.put("stopNumber", "Previous Stop");
                } else {
                    innerObj.put("stopNumber", "");
                }
                innerObj.put("address", lastClickedAddressValue);

            } catch (JSONException e) {
                e.printStackTrace();
            }
            contacts.put(0, innerObj);

            JSONObject newJsonObject = new JSONObject(jsonObj.toString());
            //Toast.makeText(this, newJsonObject.toString(), Toast.LENGTH_SHORT).show();

            try {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput("routes.json", Context.MODE_PRIVATE));
                outputStreamWriter.write(newJsonObject.toString());
                outputStreamWriter.flush();
                outputStreamWriter.close();
            } catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }

        } catch (final JSONException e) {
            Log.e(TAG, "Json parsing error: " + e.getMessage());
        }
        refreshView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //listener on changed sort order preference:
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        selfDeleteAfterClick = prefs.getBoolean("self_delete", false);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putInt("prevClickedStopNum", previousClickedOnStopNumber).apply();
        prefs.edit().putString("lastClickedAddress", lastClickedAddressValue).apply();
    }

    private ClipboardManager.OnPrimaryClipChangedListener mOnPrimaryClipChangedListener =
            new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override
                public void onPrimaryClipChanged() {
                    try{
                        Log.d(TAG, "onPrimaryClipChanged");
                        ClipData clip = mClipboardManager.getPrimaryClip();
                        final String newClip = clip.getItemAt(0).getText().toString();

                        //new WriteJsonTask().execute(objectsToWrite);
                        Toast.makeText(MainActivity.this, "Clip Changed: " + newClip, Toast.LENGTH_SHORT).show();

                        if(mPreviousText.equals(clip.getItemAt(0).getText().toString())) return;
                        else{
                            AlertDialog.Builder adb=new AlertDialog.Builder(MainActivity.this);
                            adb.setTitle("Write?");
                            adb.setMessage("Write this Address: " + newClip + " ?");
                            adb.setNegativeButton("Cancel", null);
                            adb.setPositiveButton("Ok", new AlertDialog.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    writeNewObjects(newClip);
                                }});
                            adb.show();
                            mPreviousText = clip.getItemAt(0).getText().toString();
                        }
                        UnRegPrimaryClipChanged();
                        RegPrimaryClipChanged();
                    } catch(final Exception e){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(),
                                        "Clip change error: " + e.getMessage(),
                                        Toast.LENGTH_LONG)
                                        .show();
                            }
                        });
                    }
                }
            };

    /**
     * Async task class to get json by making HTTP call
     */
    private class GetContacts extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Showing progress dialog
            pDialog = new ProgressDialog(MainActivity.this);
            pDialog.setMessage("Please wait...");
            pDialog.setCancelable(false);
            pDialog.show();

        }

        @Override
        protected Void doInBackground(Void... arg0) {
            try {
                JSONObject jsonObj = new JSONObject(readJSONFromAsset());

                // Getting JSON Array node
                JSONArray contacts = jsonObj.getJSONArray("stops");

                // looping through All Logs
                for (int i = 0; i < contacts.length(); i++) {
                    JSONObject c = contacts.getJSONObject(i);

                    String objectId = c.getString("objectId");
                    String stopNumber = c.getString("stopNumber");
                    String address = c.getString("address");

                    // tmp hash map for single log
                    HashMap<String, String> contact = new HashMap<>();

                    // adding each child node to HashMap key => value
                    contact.put("objectId", objectId);
                    contact.put("stopNumber", stopNumber);
                    contact.put("address", address);

                    // adding log to log list
                    contactList.add(contact);
                }
            } catch (final JSONException e) {
                Log.e(TAG, "Json parsing error: " + e.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),
                                "Json parsing error: " + e.getMessage(),
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });

            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            // Dismiss the progress dialog
            if (pDialog.isShowing())
                pDialog.dismiss();
            //* Updating parsed JSON data into ListView
            ListAdapter adapter = new SimpleAdapter(
                    MainActivity.this, contactList,
                    R.layout.list_item, new String[]{"objectId", "stopNumber", "address"},
                    new int[]{R.id.objectId, R.id.stopNumber,
                    R.id.address});

            lv.setAdapter(adapter);
        }
    }

}
