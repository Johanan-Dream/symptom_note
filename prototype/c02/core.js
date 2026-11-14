/* Offline consultation prototype. Fixtures are synthetic; imported probe files retain unverified provenance. */
(function(root) {
  'use strict';
  const fields = {
    speedKph: {label:'속도', unit:'km/h', type:'number'},
    brakePressed: {label:'브레이크 페달', unit:'', type:'boolean'},
    steeringDeg: {label:'조향각', unit:'°', type:'number'},
    odometerKm: {label:'누적 주행거리', unit:'km', type:'number'},
    rpm: {label:'엔진 회전수', unit:'rpm', type:'number'}
  };
  function finite(x) { return typeof x === 'number' && Number.isFinite(x); }
  function validate(s) {
    if (!s || typeof s !== 'object') throw new Error('Invalid session');
    if (!['synthetic','android-probe'].includes(s.provenance)) throw new Error('Unknown provenance');
    if(s.provenance==='android-probe' && (!s.importAudit || s.importAudit.originVerified!==false)) throw new Error('Missing import audit');
    if (!finite(s.durationMs) || s.durationMs < 0 || !Array.isArray(s.samples)) throw new Error('Invalid session');
    let prev = -1;
    for (const row of s.samples) {
      if (!row || !row.values || typeof row.values !== 'object' || Array.isArray(row.values)) throw new Error('Invalid sample');
      if (!finite(row.tMs) || row.tMs <= prev || row.tMs < 0 || row.tMs > s.durationMs) throw new Error('Invalid sample timestamp');
      prev = row.tMs;
      for (const [key, value] of Object.entries(row.values)) {
        if (!Object.hasOwn(fields,key) || !value || !['ok','unavailable','permission_denied','unsupported','error'].includes(value.status)) throw new Error('Invalid signal');
        if (value.status === 'ok' && (typeof value.value !== fields[key].type || (fields[key].type === 'number' && !finite(value.value)))) throw new Error('Invalid signal value');
      }
    }
  }
  function buildReport(session, eventMs, note='', beforeMs=30000, afterMs=30000) {
    validate(session);
    if (!finite(eventMs) || eventMs < 0 || eventMs > session.durationMs || !finite(beforeMs) || !finite(afterMs) || beforeMs < 0 || afterMs < 0) throw new Error('Invalid event/window');
    const start = Math.max(0,eventMs-beforeMs), end = Math.min(session.durationMs,eventMs+afterMs);
    const rows = session.samples.filter(x=>x.tMs >= start && x.tMs <= end);
    const signals = {};
    for (const [key, def] of Object.entries(fields)) {
      const observed = rows.filter(r=>r.values[key]?.status === 'ok');
      const failures = [...new Set(rows.map(r=>r.values[key]?.status || 'not_collected').filter(x=>x !== 'ok'))];
      const prior = rows.filter(r=>r.tMs <= eventMs && r.values[key]).at(-1);
      const age = prior ? eventMs-prior.tMs : null;
      // 2 seconds is a conservative DEMO threshold for sampled data, not a Pleos policy.
      const atEvent = prior?.values[key]?.status === 'ok' && age <= 2000 ? prior.values[key].value : null;
      const atStatus = !prior ? 'not_collected' : prior.values[key].status !== 'ok' ? prior.values[key].status : age > 2000 ? 'stale' : 'ok';
      signals[key] = {...def, atEvent, atStatus, ageMs:age, validSamples:observed.length,
        failures, range: def.type === 'number' && observed.length ? {min:Math.min(...observed.map(r=>r.values[key].value)),max:Math.max(...observed.map(r=>r.values[key].value))} : null};
    }
    const points = [start,...rows.map(r=>r.tMs),end];
    const gaps = points.slice(1).map((t,i)=>({startMs:points[i],endMs:t})).filter(x=>x.endMs-x.startMs > 2000);
    return {schemaVersion:1,provenance:session.provenance, ...(session.importAudit?{importAudit:JSON.parse(JSON.stringify(session.importAudit))}:{}), disclaimer:session.provenance==='synthetic'?'합성 데이터 시제품 — 실차·Pleos 연동·정비 효용 미검증':'가져온 Android 관측 파일 — 출처·실차 여부·정비 효용 미검증',
      eventMs, eventSource:'simulation_marker', sessionElapsedMs:eventMs,
      requestedWindow:{beforeMs,afterMs}, capturedWindow:{startMs:start,endMs:end},
      truncatedBefore:start>eventMs-beforeMs,truncatedAfter:end<eventMs+afterMs,
      sampleCount:rows.length,gaps,signals, userNote:{source:'user_entered',text:String(note)},
      audio:{status:'not_collected'},diagnosis:null,
      samples:JSON.parse(JSON.stringify(rows))};
  }
  function fixture(name='normal') {
    const samples=[];
    for(let t=0;t<=80000;t+=1000) {
      if(name==='gap' && t>=35000 && t<=45000) continue;
      const values={speedKph:{status:'ok',value:t<20000? t/1000*2:50+(t%7000)/1000},
        brakePressed:{status:'ok',value:name==='brake-outside'?(t>=20000&&t<=23000):(t>=39000&&t<=42000)},
        steeringDeg:{status:name==='denied'?'permission_denied':'ok',value:12},
        odometerKm:{status:'ok',value:12000+t/3600000*45},rpm:{status:'unsupported'}};
      if(name==='unavailable' && t>=39000 && t<=41000) values.speedKph={status:'unavailable'};
      samples.push({tMs:t,values});
    }
    return {provenance:'synthetic',durationMs:80000,samples};
  }
  function escapeHtml(s) { return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
  function reportHtml(r) {
    const statuses={ok:'기록됨',stale:'갱신 지연',not_collected:'미수집',unavailable:'값 없음',unsupported:'미지원',permission_denied:'권한 없음'};
    return `<h2>정비 상담용 사건 카드</h2><p class="warning">${escapeHtml(r.disclaimer)}</p>
      <p>기록 시작 후 ${r.eventMs/1000}초 · 앞뒤 구간 ${r.capturedWindow.startMs/1000}–${r.capturedWindow.endMs/1000}초</p>
      <p>세션 앞부분 부족: ${r.truncatedBefore?'있음':'없음'} / 뒷부분 부족: ${r.truncatedAfter?'있음':'없음'} / 데이터 공백: ${r.gaps.length}곳</p>
      <table><thead><tr><th>항목</th><th>시점 값</th><th>구간 내 유효값 범위</th><th>상태</th></tr></thead><tbody>${Object.values(r.signals).map(s=>`<tr><td>${s.label}</td><td>${s.atEvent===null?'—':typeof s.atEvent==='boolean'?(s.atEvent?'눌림':'해제'):s.atEvent.toFixed(1)+' '+s.unit}</td><td>${s.range?s.range.min.toFixed(1)+'–'+s.range.max.toFixed(1):'—'}</td><td>${statuses[s.atStatus]}</td></tr>`).join('')}</tbody></table>
      <h3>차주 설명 · 수동 입력</h3><p style="white-space:pre-wrap">${escapeHtml(r.userNote.text)||'미입력'}</p><p>원음: 미수집 · 고장 진단: 제공하지 않음</p>
      <details><summary>기록된 시점 목록</summary><p>${r.samples.map(x=>x.tMs/1000+'초').join(', ')}</p></details>`;
  }
  const detailLabels={symptom:'증상·소리',location:'느껴진 위치',conditions:'차주가 기억하는 발생 조건',frequency:'처음 발생·반복 빈도',changes:'최근 정비·장착 변경'};
  function summarizeSamples(rows) {
    return Object.fromEntries(Object.entries(fields).map(([key,def])=>{
      const valid=rows.filter(row=>row.values[key]?.status==='ok');
      return [key,{label:def.label,unit:def.unit,validSamples:valid.length,
        range:def.type==='number'&&valid.length?{min:Math.min(...valid.map(row=>row.values[key].value)),max:Math.max(...valid.map(row=>row.values[key].value))}:null,
        trueAtMs:def.type==='boolean'?valid.filter(row=>row.values[key].value===true).map(row=>row.tMs):[],
        falseAtMs:def.type==='boolean'?valid.filter(row=>row.values[key].value===false).map(row=>row.tMs):[],
        missing:rows.filter(row=>row.values[key]?.status!=='ok').map(row=>({tMs:row.tMs,status:row.values[key]?.status||'not_collected'}))}];
    }));
  }
  function timeContext(rows,startMs,endMs) {
    const groups=[['before','회상 구간 전',rows.filter(r=>r.tMs<startMs)],
      ['recalled','회상한 증상 구간',rows.filter(r=>r.tMs>=startMs&&r.tMs<=endMs)],
      ['after','회상 구간 후',rows.filter(r=>r.tMs>endMs)]];
    return {interpolation:false,causalInference:false,
      scopes:groups.map(([id,label,samples])=>({id,label,sampleCount:samples.length,signals:summarizeSamples(samples)}))};
  }
  function buildConsultation(session,startMs,endMs,details={}) {
    validate(session);
    if(!finite(startMs)||!finite(endMs)||startMs<0||endMs<startMs||endMs>session.durationMs) throw new Error('증상 구간을 확인하세요');
    if(!details || typeof details!=='object'||Array.isArray(details)) throw new Error('Invalid details');
    const descriptions={};
    for(const [key,label] of Object.entries(detailLabels)) {
      const value=details[key]??'';
      if(typeof value!=='string'||value.length>1500) throw new Error('각 설명은 1500자 이하로 입력하세요');
      descriptions[key]={label,text:value.trim(),source:'user_entered'};
    }
    const report=buildReport(session,startMs,'',30000,endMs-startMs+30000);
    delete report.eventMs;delete report.eventSource;delete report.sessionElapsedMs;delete report.userNote;
    report.schemaVersion=2;
    report.symptomWindow={startMs,endMs,source:'parked_user_recall',precision:'unverified'};
    report.descriptions=descriptions;report.requestedWindow={beforeMs:30000,afterMs:30000};
    report.timeContext=timeContext(report.samples,startMs,endMs);
    for(const signal of Object.values(report.signals)) {
      delete signal.atEvent;delete signal.atStatus;delete signal.ageMs;
      if(!report.sampleCount)signal.failures=['not_collected'];
      signal.scope='context_window_not_proven_symptom_cause';
    }
    report.disclaimer=session.provenance==='synthetic'?'합성 데이터 시제품 — 차주가 회상한 구간이며 실제 차량 기록·고장 진단이 아닙니다.':'가져온 Android 관측 파일 — 파일의 출처·진위 및 실차 여부는 미검증입니다. 차주 회상 구간이며 고장 진단이 아닙니다.';
    return report;
  }
  function consultationHtml(r) {
    const e=escapeHtml;
    const names={error:'수집 오류',not_collected:'미수집',unavailable:'값 없음',unsupported:'미지원',permission_denied:'권한 없음'};
    return `<h2>정비 상담 기록 · 증상노트</h2><p class="warning">${e(r.disclaimer)}</p>
    ${r.importAudit?`<p>원본 버퍼 삭제: ${r.importAudit.droppedEvents}건 · 기록 시작 전 표본 제외: ${r.importAudit.excludedBeforeStart}건 · 동일 측정 반복: ${r.importAudit.duplicateTimestamps}건 · 순서 뒤바뀜: ${r.importAudit.outOfOrder}건. ${r.importAudit.activeAtCheckpoint?'기록 중 저장된 스냅샷으로 이후 데이터는 포함하지 않습니다.':''} ${r.importAudit.storageError?'이전 저장 오류가 보고되어 완전성을 보장하지 않습니다.':''}</p>`:''}
    <h3>1. 차주 설명</h3><p>정차 후 회상한 증상 구간: 기록 시작 후 ${r.symptomWindow.startMs/1000}–${r.symptomWindow.endMs/1000}초. 정확한 발생 시각은 검증되지 않았습니다.</p>
    <dl>${Object.values(r.descriptions).map(d=>`<dt>${e(d.label)} <small>차주 입력</small></dt><dd>${e(d.text)||'미입력'}</dd>`).join('')}</dl>
    ${timelineHtml(r)}
    <details><summary>주변 전체 구간의 참고 요약</summary><p>${r.capturedWindow.startMs/1000}–${r.capturedWindow.endMs/1000}초의 관측값 범위입니다. 증상 순간의 값이나 원인으로 해석하지 않습니다.</p>
    <table><thead><tr><th>항목</th><th>관측 범위</th><th>유효 표본</th><th>누락·제한</th></tr></thead><tbody>${Object.values(r.signals).map(s=>`<tr><td>${e(s.label)}</td><td>${s.range?e(s.range.min.toFixed(1)+'–'+s.range.max.toFixed(1)+' '+s.unit):s.type==='boolean'&&s.validSamples?'구간에 상태 기록 있음':'—'}</td><td>${s.validSamples}</td><td>${s.failures.map(f=>e(names[f]||f)).join(', ')||'표본상 없음'}</td></tr>`).join('')}</tbody></table>
    </details><h3>3. 기록 한계</h3><p>앞부분 부족: ${r.truncatedBefore?'있음':'없음'} · 뒷부분 부족: ${r.truncatedAfter?'있음':'없음'} · 데이터 공백: ${r.gaps.length}곳</p>
    <p>원음: 미수집. RPM 등 지원되지 않은 값은 추정하지 않았습니다. 증상 재현·고장 원인·수리 필요 여부는 이 카드로 판정하지 않습니다.</p>`;
  }
  function timelineHtml(r) {
    const e=escapeHtml,selected=r.timeContext.scopes.find(s=>s.id==='recalled');
    const {startMs,endMs}=r.symptomWindow;
    const seconds=times=>times.length?times.map(t=>+(t/1000).toFixed(3)).join(', ')+'초':'없음';
    const range=s=>s.range?`${s.range.min.toFixed(1)}–${s.range.max.toFixed(1)} ${s.unit}`:'유효 표본 없음';
    const b=selected.signals.brakePressed;
    const statusNames={error:'수집 오류',unavailable:'값 없음',permission_denied:'권한 없음',unsupported:'미지원',not_collected:'미수집'};
    const value=(row,key)=>{const s=row.values[key];if(!s||s.status!=='ok')return statusNames[s?.status||'not_collected'];return typeof s.value==='boolean'?(s.value?'눌림':'해제'):s.value.toFixed(1);};
    const zone=t=>t<startMs?'전':t>endMs?'후':'회상 구간';
    const points=r.samples.filter(row=>row.values.speedKph?.status==='ok');
    const left=50,right=730,top=26,bottom=158;
    const from=r.capturedWindow.startMs,to=r.capturedWindow.endMs;
    const x=t=>left+(t-from)/Math.max(1,to-from)*(right-left);
    const max=Math.max(1,...points.map(row=>Math.abs(row.values.speedKph.value)));
    const negative=points.some(row=>row.values.speedKph.value<0);
    const min=negative?-max:0;
    const y=v=>bottom-(v-min)/(max-min)*(bottom-top);
    // Discrete points only: never draw a line through a gap or unavailable signal.
    const chart=`<svg viewBox="0 0 780 205" role="img" aria-label="속도 관측 표본. 파란 영역은 회상 구간이며 선으로 보간하지 않습니다." style="width:100%;max-width:900px"><rect x="${x(startMs)}" y="${top}" width="${Math.max(1,x(endMs)-x(startMs))}" height="${bottom-top}" fill="#dcecff"/><path d="M50 26 V158 H730" fill="none" stroke="#586c7b"/><text x="3" y="24" font-size="12">km/h</text><text x="5" y="42" font-size="12">${max.toFixed(1)}</text><text x="5" y="158" font-size="12">${min.toFixed(1)}</text>${points.map(row=>`<circle cx="${x(row.tMs)}" cy="${y(row.values.speedKph.value)}" r="2.7" fill="#174f7c"><title>${row.tMs/1000}초: ${row.values.speedKph.value} km/h</title></circle>`).join('')}<text x="50" y="183" font-size="12">${from/1000}초</text><text x="650" y="183" font-size="12">${to/1000}초</text><text x="50" y="201" font-size="12">파란 영역: 차주 회상 ${startMs/1000}–${endMs/1000}초 · 점: 기록된 표본</text></svg>`;
    return `<h3>2. 회상 구간과 시간대별 관측 · ${r.provenance==='synthetic'?'합성 예시':'가져온 기록 (출처 미검증)'}</h3><p>시간은 기록 시작 기준입니다. 회상 구간에 포함된 표본일 뿐, 소리가 난 정확한 순간이나 원인으로 해석하지 않습니다.</p>
      <p><strong>회상 구간의 속도 표본:</strong> ${e(range(selected.signals.speedKph))} (${selected.signals.speedKph.validSamples}개).<br><strong>브레이크 눌림 관측 시각:</strong> ${e(seconds(b.trueAtMs))}.<br><strong>브레이크 해제 관측 시각:</strong> ${e(seconds(b.falseAtMs))}.</p>
      ${!selected.sampleCount?'<p class="warning">회상 구간에 관측 표본이 없어 운행 조건을 판단할 수 없습니다.</p>':''}
      <p>위 시각은 표본 목록입니다. 연속 작동 시간이나 증상과의 인과관계를 뜻하지 않습니다. 기록이 없으면 상태를 판정할 수 없습니다.</p>${chart}
      <table><thead><tr><th>시간 범위</th><th>표본 수</th><th>속도 범위</th><th>브레이크 눌림/해제 표본 수</th></tr></thead><tbody>${r.timeContext.scopes.map(s=>`<tr><td>${e(s.label)}</td><td>${s.sampleCount}</td><td>${e(range(s.signals.speedKph))}</td><td>${s.signals.brakePressed.trueAtMs.length} / ${s.signals.brakePressed.falseAtMs.length}</td></tr>`).join('')}</tbody></table>
      <details><summary>시간대별 원본 표본 (${r.samples.length}개)</summary><table><thead><tr><th>초 / 구간</th><th>속도 km/h</th><th>브레이크</th><th>조향각 °</th></tr></thead><tbody>${r.samples.map(row=>`<tr${zone(row.tMs)==='회상 구간'?' style="background:#edf5ff"':''}><td>${row.tMs/1000} / ${zone(row.tMs)}</td><td>${e(value(row,'speedKph'))}</td><td>${e(value(row,'brakePressed'))}</td><td>${e(value(row,'steeringDeg'))}</td></tr>`).join('')||'<tr><td colspan="4">관측 표본 없음</td></tr>'}</tbody></table></details>`;
  }
  function consultationDocument(r) {
    return '<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>증상노트 · 상담 기록</title><style>body{font-family:system-ui,sans-serif;max-width:850px;margin:32px auto;padding:20px;color:#182b3a}table{width:100%;border-collapse:collapse}td,th{padding:10px;text-align:left;border-bottom:1px solid #ccd}dt{font-weight:bold;margin-top:12px}dd{margin:4px 0 12px;white-space:pre-wrap;overflow-wrap:anywhere}.warning{background:#fff0ca;padding:12px}small{font-weight:normal}</style>'+consultationHtml(r)+'</html>';
  }
  function importProbe(doc) {
    if(!doc || doc.schemaVersion!==1 || doc.provenance!=='android-probe' || !Array.isArray(doc.events) || doc.events.length>7200) throw new Error('지원하지 않는 관측 파일');
    const ns=v=>{if(typeof v!=='string'||!/^\d{1,20}$/.test(v))throw new Error('잘못된 나노초 시각');return BigInt(v);};
    const origin=ns(doc.originNs), checkpoint=ns(doc.checkpointNs);
    const span=checkpoint-origin;
    if(span<0n||span>86400000000000n)throw new Error('기록 길이 범위 오류');
    if(!Number.isSafeInteger(doc.droppedEvents)||doc.droppedEvents<0)throw new Error('누락 건수 오류');
    const durationMs=Number(span)/1e6, samples=[];
    const audit={originVerified:false,timeBase:'elapsedRealtimeNanos',originNs:doc.originNs,checkpointNs:doc.checkpointNs,
      droppedEvents:doc.droppedEvents,excludedBeforeStart:0,duplicateTimestamps:0,outOfOrder:0,
      activeAtCheckpoint:doc.activeAtCheckpoint===true,storageError:doc.previousStorageError??null};
    const seen=new Map();let last=-1n;
    for(const event of doc.events) {
      if(!event||typeof event!=='object')throw new Error('잘못된 관측 이벤트');
      if(event.type!=='speed')continue;
      const received=ns(event.receivedNs);
      if(received<origin||received>checkpoint)throw new Error('수신 시각 범위 오류');
      if(!['ok','unavailable','permission_denied','unsupported','error'].includes(event.status))throw new Error('알 수 없는 수집 상태');
      const measured=event.timestampNs==null?null:ns(event.timestampNs);
      if(event.status==='ok' && (measured===null||!finite(event.valueMps)))throw new Error('속도 값 또는 측정 시각 오류');
      if(measured!==null && measured>received)throw new Error('측정 시각이 수신 시각보다 늦음');
      const t=measured??received;
      if(t<origin){audit.excludedBeforeStart++;continue;}
      if(t<last)audit.outOfOrder++;last=t;
      const signature=JSON.stringify([event.status,event.status==='ok'?event.valueMps:null]);
      if(seen.has(t.toString())) {
        if(seen.get(t.toString())!==signature)throw new Error('중복 측정 시각의 값이 서로 다릅니다');
        audit.duplicateTimestamps++;continue;
      }
      seen.set(t.toString(),signature);
      const signal={status:event.status};
      if(event.status==='ok'){signal.value=event.valueMps*3.6;if(!finite(signal.value))throw new Error('속도 변환 범위 오류');}
      samples.push({tMs:Number(t-origin)/1e6,values:{speedKph:signal},sourceTimestampNs:event.timestampNs??null,receivedNs:event.receivedNs,timeSource:measured===null?'receipt':'measurement'});
    }
    samples.sort((a,b)=>a.tMs-b.tMs);
    const session={provenance:'android-probe',durationMs,samples,importAudit:audit};validate(session);return session;
  }
  const api={importProbe,buildReport,fixture,reportHtml,buildConsultation,consultationHtml,consultationDocument};
  if(typeof module!=='undefined') module.exports=api;
  root.C02=api;
})(typeof globalThis!=='undefined'?globalThis:this);
