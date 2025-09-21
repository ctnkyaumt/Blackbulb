package info.papdt.blackblub.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
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
    private SeekBar mYellowFilterSeekBar;
    private ExpandIconView mExpandIcon;
    private ImageView mSettingsButton;
    private boolean isUsingDarkTheme = false;
    private boolean isRunning = false;
    private boolean isActiveSliderBrightness = true; // Track which slider is active

    // Service states

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

        // Reset OK button pressed state on app start
        mSettings.setOkButtonPressed(false);

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
        setSeekBarProgress(mSettings.getBrightness(50) - 20); // Changed default brightness to 50%
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int currentProgress = -1;
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Only update if in slider mode or if changed by code
                if (!fromUser || mSettings.isOkButtonPressed()) {
                    currentProgress = progress + 20;
                    if (isRunning) {
                        Intent intent = new Intent(MainActivity.this, MaskService.class);
                        intent.putExtra(Constants.Extra.ACTION, Constants.Action.UPDATE);
                        intent.putExtra(Constants.Extra.BRIGHTNESS, currentProgress);
                        startService(intent);
                    }
                }
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (currentProgress != -1) {
                    mSettings.setBrightness(currentProgress);
                }
            }
        });

        // Prevent SeekBar from changing in navigation mode; allow focus navigation
        mSeekBar.setOnKeyListener((v, keyCode, event) -> {
            if (!mSettings.isOkButtonPressed()) { // Navigation mode
                // Let OK/Enter toggle modes at Activity level
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            // Move focus to settings button
                            if (mSettingsButton != null) {
                                mSettingsButton.requestFocus();
                            }
                            return true;
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                            // Move focus back to toggle
                            mToggle.requestFocus();
                            return true;
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            // Move focus to yellow filter slider
                            if (mYellowFilterSeekBar != null) {
                                mYellowFilterSeekBar.requestFocus();
                            }
                            return true;
                        case KeyEvent.KEYCODE_DPAD_UP:
                            // Stay on current control
                            return true;
                        case KeyEvent.KEYCODE_VOLUME_DOWN:
                        case KeyEvent.KEYCODE_VOLUME_UP:
                            // Do not alter progress in navigation mode
                            return true;
                    }
                }
                // Consume other keys to avoid SeekBar handling them
                return true;
            }
            return false; // In slider mode, allow normal handling
        });

        // Add color temperature filter controls
        View yellowFilterRow = findViewById(R.id.yellow_filter_row);
        yellowFilterRow.setVisibility(View.VISIBLE);
        mYellowFilterSeekBar = findViewById(R.id.yellow_filter_seek_bar);
        mYellowFilterSeekBar.setProgress(mSettings.getYellowFilterAlpha());
        mYellowFilterSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int currentFilterAlpha = -1;
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Only update if in slider mode or if changed by code
                if (!fromUser || mSettings.isOkButtonPressed()) {
                    currentFilterAlpha = progress;
                    if (isRunning) {
                        Intent intent = new Intent(MainActivity.this, MaskService.class);
                        intent.putExtra(Constants.Extra.ACTION, Constants.Action.UPDATE);
                        intent.putExtra(Constants.Extra.YELLOW_FILTER_ALPHA, currentFilterAlpha);
                        startService(intent);
                    }
                }
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (currentFilterAlpha != -1) {
                    mSettings.setYellowFilterAlpha(currentFilterAlpha);
                }
            }
        });

        // Prevent yellow filter SeekBar from changing in navigation mode; allow focus navigation
        mYellowFilterSeekBar.setOnKeyListener((v, keyCode, event) -> {
            if (!mSettings.isOkButtonPressed()) { // Navigation mode
                // Let OK/Enter toggle modes at Activity level
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_UP:
                            // Move focus back to brightness slider
                            mSeekBar.requestFocus();
                            return true;
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                        case KeyEvent.KEYCODE_VOLUME_DOWN:
                        case KeyEvent.KEYCODE_VOLUME_UP:
                            // Do not alter progress; stay/freeze
                            return true;
                    }
                }
                // Consume other keys to avoid SeekBar handling them
                return true;
            }
            return false; // In slider mode, allow normal handling
        });

        // Expand filter controls
        mExpandIcon = findViewById(R.id.expand_icon);
        mSettingsButton = findViewById(R.id.settings_button);
        mSettingsButton.setImageResource(R.drawable.ic_settings_black_24dp);
        mSettingsButton.setOnClickListener(v -> {
            // Show settings menu
            showSettingsMenu();
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
        
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            // Toggle OK button pressed state when OK/Enter is pressed
            boolean newState = !mSettings.isOkButtonPressed();
            mSettings.setOkButtonPressed(newState);
            if (newState) {
                Toast.makeText(this, R.string.slider_mode_enabled, Toast.LENGTH_SHORT).show();
                // Set initial focus to brightness slider when entering slider mode
                if (isActiveSliderBrightness) {
                    mSeekBar.requestFocus();
                } else {
                    mYellowFilterSeekBar.requestFocus();
                }
            } else {
                Toast.makeText(this, R.string.navigation_mode_enabled, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        
        // Handle directional keys based on current mode
        if (mSettings.isOkButtonPressed()) {
            // Slider adjustment mode
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (action == KeyEvent.ACTION_DOWN) {
                        if (isActiveSliderBrightness) {
                            setSeekBarProgress(mSeekBar.getProgress() - 5);
                            int newBrightness = mSeekBar.getProgress() + 20;
                            mSettings.setBrightness(newBrightness);
                        } else {
                            int progress = Math.max(0, mYellowFilterSeekBar.getProgress() - 5);
                            mYellowFilterSeekBar.setProgress(progress);
                            if (isRunning) {
                                Intent intent = new Intent(this, MaskService.class);
                                intent.putExtra(Constants.Extra.ACTION, Constants.Action.UPDATE);
                                intent.putExtra(Constants.Extra.YELLOW_FILTER_ALPHA, progress);
                                startService(intent);
                            }
                            mSettings.setYellowFilterAlpha(progress);
                        }
                    }
                    return true;
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (action == KeyEvent.ACTION_DOWN) {
                        if (isActiveSliderBrightness) {
                            setSeekBarProgress(mSeekBar.getProgress() + 5);
                            int newBrightness = mSeekBar.getProgress() + 20;
                            mSettings.setBrightness(newBrightness);
                        } else {
                            int progress = Math.min(100, mYellowFilterSeekBar.getProgress() + 5);
                            mYellowFilterSeekBar.setProgress(progress);
                            if (isRunning) {
                                Intent intent = new Intent(this, MaskService.class);
                                intent.putExtra(Constants.Extra.ACTION, Constants.Action.UPDATE);
                                intent.putExtra(Constants.Extra.YELLOW_FILTER_ALPHA, progress);
                                startService(intent);
                            }
                            mSettings.setYellowFilterAlpha(progress);
                        }
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (action == KeyEvent.ACTION_DOWN) {
                        // Toggle between brightness and yellow filter sliders
                        isActiveSliderBrightness = !isActiveSliderBrightness;
                        Toast.makeText(this, isActiveSliderBrightness ? 
                                R.string.brightness_slider_active : 
                                R.string.yellow_filter_slider_active, Toast.LENGTH_SHORT).show();
                        
                        // Set focus to the active slider
                        if (isActiveSliderBrightness) {
                            mSeekBar.requestFocus();
                        } else {
                            mYellowFilterSeekBar.requestFocus();
                        }
                    }
                    return true;
            }
        } else {
            // Navigation mode - handle directional navigation
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    // Handle navigation based on current focus
                    if (mToggle.isFocused()) {
                        mSeekBar.requestFocus();
                        return true;
                    } else if (mSeekBar.isFocused()) {
                        mSettingsButton.requestFocus();
                        return true;
                    }
                    break;
                    
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    // Handle navigation based on current focus
                    if (mSettingsButton.isFocused()) {
                        mSeekBar.requestFocus();
                        return true;
                    } else if (mSeekBar.isFocused()) {
                        mToggle.requestFocus();
                        return true;
                    }
                    break;
                    
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // Navigate to yellow filter slider
                    if (mSeekBar.isFocused()) {
                        mYellowFilterSeekBar.requestFocus();
                        return true;
                    }
                    break;
                    
                case KeyEvent.KEYCODE_DPAD_UP:
                    // Navigate back to brightness slider
                    if (mYellowFilterSeekBar.isFocused()) {
                        mSeekBar.requestFocus();
                        return true;
                    }
                    break;
            }
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

    // Add method to show settings menu
    private void showSettingsMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.settings_title);
        
        String[] options = {
            getString(R.string.settings_startup_brightness),
            getString(R.string.settings_startup_yellow_filter)
        };
        
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    // Set startup brightness
                    showBrightnessSettingDialog();
                    break;
                case 1:
                    // Set startup yellow filter
                    showYellowFilterSettingDialog();
                    break;
            }
        });
        
        builder.show();
    }
    
    private void showBrightnessSettingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.settings_startup_brightness);
        
        final SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(80);
        seekBar.setProgress(mSettings.getBrightness(50) - 20);
        
        builder.setView(seekBar);
        
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            int brightness = seekBar.getProgress() + 20;
            mSettings.setBrightness(brightness);
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton(android.R.string.cancel, null);
        
        builder.show();
    }
    
    private void showYellowFilterSettingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.settings_startup_yellow_filter);
        
        final SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(mSettings.getYellowFilterAlpha());
        
        builder.setView(seekBar);
        
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            int yellowFilter = seekBar.getProgress();
            mSettings.setYellowFilterAlpha(yellowFilter);
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton(android.R.string.cancel, null);
        
        builder.show();
    }
}
