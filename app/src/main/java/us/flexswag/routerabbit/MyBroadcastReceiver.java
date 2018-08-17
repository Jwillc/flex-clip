package us.flexswag.routerabbit;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public class MyBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String nextStopToRoute = intent.getStringExtra("nextStop");
        Toast.makeText(context, "Routing to next stop: " + nextStopToRoute,
                Toast.LENGTH_LONG).show();

        try
        {
            // Launch Waze to look for address:
            String url = "https://waze.com/ul?q=" + nextStopToRoute;
            Intent intentMaps = new Intent( Intent.ACTION_VIEW, Uri.parse( url ) );
            context.startActivity(intentMaps);
        }
        catch ( ActivityNotFoundException ex  )
        {
            // If Waze is not installed, open it in Google Play:
            Intent intentNoMaps = new Intent( Intent.ACTION_VIEW, Uri.parse( "market://details?id=com.waze" ) );
            context.startActivity(intentNoMaps);
        }

    }
}
