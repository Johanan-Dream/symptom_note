package example.c01;

import android.app.*;
import android.car.Car;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.hardware.property.CarPropertyManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** Explicitly started, bounded diagnostic capture. No automatic restart or upload. */
public final class RecordingService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<JSONObject> events = new ArrayDeque<>();
    private Car car;
    private VehicleProbe probe;
    private boolean active;
    private int generation, dropped;
    private long originNs;
    private String storageError;
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() { if (active) { save(); handler.postDelayed(this, 5000); } }
    };
    private final Runnable timeout = () -> { event("duration_limit_30_minutes"); stopSelf(); };
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (intent == null || !"START".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (active) return START_NOT_STICKY;
        if (checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("capture", "차량 기록", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, ProbeActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, RecordingService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "capture")
            .setSmallIcon(android.R.drawable.ic_menu_save).setContentTitle("차량 신호 기록 중")
            .setContentText("최대 30분 · 앱에서 기록 종료 가능").setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "기록 종료", stop).build()).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(21, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(21, notification);
        active = true; originNs = SystemClock.elapsedRealtimeNanos(); event("recording_started");
        try {
            car = Car.createCar(this, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, (connected, ready) -> {
                if (!active) return;
                closeProbe();
                if (!ready) { event("car_disconnected"); save(); return; }
                car = connected;
                final int epoch = generation;
                try {
                    CarPropertyManager p = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
                    CarUxRestrictionsManager u = (CarUxRestrictionsManager) car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
                    if (p == null || u == null) { event("manager_unavailable"); return; }
                    probe = new VehicleProbe(p, u, value -> handler.post(() -> {
                        if (active && epoch == generation) append(value);
                    }));
                    probe.start(); event("subscribed");
                } catch (RuntimeException e) { event("subscribe_error:" + e.getClass().getSimpleName()); }
            });
        } catch (RuntimeException e) { event("connection_error:" + e.getClass().getSimpleName()); stopSelf(); }
        handler.post(checkpoint); handler.postDelayed(timeout, 30 * 60 * 1000L);
        return START_NOT_STICKY;
    }
    private void append(JSONObject value) {
        if (events.size() >= 7200) { events.removeFirst(); dropped++; }
        events.addLast(value);
    }
    private void event(String message) {
        try { append(new JSONObject().put("type", "lifecycle").put("message", message)
            .put("receivedNs", Long.toString(SystemClock.elapsedRealtimeNanos()))); }
        catch (JSONException e) { throw new IllegalStateException(e); }
    }
    private void save() {
        AtomicFile file = new AtomicFile(new File(getFilesDir(), "vehicle-recording.json"));
        FileOutputStream stream = null;
        try {
            JSONObject doc = new JSONObject().put("schemaVersion", 1).put("provenance", "android-probe")
                .put("vehicleOriginVerified", false).put("originNs", Long.toString(originNs))
                .put("activeAtCheckpoint", active).put("checkpointNs", Long.toString(SystemClock.elapsedRealtimeNanos()))
                .put("droppedEvents", dropped).put("previousStorageError", storageError == null ? JSONObject.NULL : storageError)
                .put("events", new JSONArray(events));
            stream = file.startWrite(); stream.write(doc.toString(2).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream); storageError = null;
        } catch (Exception e) {
            if (stream != null) file.failWrite(stream);
            storageError = e.getClass().getSimpleName(); android.util.Log.e("VehicleRecording", "checkpoint failed", e);
        }
    }
    private void closeProbe() {
        generation++;
        if (probe != null) { try { probe.close(); } catch (RuntimeException ignored) {} probe = null; }
    }
    @Override public void onDestroy() {
        active = false; handler.removeCallbacksAndMessages(null); closeProbe();
        if (car != null) { try { car.disconnect(); } catch (RuntimeException ignored) {} car = null; }
        if (originNs != 0) { event("recording_stopped"); save(); } stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
}
