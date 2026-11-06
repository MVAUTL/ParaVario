package fr.paravario.prototype;

import android.Manifest;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.*;
import android.os.*;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class FlightActivity extends Activity implements SensorEventListener, LocationListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SensorManager sensors;
    private LocationManager gps;
    private TextView instruments, altitudeView, speedView, details;
    private Button control;
    private BufferedWriter journal;
    private Location fix;
    private double pressure = Double.NaN, altitude = Double.NaN, vario = Double.NaN, acceleration = Double.NaN;
    private long pressureTime;
    private boolean recording;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        sensors = getSystemService(SensorManager.class);
        gps = getSystemService(LocationManager.class);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff081722);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(34), dp(16), dp(34), dp(22));
        scroll.addView(panel);

        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("paraglider", "drawable", getPackageName()));
        panel.addView(logo, new LinearLayout.LayoutParams(dp(32), dp(32)));
        panel.addView(label("PARAVARIO", 11, 0xff89b8c7));
        instruments = label("", 31, 0xff55e2cb);
        instruments.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(instruments);
        panel.addView(label("VARIO", 10, 0xff89b8c7));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setGravity(Gravity.CENTER);
        metrics.setPadding(0, dp(6), 0, dp(2));
        panel.addView(metrics, new LinearLayout.LayoutParams(-1, -2));
        altitudeView = metric(metrics, "BARO STD");
        speedView = metric(metrics, "VITESSE SOL");

        control = new Button(this);
        control.setAllCaps(false);
        control.setMinHeight(dp(42));
        control.setMinimumHeight(dp(42));
        setControl(false);
        panel.addView(control, new LinearLayout.LayoutParams(dp(116), dp(44)));
        details = label("Prêt à enregistrer", 11, 0xffa5c5d0);
        details.setPadding(0, dp(6), 0, 0);
        panel.addView(details);
        setContentView(scroll);
        render();
        control.setOnClickListener(v -> {
            if (recording) stop("Vol enregistré");
            else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            else start();
        });
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 1 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) start();
        else details.setText("Autorise la position précise pour enregistrer le vol.");
    }

    private void start() {
        if (!gps.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            details.setText("Active la localisation sur la montre.");
            return;
        }
        fix = null;
        pressure = altitude = vario = acceleration = Double.NaN;
        pressureTime = 0;
        try {
            journal = new BufferedWriter(new OutputStreamWriter(
                openFileOutput("vol-" + System.currentTimeMillis() + ".csv", MODE_PRIVATE), StandardCharsets.UTF_8));
            journal.write("epoch_ms,latitude,longitude,vitesse_sol_kmh,altitude_gps_ellipsoide_m,pression_hpa,altitude_std_m,vario_ms,acceleration_lineaire_ms2\n");
            gps.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            register(Sensor.TYPE_PRESSURE);
            register(Sensor.TYPE_LINEAR_ACCELERATION);
            recording = true;
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setControl(true);
            tick.run();
        } catch (IOException | RuntimeException failure) {
            stop("Impossible de démarrer le vol.");
        }
    }

    private void register(int type) {
        Sensor sensor = sensors.getDefaultSensor(type);
        if (sensor != null) sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            if (fix != null && SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos() > 5_000_000_000L)
                fix = null;
            render();
            try {
                journal.write(System.currentTimeMillis() + "," +
                    (fix == null ? ",," : csv(fix.getLatitude(), 6) + "," + csv(fix.getLongitude(), 6) + ",") +
                    csv(speed(), 2) + "," + csv(gpsAltitude(), 2) + "," + csv(pressure, 2) + "," +
                    csv(altitude, 2) + "," + csv(vario, 2) + "," + csv(acceleration, 2) + "\n");
                journal.flush();
            } catch (IOException failure) {
                stop("Erreur d’écriture du journal.");
                return;
            }
            handler.postDelayed(this, 1000);
        }
    };

    private double speed() { return fix != null && fix.hasSpeed() ? fix.getSpeed() * 3.6 : Double.NaN; }
    private double gpsAltitude() { return fix != null && fix.hasAltitude() ? fix.getAltitude() : Double.NaN; }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int size, int color) {
        TextView label = new TextView(this);
        label.setGravity(Gravity.CENTER);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }

    private TextView metric(LinearLayout row, String title) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = label("", 15, 0xffeffbff);
        column.addView(value);
        column.addView(label(title, 9, 0xff89b8c7));
        return value;
    }

    private void setControl(boolean active) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(active ? 0xffffa680 : 0xff55e2cb);
        background.setCornerRadius(dp(22));
        control.setBackground(background);
        control.setTextColor(0xff081722);
        control.setText(active ? "Arrêter" : "Démarrer");
    }

    private void render() {
        instruments.setText(display(vario, "%+.1f") + " m/s");
        altitudeView.setText(display(altitude, "%.0f") + " m");
        speedView.setText(display(speed(), "%.1f") + " km/h");
        if (recording) details.setText((fix == null ? "Recherche GPS" : "GPS actif") + "\n" +
            display(gpsAltitude(), "%.0f") + " m GPS · " + display(pressure, "%.1f") + " hPa\n" +
            "Accélération " + display(acceleration, "%.2f") + " m/s²");
    }

    private static String display(double value, String format) {
        return Double.isFinite(value) ? String.format(Locale.FRANCE, format, value) : "—";
    }

    private static String csv(double value, int decimals) {
        return Double.isFinite(value) ? String.format(Locale.US, "%." + decimals + "f", value) : "";
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            pressure = event.values[0];
            double measured = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, (float) pressure);
            double elapsed = (event.timestamp - pressureTime) / 1e9;
            if (pressureTime == 0 || elapsed > 4) {
                altitude = measured;
                vario = Double.NaN;
            } else if (elapsed > 0) {
                double previous = altitude;
                altitude += (1 - Math.exp(-elapsed / 0.8)) * (measured - altitude);
                double rate = (altitude - previous) / elapsed;
                if (!Double.isFinite(vario)) vario = 0;
                vario += (1 - Math.exp(-elapsed / 1.5)) * (rate - vario);
            }
            pressureTime = event.timestamp;
        } else {
            float[] v = event.values;
            acceleration = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public void onLocationChanged(Location location) { fix = location; }

    private void stop(String message) {
        recording = false;
        handler.removeCallbacks(tick);
        gps.removeUpdates(this);
        sensors.unregisterListener(this);
        if (journal != null) {
            try { journal.close(); } catch (IOException failure) { message = "Erreur de fermeture du journal."; }
            journal = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setControl(false);
        details.setText(message);
    }

    @Override protected void onStop() {
        if (recording) stop("Vol enregistré");
        super.onStop();
    }
}
