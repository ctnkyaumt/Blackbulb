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
    
    // Predefined settings for auto-start
    private static final int DEFAULT_BRIGHTNESS = 45;
    private static final int DEFAULT_YELLOW_FILTER = 60;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            
            Log.i(TAG, "Device boot completed, starting Blackbulb service automatically");
            
            // Get settings
            Settings settings = Settings.getInstance(context);
            
            // Save the predefined settings
            settings.setBrightness(DEFAULT_BRIGHTNESS);
            settings.setYellowFilterAlpha(DEFAULT_YELLOW_FILTER);
            
            // Create intent to start the service
            Intent startIntent = new Intent(context, MaskService.class);
            startIntent.putExtra(Constants.Extra.ACTION, Constants.Action.START);
            startIntent.putExtra(Constants.Extra.BRIGHTNESS, DEFAULT_BRIGHTNESS);
            startIntent.putExtra(Constants.Extra.ADVANCED_MODE, settings.getAdvancedMode());
            startIntent.putExtra(Constants.Extra.YELLOW_FILTER_ALPHA, DEFAULT_YELLOW_FILTER);
            
            // Start the service
            Utility.startForegroundService(context, startIntent);
            
            Log.i(TAG, "Blackbulb started with brightness: " + DEFAULT_BRIGHTNESS + 
                  ", yellow filter: " + DEFAULT_YELLOW_FILTER);
        }
    }
}
