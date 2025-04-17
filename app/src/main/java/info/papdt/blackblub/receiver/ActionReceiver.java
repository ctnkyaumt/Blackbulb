package info.papdt.blackblub.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import info.papdt.blackblub.Constants;
import info.papdt.blackblub.service.MaskService;
import info.papdt.blackblub.service.MaskTileService;
import info.papdt.blackblub.util.Settings;
import info.papdt.blackblub.util.Utility;

public class ActionReceiver extends BroadcastReceiver {

    private static String TAG = ActionReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Settings settings = Settings.getInstance(context);
        Log.i(TAG, "received \"" + intent.getAction() + "\" action");
        if (Constants.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
            int action = intent.getIntExtra(Constants.Extra.ACTION, -1);
            int brightness = intent.getIntExtra(Constants.Extra.BRIGHTNESS, 50);
            int yellowFilterAlpha = intent.getIntExtra(Constants.Extra.YELLOW_FILTER_ALPHA, 0);

            Log.i(TAG, "handle \"" + action + "\" action");
            switch (action) {
                case Constants.Action.START:
                    Intent startIntent = new Intent(context, MaskService.class);
                    startIntent.putExtra(Constants.Extra.ACTION, Constants.Action.START);
                    startIntent.putExtra(Constants.Extra.BRIGHTNESS, settings.getBrightness(brightness));
                    startIntent.putExtra(Constants.Extra.ADVANCED_MODE, settings.getAdvancedMode());
                    startIntent.putExtra(Constants.Extra.YELLOW_FILTER_ALPHA,
                            settings.getYellowFilterAlpha(yellowFilterAlpha));
                    Utility.startForegroundService(context, startIntent);
                    break;
                case Constants.Action.PAUSE:
                    Intent pauseIntent = new Intent(context, MaskService.class);
                    pauseIntent.putExtra(Constants.Extra.ACTION, Constants.Action.PAUSE);
                    pauseIntent.putExtra(Constants.Extra.BRIGHTNESS, settings.getBrightness(brightness));
                    pauseIntent.putExtra(Constants.Extra.ADVANCED_MODE, settings.getAdvancedMode());
                    pauseIntent.putExtra(Constants.Extra.YELLOW_FILTER_ALPHA,
                            settings.getYellowFilterAlpha(yellowFilterAlpha));
                    Utility.startForegroundService(context, pauseIntent);
                    break;
                case Constants.Action.STOP:
                    Intent stopIntent = new Intent(context, MaskService.class);
                    stopIntent.putExtra(Constants.Extra.ACTION, Constants.Action.STOP);
                    context.startService(stopIntent);
                    break;
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Intent tileUpdateIntent = new Intent(context, MaskTileService.class);
                tileUpdateIntent.putExtra(Constants.Extra.ACTION, action);
                context.startService(tileUpdateIntent);
            }
        }
        // Timer functionality removed: no handling for ACTION_ALARM_START/ACTION_ALARM_STOP
    }

    /**
     * Send action to ActionReceiver
     */
    public static void sendAction(Context context, int action) {
        Intent activeIntent = new Intent(context, ActionReceiver.class);
        activeIntent.setAction(Constants.ACTION_UPDATE_STATUS);
        activeIntent.putExtra(Constants.Extra.ACTION, action);
        context.sendBroadcast(activeIntent);
    }

    public static void sendActionStart(Context context) {
        sendAction(context, Constants.Action.START);
    }

    public static void sendActionPause(Context context) {
        sendAction(context, Constants.Action.PAUSE);
    }

    public static void sendActionStop(Context context) {
        sendAction(context, Constants.Action.STOP);
    }

    public static void sendActionStartOrStop(Context context, boolean shouldStart) {
        if (shouldStart) {
            sendActionStart(context);
        } else {
            sendActionStop(context);
        }
    }
}
