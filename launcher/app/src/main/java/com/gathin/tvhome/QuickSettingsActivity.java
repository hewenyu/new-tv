package com.gathin.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Control Center: a full-height sheet at the right edge, opened by Home's 控制中心 pill (control_center) and by
 * the remote's Settings button when {@code settings_key_panel} is on (HomeKeyService, MainActivity). Volume, the
 * network's state and the TV's settings pages, over Home or whatever app is playing. Translucent and in a task of
 * its own, so the app behind keeps running and Back returns to it. Pressing Settings again, or anything else taking
 * the screen, closes it.
 */
public final class QuickSettingsActivity extends Activity {
    /** Slide and scrim fade, docs/redesign-spec.md §3. */
    private static final int SLIDE_MS = 220;
    private static final int FOCUS_MS = 140;
    private static final String XIAOMI_SETTINGS = "com.xiaomi.mitv.settings";
    /** AudioManager's hidden VOLUME_CHANGED_ACTION and STREAM_MUTE_CHANGED_ACTION, sent by the system only. */
    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION";

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshVolume();
        }
    };
    private boolean volumeReceiverOn;

    private AudioManager audio;
    private View sheet, scrim, volumeRow, volumeHint;
    private ImageView volumeIcon;
    private ProgressBar volumeBar;
    private TextView volumeValue;
    /** The Network tile's icon and status line, refreshed in onResume; null when the tile is hidden. */
    private ImageView networkIcon;
    private TextView networkStatus;
    private int slideMs, focusMs;
    private boolean leftEdge, closing;
    /** wifi_status_warnings: only then does an unvalidated network read 「已连接，无法上网」 (see ControlText.network). */
    private boolean wifiStatusWarnings;
    private float density;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        density = getResources().getDisplayMetrics().density;
        Config cfg = Config.load(this);
        boolean reduced = cfg.reducedMotion || !cfg.animations;
        slideMs = reduced ? Motion.REDUCED_MS : SLIDE_MS;
        focusMs = reduced ? Motion.REDUCED_MS : FOCUS_MS;

        sheet = findViewById(R.id.panel);
        scrim = findViewById(R.id.scrim);
        wifiStatusWarnings = cfg.wifiStatusWarnings;
        // Hebrew puts the menu bar's Settings at the left, so the sheet comes in from that edge.
        leftEdge = getResources().getBoolean(R.bool.rtl_menu_bar);
        if (leftEdge) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sheet.getLayoutParams();
            lp.gravity = Gravity.LEFT;
            sheet.setLayoutParams(lp);
        }
        sheet.setBackground(sheetBackground());
        scrim.setOnClickListener(v -> close());
        scrim.setFocusable(false);   // clickable views are focusable by default since API 26; keep focus on the items

        setUpVolume();
        Map<String, String> overrides = ControlTargets.parse(cfg.controlCenterTargets);
        LinearLayout tiles = findViewById(R.id.tiles);
        List<View> tileViews = buildTiles(tiles, overrides, cfg);
        layoutTiles(tiles, tileViews);
        if (tileViews.isEmpty()) findViewById(R.id.quickLabel).setVisibility(View.GONE);
        LinearLayout rows = findViewById(R.id.rows);
        buildRows(rows, overrides);
        if (rows.getChildCount() == 0) findViewById(R.id.moreLabel).setVisibility(View.GONE);

        View first = volumeRow.getVisibility() == View.VISIBLE ? volumeRow
                : !tileViews.isEmpty() ? tileViews.get(0) : rows.getChildAt(0);
        // ScrollView makes itself focusable in its constructor and the window gave it focus on open; it only needs to
        // scroll. Asked again after the first layout, when the window has focus to give.
        sheet.setFocusable(false);
        if (first != null) {
            first.requestFocus();
            sheet.post(first::requestFocus);
        }
        if (state == null) slideIn();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // On Android TV the window manager hands the remote's volume keys to the media session, so this window never
        // sees them; the system's broadcast is what tells the row the level or mute changed.
        IntentFilter volumeChanges = new IntentFilter(VOLUME_CHANGED);
        volumeChanges.addAction(MUTE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeReceiver, volumeChanges, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(volumeReceiver, volumeChanges);
        }
        volumeReceiverOn = true;
        refreshVolume();
        refreshNetwork();
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        if (volumeReceiverOn) {
            unregisterReceiver(volumeReceiver);
            volumeReceiverOn = false;
        }
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        close();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (closing) return true;   // sliding out: nothing should move focus or open a page now
        return super.dispatchKeyEvent(event);
    }

    // ---- sheet ------------------------------------------------------------------------------------

    /** Opaque tvh_surface (even at 98 % the focused Home pill showed through), 20dp corners on the screen side, a 1dp light edge there (a lighter layer peeking out). */
    private Drawable sheetBackground() {
        float r = 20 * density;
        float[] radii = leftEdge ? new float[]{0, 0, r, r, r, r, 0, 0} : new float[]{r, r, 0, 0, 0, 0, r, r};
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(0x1FFFFFFF);
        edge.setCornerRadii(radii);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(getColor(R.color.tvh_surface) | 0xFF000000);
        fill.setCornerRadii(radii);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{edge, fill});
        int line = Math.max(1, Math.round(density));
        if (leftEdge) layers.setLayerInset(1, 0, 0, line, 0);
        else layers.setLayerInset(1, line, 0, 0, 0);
        return layers;
    }

    /** Where the sheet sits while it is off screen. Its width is in its layout params before the first layout. */
    private float offScreen() {
        int width = sheet.getWidth() > 0 ? sheet.getWidth() : sheet.getLayoutParams().width;
        return leftEdge ? -width : width;
    }

    private void slideIn() {
        sheet.setTranslationX(offScreen());
        scrim.setAlpha(0f);
        sheet.animate().translationX(0f).setDuration(slideMs).setInterpolator(new DecelerateInterpolator()).start();
        scrim.animate().alpha(1f).setDuration(slideMs).setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Back and a touch on the scrim: slide out, then finish. Opening a page finishes at once instead. */
    private void close() {
        if (closing) return;
        closing = true;
        scrim.animate().alpha(0f).setDuration(slideMs).setInterpolator(new DecelerateInterpolator()).start();
        sheet.animate().translationX(offScreen()).setDuration(slideMs).setInterpolator(new DecelerateInterpolator())
                .withEndAction(this::finish).start();
    }

    /** The light fill alone is colour only; the spec wants focus to grow too. */
    private void scaleOnFocus(View item, float scale) {
        View.OnFocusChangeListener previous = item.getOnFocusChangeListener();
        item.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? scale : 1f).scaleY(focused ? scale : 1f).setDuration(focusMs).start();
            if (previous != null) previous.onFocusChange(v, focused);
        });
    }

    // ---- volume -----------------------------------------------------------------------------------

    private void setUpVolume() {
        volumeRow = findViewById(R.id.volume);
        volumeHint = findViewById(R.id.volumeHint);
        volumeIcon = findViewById(R.id.volumeIcon);
        volumeBar = findViewById(R.id.volumeBar);
        volumeValue = findViewById(R.id.volumeValue);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        // A set whose volume belongs to the amplifier (HDMI-CEC, a fixed line out) cannot be turned here.
        if (audio == null || audio.isVolumeFixed()) {
            volumeRow.setVisibility(View.GONE);
            volumeHint.setVisibility(View.GONE);
            return;
        }
        volumeRow.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            // Held keys repeat ACTION_DOWN, one step each. Both actions are consumed so focus stays on the row.
            if (event.getAction() == KeyEvent.ACTION_DOWN) stepVolume(code == KeyEvent.KEYCODE_DPAD_RIGHT);
            return true;
        });
        volumeRow.setOnClickListener(v -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE));
        volumeRow.setOnFocusChangeListener((v, focused) -> volumeHint.setVisibility(focused ? View.VISIBLE : View.INVISIBLE));
        scaleOnFocus(volumeRow, 1.02f);
    }

    /** One press of Left or Right: a fifteenth of the range (ControlText.volumeStep), not one index of it. */
    private void stepVolume(boolean up) {
        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int level = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            // A press while muted unmutes first, as the remote's own volume keys do.
            if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            }
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, ControlText.volumeStep(level, max, up), 0);   // flags 0: no system volume bar
        } catch (SecurityException e) {
            // Do Not Disturb may refuse the change; the row then shows the level as it is.
        }
        refreshVolume();
    }

    private void adjustVolume(int direction) {
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);   // flags 0: no system volume bar
        } catch (SecurityException e) {
            // Do Not Disturb may refuse a mute change; the row then shows the level as it is.
        }
        refreshVolume();
    }

    private void refreshVolume() {
        if (audio == null || volumeRow.getVisibility() != View.VISIBLE) return;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int level = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
        String value = ControlText.volume(level, max, muted, getString(R.string.control_muted));
        volumeValue.setText(value);
        volumeBar.setMax(Math.max(1, max));
        volumeBar.setProgress(ControlText.volumeFill(level, max, muted));
        volumeIcon.setImageResource(muted ? R.drawable.ic_volume_off : R.drawable.ic_volume);
        volumeRow.setContentDescription(getString(R.string.control_volume) + ", " + value);
    }

    // ---- tiles ------------------------------------------------------------------------------------

    private List<View> buildTiles(LinearLayout parent, Map<String, String> overrides, Config cfg) {
        List<View> tiles = new ArrayList<>();
        // MainActivity opens the Inputs dropdown under its pill (or top_menu's gear); without either it has nowhere to.
        // An override that can open stands on its own.
        Intent inputs = override(overrides, ControlTargets.INPUTS);
        if (inputs == null && (cfg.showInputsPill || cfg.topMenu) && hasPassthroughInput()) {
            inputs = new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_VIEW_INPUTS);
        }
        if (inputs != null) {
            addTile(parent, tiles, R.drawable.ic_input, R.string.control_inputs, getString(R.string.control_inputs_status), inputs);
        }
        View network = addTile(parent, tiles, R.drawable.ic_wifi, R.string.control_network, "",
                target(overrides, ControlTargets.NETWORK, SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS)));
        if (network != null) {
            networkIcon = network.findViewById(R.id.icon);
            networkStatus = network.findViewById(R.id.status);
        }
        addTile(parent, tiles, R.drawable.ic_picture, R.string.control_picture, getString(R.string.control_picture_status),
                target(overrides, ControlTargets.PICTURE, SystemSettings.intent(this, Settings.ACTION_DISPLAY_SETTINGS)));
        addTile(parent, tiles, R.drawable.ic_sound, R.string.control_sound, getString(R.string.control_sound_status),
                target(overrides, ControlTargets.SOUND, SystemSettings.intent(this, Settings.ACTION_SOUND_SETTINGS)));
        addTile(parent, tiles, R.drawable.ic_bluetooth, R.string.control_bluetooth, getString(R.string.control_bluetooth_status),
                target(overrides, ControlTargets.BLUETOOTH, SystemSettings.intent(this, Settings.ACTION_BLUETOOTH_SETTINGS)));
        addTile(parent, tiles, R.drawable.ic_apps, R.string.control_apps, getString(R.string.control_apps_status),
                target(overrides, ControlTargets.APPS, SystemSettings.intent(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        return tiles;
    }

    /** A tile for target, or null (and nothing added) when target opens nothing on this TV. */
    private View addTile(LinearLayout parent, List<View> tiles, int icon, int title, CharSequence status, Intent target) {
        if (!opens(target)) return null;
        View tile = getLayoutInflater().inflate(R.layout.item_control_tile, parent, false);
        ((ImageView) tile.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) tile.findViewById(R.id.title)).setText(title);
        ((TextView) tile.findViewById(R.id.status)).setText(status);
        tile.setOnClickListener(v -> launch(target));
        scaleOnFocus(tile, 1.04f);
        tiles.add(tile);
        return tile;
    }

    /** Two columns, 12dp apart; an odd last tile keeps its half width beside an empty cell. */
    private void layoutTiles(LinearLayout parent, List<View> tiles) {
        int gap = Math.round(12 * density);
        for (int i = 0; i < tiles.size(); i += 2) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setClipChildren(false);
            line.setClipToPadding(false);
            View left = tiles.get(i);
            line.addView(left);
            View right;
            LinearLayout.LayoutParams rightParams;
            if (i + 1 < tiles.size()) {
                right = tiles.get(i + 1);
                rightParams = (LinearLayout.LayoutParams) right.getLayoutParams();
            } else {
                right = new Space(this);
                rightParams = new LinearLayout.LayoutParams(0, left.getLayoutParams().height, 1f);
                // Nothing beside it: Right would otherwise jump to the tile up and to the right.
                left.setOnKeyListener((v, code, event) -> code == KeyEvent.KEYCODE_DPAD_RIGHT);
            }
            rightParams.setMarginStart(gap);
            line.addView(right, rightParams);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lineParams.topMargin = gap;
            parent.addView(line, lineParams);
        }
    }

    /** The same test as MainActivity.updateInputsButton's: an HDMI or AV input that Settings has not hidden. */
    private boolean hasPassthroughInput() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return false;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.isPassthroughInput() && !info.isHidden(this)) return true;
        }
        return false;
    }

    /**
     * The network Android uses (getActiveNetwork). Only when it has none, or that one is a VPN, the other networks
     * are ranked: Ethernet first, as the network pill does, then Wi-Fi, then any other.
     */
    private void refreshNetwork() {
        if (networkStatus == null) return;
        ControlText.Transport best = ControlText.Transport.NONE;
        boolean validated = false;
        String ipv4 = null;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network chosen = null;
                Network active = cm.getActiveNetwork();
                if (active != null && transport(cm.getNetworkCapabilities(active)) != ControlText.Transport.NONE) {
                    chosen = active;
                } else {
                    for (Network net : cm.getAllNetworks()) {
                        ControlText.Transport t = transport(cm.getNetworkCapabilities(net));
                        if (ControlText.preferred(t, best)) {
                            best = t;
                            chosen = net;
                        }
                    }
                }
                if (chosen != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(chosen);
                    best = transport(caps);
                    validated = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    LinkProperties link = cm.getLinkProperties(chosen);
                    List<InetAddress> addresses = new ArrayList<>();
                    if (link != null) for (LinkAddress a : link.getLinkAddresses()) addresses.add(a.getAddress());
                    ipv4 = ControlText.ipv4(addresses);
                }
            }
        } catch (RuntimeException ignored) {
            // A firmware that refuses the lookup reads as offline, as the network pill does.
        }
        ControlText.Network state = ControlText.network(best, validated, wifiStatusWarnings);
        String text;
        switch (state) {
            case WIRED: text = ControlText.withAddress(getString(R.string.control_wired), ipv4); break;
            case WIRELESS: text = ControlText.withAddress(getString(R.string.control_wireless), ipv4); break;
            case CONNECTED: text = ControlText.withAddress(getString(R.string.control_connected), ipv4); break;
            case NO_INTERNET: text = getString(R.string.control_no_internet); break;
            default: text = getString(R.string.control_offline); break;
        }
        networkStatus.setText(text);
        boolean warn = state == ControlText.Network.NO_INTERNET || state == ControlText.Network.OFFLINE;
        ColorStateList colour = getColorStateList(warn ? R.color.cc_warn : R.color.cc_ink2);
        networkStatus.setTextColor(colour);
        networkIcon.setImageResource(best == ControlText.Transport.ETHERNET ? R.drawable.ic_ethernet : R.drawable.ic_wifi);
    }

    /** The transport a network with internet answers on; NONE for no capabilities, no internet, or a VPN. */
    private static ControlText.Transport transport(NetworkCapabilities caps) {
        if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return ControlText.Transport.NONE;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return ControlText.Transport.ETHERNET;
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? ControlText.Transport.WIFI : ControlText.Transport.OTHER;
    }

    // ---- rows -------------------------------------------------------------------------------------

    private void buildRows(LinearLayout parent, Map<String, String> overrides) {
        Intent vendor = SystemSettings.vendorIntent(this);
        Intent adb = target(overrides, ControlTargets.ADB, vendor != null ? vendor
                : new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        addRow(parent, R.drawable.ic_adb, R.string.control_adb, adbHint(adb), adb);
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(parent, R.drawable.ic_home_settings, R.string.control_launcher, getString(R.string.control_launcher_hint),
                target(overrides, ControlTargets.LAUNCHER,
                        new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS)));
        addRow(parent, R.drawable.ic_settings, R.string.control_system, "", target(overrides, ControlTargets.SYSTEM,
                vendor != null ? vendor : SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        // With vendor settings as 系统设置, Android's own settings get a row of their own; otherwise it would repeat.
        addRow(parent, R.drawable.ic_settings, R.string.control_android, "", target(overrides, ControlTargets.ANDROID,
                vendor != null ? new Intent(Settings.ACTION_SETTINGS) : null));
        // A launcher may not restart the TV itself; the Home key helper can open the system power menu instead.
        Intent powerTarget = target(overrides, ControlTargets.POWER, null);
        if (powerTarget != null) {
            addRow(parent, R.drawable.ic_power, R.string.power_menu, "", powerTarget);
        } else if (HomeKeyService.canOpenPowerMenu()) {
            addRow(parent, R.drawable.ic_power, R.string.power_menu, "", v -> {
                HomeKeyService.openPowerMenu();
                finish();
            });
        }
    }

    /** Where the switch is: Xiaomi's settings app keeps it under Account & security; AOSP under Developer options. */
    private String adbHint(Intent adb) {
        if (adb == null) return "";
        ComponentName c = adb.getComponent();
        if (c != null && XIAOMI_SETTINGS.equals(c.getPackageName())) return getString(R.string.control_adb_xiaomi_hint);
        if (Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS.equals(adb.getAction())) return getString(R.string.control_adb_hint);
        return "";
    }

    /** A row for target, or none when target is null or opens nothing on this TV. */
    private void addRow(LinearLayout parent, int icon, int title, CharSequence hint, Intent target) {
        if (opens(target)) addRow(parent, icon, title, hint, v -> launch(target));
    }

    private void addRow(LinearLayout parent, int icon, int title, CharSequence hint, View.OnClickListener click) {
        View row = getLayoutInflater().inflate(R.layout.item_control_row, parent, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        ((TextView) row.findViewById(R.id.status)).setText(hint);
        row.setOnClickListener(click);
        scaleOnFocus(row, 1.02f);
        if (parent.getChildCount() > 0) ((LinearLayout.LayoutParams) row.getLayoutParams()).topMargin = Math.round(8 * density);
        parent.addView(row);
    }

    // ---- targets ----------------------------------------------------------------------------------

    /** control_center_targets' component for key when this launcher may start it, else fallback (may be null). */
    private Intent target(Map<String, String> overrides, String key, Intent fallback) {
        Intent override = override(overrides, key);
        return override != null ? override : fallback;
    }

    /**
     * The override for key, or null when there is none or it could not open: an explicit intent "resolves" even to an
     * activity that is not exported or wants a permission this app lacks, and OK would then throw SecurityException
     * where the standard page would have worked. A disabled or missing activity throws NameNotFoundException.
     */
    private Intent override(Map<String, String> overrides, String key) {
        String flat = overrides.get(key);
        ComponentName component = flat == null ? null : ComponentName.unflattenFromString(flat);
        if (component == null) return null;
        try {
            ActivityInfo info = getPackageManager().getActivityInfo(component, 0);
            if (!info.exported) return null;
            if (info.permission != null && checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED) return null;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        return new Intent().setComponent(component);
    }

    /** This launcher's own screens always open; anything else must resolve, or its item is left out. */
    private boolean opens(Intent target) {
        if (target == null) return false;
        ComponentName c = target.getComponent();
        if (c != null && getPackageName().equals(c.getPackageName())) return true;
        return getPackageManager().resolveActivity(target, PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    private void launch(Intent target) {
        try {
            startActivity(new Intent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
