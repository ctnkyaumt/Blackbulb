package info.papdt.blackblub.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import info.papdt.blackblub.Constants;
import info.papdt.blackblub.service.MaskService;
import info.papdt.blackblub.util.Settings;
import info.papdt.blackblub.util.Utility;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = BootReceiver.class.getSimpleName();
    

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            
            Log.i(TAG, "Device boot completed, starting Blackbulb service automatically");
            
            // Get settings
            Settings settings = Settings.getInstance(context);
            
            // Read saved settings (do not override user preferences)
            int brightness = settings.getBrightness(50);
            int yellowFilter = settings.getYellowFilterAlpha(0);
            
            // Create intent to start the service with user's saved settings
            Intent startIntent = new Intent(context, MaskService.class);
            startIntent.putExtra(Constants.Extra.ACTION, Constants.Action.START);
            startIntent.putExtra(Constants.Extra.BRIGHTNESS, brightness);
            startIntent.putExtra(Constants.Extra.ADVANCED_MODE, settings.getAdvancedMode());
            startIntent.putExtra(Constants.Extra.YELLOW_FILTER_ALPHA, yellowFilter);
            
            // Start the service
            Utility.startForegroundService(context, startIntent);
            
            Log.i(TAG, "Blackbulb started with brightness: " + brightness + 
                  ", yellow filter: " + yellowFilter);
        }
    }
}
