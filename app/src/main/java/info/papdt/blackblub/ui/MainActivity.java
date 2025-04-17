package info.papdt.blackblub.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Toast;

import com.github.zagum.expandicon.ExpandIconView;

import info.papdt.blackblub.Constants;
import info.papdt.blackblub.IMaskServiceInterface;
import info.papdt.blackblub.R;
import info.papdt.blackblub.receiver.ActionReceiver;
import info.papdt.blackblub.service.MaskService;
import info.papdt.blackblub.util.Settings;
import info.papdt.blackblub.util.Utility;

public class MainActivity extends Activity {

    // Views & States
    private ImageButton mToggle;
    private SeekBar mSeekBar;
    private ExpandIconView mExpandIcon;
    private boolean isUsingDarkTheme = false;

    // Service states
    private boolean isRunning = false;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            IMaskServiceInterface msi = IMaskServiceInterface.Stub.asInterface(service);
            try {
                setToggleIconState(isRunning = msi.isShowing());
                Utility.createStatusBarTiles(MainActivity.this, isRunning);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    // Settings
    private Settings mSettings;

    // Constants
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mSettings = Settings.getInstance(this);

        // Exclude from recents if needed
        if (!mSettings.shouldShowTask()) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                for (ActivityManager.AppTask task : am.getAppTasks()) {
                    task.setExcludeFromRecents(true);
                }
            }
        }

        // Apply theme and transparent system ui
        Utility.applyTransparentSystemUI(this);
        if (mSettings.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark);
            isUsingDarkTheme = true;
        } else {
            setTheme(R.style.AppTheme_Light);
            isUsingDarkTheme = false;
        }

        // Apply Noto Sans CJK font
        Utility.applyNotoSansCJK(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Clicking outside to close
        findViewById(R.id.root_layout).setOnClickListener(v -> finish());

        // Setup toggle button
        mToggle = findViewById(R.id.toggle);
        mToggle.setOnClickListener(v -> {
            if (!isRunning) {
                if (!Utility.canDrawOverlays(this)) {
                    Utility.requestOverlayPermission(this, REQUEST_CODE_OVERLAY_PERMISSION);
                    return;
                }
                startMaskService();
            } else {
                stopMaskService();
            }
        });

        // Setup brightness seekbar
        mSeekBar = findViewById(R.id.seek_bar);
        setSeekBarProgress(mSettings.getBrightness(60) - 20);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int currentProgress = -1;
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentProgress = progress + 20;
                if (isRunning) {
                    Intent intent = new Intent(MainActivity.this, MaskService.class);
                    intent.putExtra(Constants.Extra.ACTION, Constants.Action.UPDATE);
                    intent.putExtra(Constants.Extra.BRIGHTNESS, currentProgress);
                    startService(intent);
                }
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (currentProgress != -1) {
                    mSettings.setBrightness(currentProgress);
                }
            }
        });

        // Expand filter controls
        mExpandIcon = findViewById(R.id.expand_icon);
        mExpandIcon.setOnClickListener(v -> {
            isUsingDarkTheme = !isUsingDarkTheme; // placeholder: keep expansion logic if needed
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, MaskService.class), mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unbindService(mServiceConnection); } catch (Exception ignored) {}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION && Utility.canDrawOverlays(this)) {
            startMaskService();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int action = event.getAction();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) setSeekBarProgress(mSeekBar.getProgress() - 5);
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) setSeekBarProgress(mSeekBar.getProgress() + 5);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void setSeekBarProgress(int progress) {
        progress = Math.max(0, Math.min(80, progress));
        mSeekBar.setProgress(progress);
    }

    private void setToggleIconState(boolean isRunning) {
        mToggle.setImageResource(isRunning ? R.drawable.ic_brightness_2_black_24dp
                                            : R.drawable.ic_brightness_7_black_24dp);
    }

    private void startMaskService() {
        ActionReceiver.sendActionStart(this);
        setToggleIconState(isRunning = true);
    }

    private void stopMaskService() {
        ActionReceiver.sendActionStop(this);
        setToggleIconState(isRunning = false);
    }
}
