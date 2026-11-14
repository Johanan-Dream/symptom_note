package example.c01;

import android.car.VehiclePropertyIds;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.os.SystemClock;
import org.json.JSONException;
import org.json.JSONObject;

/** UNBUILT integration probe, not an Android application or verified Pleos adapter.
 * Inject connected managers after permission handling. Sink must enqueue without blocking.
 * Use dedicated managers/listener ownership; stop on Car service disconnect.
 */
@SuppressWarnings("deprecation")
public final class VehicleProbe implements AutoCloseable {
    public interface Sink { void emit(JSONObject event); }
    private final CarPropertyManager properties;
    private final CarUxRestrictionsManager restrictions;
    private final Sink sink;
    private boolean started;
    private final CarPropertyManager.CarPropertyEventCallback callback = new CarPropertyManager.CarPropertyEventCallback() {
        @Override public void onChangeEvent(CarPropertyValue value) {
            if (!started || value.getPropertyId() != VehiclePropertyIds.PERF_VEHICLE_SPEED) return;
            String status = value.getStatus() == CarPropertyValue.STATUS_AVAILABLE ? "ok"
                    : value.getStatus() == CarPropertyValue.STATUS_UNAVAILABLE ? "unavailable" : "error";
            Object raw = "ok".equals(status) ? value.getValue() : null;
            if ("ok".equals(status) && (!(raw instanceof Number) || !Double.isFinite(((Number)raw).doubleValue()))) {
                status = "error"; raw = null;
            }
            emit("speed", status, value.getTimestamp(), raw, null);
        }
        @Override public void onErrorEvent(int propertyId, int areaId) {
            if (started && propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED)
                emit("speed", "error", null, null, null);
        }
    };
    public VehicleProbe(CarPropertyManager p, CarUxRestrictionsManager u, Sink s) {
        properties = p; restrictions = u; sink = s;
    }
    public void start() {
        if (started) throw new IllegalStateException("already started");
        started = true;
        try {
            // Legacy API intentionally used for target compatibility; verify selected Pleos SDK.
            boolean subscribed = properties.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, 1f);
            if (!subscribed) emit("speed", "error", null, null, null);
        } catch (SecurityException e) { emit("speed", "permission_denied", null, null, null);
        } catch (IllegalArgumentException e) { emit("speed", "unsupported", null, null, null);
        } catch (RuntimeException e) { emit("speed", "error", null, null, null); }
        try {
            restrictions.registerListener(this::onRestrictions);
            onRestrictions(restrictions.getCurrentCarUxRestrictions());
        } catch (RuntimeException e) { emit("ux", "error", null, null, null); }
    }
    private void onRestrictions(CarUxRestrictions ux) {
        if (!started) return;
        if (ux == null) { emit("ux", "unavailable", null, null, null); return; }
        emit("ux", "ok", null, ux.getActiveRestrictions(), ux.isRequiresDistractionOptimization());
    }
    private void emit(String type, String status, Long timestamp, Object value, Boolean requiresDO) {
        try {
            JSONObject event = new JSONObject();
            event.put("provenance", "android-probe").put("type", type).put("status", status);
            // Decimal strings preserve nanoseconds when parsed by JavaScript.
            event.put("receivedNs", Long.toString(SystemClock.elapsedRealtimeNanos()));
            event.put("timestampNs", timestamp == null ? JSONObject.NULL : Long.toString(timestamp));
            event.put("valueMps", "speed".equals(type) && value != null ? value : JSONObject.NULL);
            if ("ux".equals(type)) {
                event.put("activeRestrictions", value == null ? JSONObject.NULL : value);
                event.put("requiresDO", requiresDO == null ? JSONObject.NULL : requiresDO);
            }
            sink.emit(event);
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
    @Override public void close() {
        started = false;
        try { properties.unregisterCallback(callback); }
        finally { restrictions.unregisterListener(); }
    }
}
