package example.c01;

import android.app.Activity;
import android.car.Car;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.hardware.property.CarPropertyManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** Diagnostic UI. Explicit recording is owned independently by RecordingService. */
public final class ProbeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<JSONObject> events = new ArrayDeque<>();
    private Car car;
    private VehicleProbe probe;
    private boolean visible, ready, uxAllows;
    private int generation, probeGeneration, dropped;
    private long originNs;
    private TextView status, latest;
    private Button permission, export, startRecording, stopRecording, card, demo;
    private String pendingExport;
    private LinearLayout diagnostics;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        VehicleStyle.configure(this);
        originNs = SystemClock.elapsedRealtimeNanos();
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(VehicleStyle.BG);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        int pad = VehicleStyle.dp(this,28); column.setPadding(pad,pad,pad,pad); scroll.addView(column);
        column.addView(VehicleStyle.text(this,"정비 상담을 위한 차량 기록",16,VehicleStyle.MUTED));
        column.addView(VehicleStyle.text(this,"증상노트",36,VehicleStyle.TEXT));
        column.addView(VehicleStyle.text(this,"그때의 증상, 정비 상담까지 이어지도록.",22,VehicleStyle.MUTED));
        status=VehicleStyle.text(this,"",18,VehicleStyle.MUTED); column.addView(status);
        LinearLayout steps=new LinearLayout(this);
        boolean wide=getResources().getConfiguration().screenWidthDp>=760;
        steps.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);column.addView(steps);
        LinearLayout recordPanel=new LinearLayout(this),cardPanel=new LinearLayout(this);
        for(LinearLayout panel:new LinearLayout[]{recordPanel,cardPanel}){
            panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(pad,pad,pad,pad);
            android.graphics.drawable.GradientDrawable surface=new android.graphics.drawable.GradientDrawable();
            surface.setColor(VehicleStyle.PANEL);
            surface.setCornerRadius(VehicleStyle.dp(this,28));panel.setBackground(surface);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(wide?0:-1,-2,wide?1:0);
            lp.setMargins(VehicleStyle.dp(this,8),VehicleStyle.dp(this,20),VehicleStyle.dp(this,8),VehicleStyle.dp(this,20));
            steps.addView(panel,lp);
        }
        recordPanel.addView(VehicleStyle.text(this,"01  출발 전",16,VehicleStyle.MUTED));
        recordPanel.addView(VehicleStyle.text(this,"차량 기록",30,VehicleStyle.TEXT));
        recordPanel.addView(VehicleStyle.text(this,"출발 전 시작 → 정차 후 종료\n새 기록을 시작하면 이전 기록을 덮어씁니다.",18,VehicleStyle.MUTED));
        permission = new Button(this); permission.setText("속도 읽기 권한 요청"); recordPanel.addView(permission);
        permission.setOnClickListener(v -> {
            if (ready && uxAllows) requestPermissions(new String[]{Car.PERMISSION_SPEED}, 10);
        });
        export = new Button(this); export.setText("관측 로그 JSON 저장"); 
        export.setOnClickListener(v -> exportLog());
        startRecording = new Button(this); startRecording.setText("연속 기록 시작 (최대 30분)"); recordPanel.addView(startRecording);
        startRecording.setOnClickListener(v -> {
            if (!visible || !ready || !uxAllows || checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) return;
            try { startForegroundService(new Intent(this, RecordingService.class).setAction("START")); appendSystem("recording_start_requested"); }
            catch (RuntimeException e) { appendSystem("recording_start_failed:"+e.getClass().getSimpleName()); }
            render();
        });
        stopRecording = new Button(this); stopRecording.setText("연속 기록 종료"); recordPanel.addView(stopRecording);
        stopRecording.setOnClickListener(v -> { if (visible && ready && uxAllows) { stopService(new Intent(this, RecordingService.class)); appendSystem("recording_stop_requested"); render(); } });
        cardPanel.addView(VehicleStyle.text(this,"02  정차 후",16,VehicleStyle.MUTED));
        cardPanel.addView(VehicleStyle.text(this,"상담 준비",30,VehicleStyle.TEXT));
        cardPanel.addView(VehicleStyle.text(this,"기억나는 구간 + 느꼈던 증상\n차량 관측과 나란히 정리합니다.",18,VehicleStyle.MUTED));
        card=new Button(this);card.setText("저장된 기록으로 상담 카드 만들기");cardPanel.addView(card);
        card.setOnClickListener(v->{if(visible&&ready&&uxAllows)startActivity(new Intent(this,ConsultationActivity.class));});
        demo=new Button(this);demo.setText("합성 예시로 상담 카드 체험");
        demo.setOnClickListener(v->{if(visible&&ready&&uxAllows)startActivity(new Intent(this,ConsultationActivity.class).putExtra("demo",true));});
        Button details=new Button(this);details.setText("예시 · 기술 정보"); cardPanel.addView(details); VehicleStyle.button(details,false);
        diagnostics=new LinearLayout(this);diagnostics.setOrientation(LinearLayout.VERTICAL);diagnostics.setVisibility(View.GONE);cardPanel.addView(diagnostics);
        diagnostics.addView(demo); diagnostics.addView(export);
        latest=VehicleStyle.text(this,"",16,VehicleStyle.MUTED);diagnostics.addView(latest);
        details.setOnClickListener(v->diagnostics.setVisibility(diagnostics.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        for(Button b:new Button[]{permission,export,startRecording,stopRecording,card,demo})VehicleStyle.button(b,b==startRecording||b==card);
        column.addView(VehicleStyle.text(this,"기록은 시작 버튼으로 별도 실행합니다. 연결 상태는 주차 판정이 아닙니다.\n차량 기록 시제품 · 고장 진단 기능이 아닙니다.",16,VehicleStyle.MUTED));
        setContentView(scroll); render();
    }
    @Override public void onStart() {
        super.onStart(); visible = true; final int epoch = ++generation;
        appendSystem("activity_started");
        try {
            car = Car.createCar(this, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, (connected, isReady) -> {
                if (!visible || epoch != generation) return;
                ready = isReady; uxAllows = false;
                closeProbe();
                if (isReady) { car = connected; attachProbe(epoch); }
                else appendSystem("car_disconnected");
                render();
            });
        } catch (RuntimeException e) { ready = false; appendSystem("connection_error:"+e.getClass().getSimpleName()); render(); }
    }
    private void attachProbe(int epoch) {
        final int sourceEpoch = ++probeGeneration;
        try {
            CarPropertyManager p = (CarPropertyManager)car.getCarManager(Car.PROPERTY_SERVICE);
            CarUxRestrictionsManager u = (CarUxRestrictionsManager)car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
            if (p == null || u == null) { appendSystem("manager_unavailable"); return; }
            probe = new VehicleProbe(p, u, event -> {
                // Capture the epoch so queued events from a disconnected service cannot unlock UI.
                handler.post(() -> {
                    if (!visible || !ready || epoch != generation || sourceEpoch != probeGeneration) return;
                    append(event);
                    if ("ux".equals(event.optString("type")))
                        uxAllows = "ok".equals(event.optString("status"))
                                && event.has("requiresDO") && !event.isNull("requiresDO")
                                && !event.optBoolean("requiresDO", true)
                                && event.optInt("activeRestrictions", -1) == 0;
                    render();
                });
            });
            probe.start();
        } catch (RuntimeException e) { uxAllows = false; appendSystem("probe_error:"+e.getClass().getSimpleName()); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != 10) return;
        appendSystem(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED ? "speed_permission_granted" : "speed_permission_denied");
        // onStart creates a fresh probe if the permission dialog stopped the activity.
        if (visible && ready) { uxAllows = false; closeProbe(); attachProbe(generation); }
        render();
    }
    private void append(JSONObject event) {
        if (events.size() >= 1800) { events.removeFirst(); dropped++; }
        events.addLast(event);
    }
    private void appendSystem(String message) {
        try { append(new JSONObject().put("type", "lifecycle").put("message", message)
                .put("receivedNs", Long.toString(SystemClock.elapsedRealtimeNanos()))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private void render() {
        if (status == null) return;
        boolean allowed = visible && ready && uxAllows;
        status.setText(allowed ? "● 차량 연결됨  ·  안전하게 정차한 뒤 조작하세요." : "차량 서비스/UX 상태 확인 전 또는 제약 활성 · 조작 잠금");
        permission.setEnabled(allowed && checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED);
        permission.setVisibility(checkSelfPermission(Car.PERMISSION_SPEED)==PackageManager.PERMISSION_GRANTED?View.GONE:View.VISIBLE);
        card.setEnabled(allowed);demo.setEnabled(allowed);
        startRecording.setEnabled(allowed && checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED);
        stopRecording.setEnabled(allowed);
        export.setEnabled(allowed && !events.isEmpty());
        latest.setVisibility(allowed ? View.VISIBLE : View.GONE);
        latest.setText(allowed ? "버퍼 "+events.size()+"건 / 버린 이전 기록 "+dropped+"건\n"+(events.peekLast()==null?"":events.peekLast().toString()) : "");
    }
    private void exportLog() {
        if (!visible || !ready || !uxAllows) return;
        try {
            JSONObject document = new JSONObject().put("schemaVersion",1).put("provenance","android-probe")
                .put("vehicleOriginVerified",false).put("device",Build.DEVICE).put("sdkInt",Build.VERSION.SDK_INT)
                .put("originNs",Long.toString(originNs)).put("droppedEvents",dropped)
                .put("events",new JSONArray(events));
            pendingExport = document.toString(2);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"c01-platform-capture.json");
            startActivityForResult(intent,20);
        } catch (Exception e) { pendingExport=null; appendSystem("export_prepare_failed"); }
    }
    @Override public void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request != 20) return;
        String snapshot=pendingExport; pendingExport=null;
        if(result!=RESULT_OK || data==null || data.getData()==null || snapshot==null)return;
        // User-selected local document. Snapshot predates the system picker; no network upload.
        try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
            if(out==null)throw new IllegalStateException("no output stream");
            out.write(snapshot.getBytes(StandardCharsets.UTF_8)); appendSystem("export_completed");
        }catch(Exception e){appendSystem("export_failed:"+e.getClass().getSimpleName());}
    }
    private void closeProbe() {
        probeGeneration++;
        if(probe!=null)try{probe.close();}catch(RuntimeException ignored){}finally{probe=null;}
    }
    @Override public void onStop() {
        visible=false; ready=false; uxAllows=false; generation++;
        closeProbe();
        if(car!=null)try{car.disconnect();}catch(RuntimeException ignored){}finally{car=null;}
        appendSystem("activity_stopped_ui_probe_ended_recording_service_independent"); render(); super.onStop();
    }
}
