'use strict';
let current, imported=null, importError=false, importSequence=0;
const keys=['symptom','location','conditions','frequency','changes'];
const el=id=>document.getElementById(id);
function render(){
  try{
    if(importError)throw new Error('관측 파일 가져오기에 실패했습니다. 다시 선택하거나 합성 예시로 돌아가세요');
    const a=el('start').value,b=el('end').value;
    if(!a.trim()||!b.trim())throw new Error('증상 구간을 입력하세요');
    current=C02.buildConsultation((imported||C02.fixture(el('scenario').value)),Number(a)*1000,Number(b)*1000,Object.fromEntries(keys.map(k=>[k,el(k).value])));
    el('report').innerHTML=C02.consultationHtml(current);el('error').textContent='';return true;
  }catch(e){current=null;el('report').textContent='';el('error').textContent='입력 오류: '+e.message;return false;}
}
function download(content,type,name){const url=URL.createObjectURL(new Blob([content],{type}));const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
el('build').onclick=render;
el('export').onclick=()=>{if(render())download(JSON.stringify(current,null,2),'application/json','C02-'+current.provenance+'-consultation.json');};
el('html').onclick=()=>{if(render())download(C02.consultationDocument(current),'text/html;charset=utf-8','C02-'+current.provenance+'-consultation.html');};
el('print').onclick=()=>{if(render())window.print();};
el('example').onclick=()=>{const values=['간헐적인 달그락 소리 — 합성 설명 예시','대시보드 쪽 같지만 확실하지 않음','주행 중 발생. 원인·정확한 조건은 모름','지난주부터 가끔 들림 — 합성 설정','기억나는 변경 없음 — 합성 설정'];keys.forEach((k,i)=>el(k).value=values[i]);render();};
el('clear').onclick=()=>{keys.forEach(k=>el(k).value='');document.querySelectorAll('[data-note]').forEach(b=>b.setAttribute('aria-pressed','false'));render();};
render();

el('import-file').onchange=async()=>{
  const sequence=++importSequence;
  current=null;imported=null;importError=true;el('report').textContent='';
  try {
    const file=el('import-file').files[0];if(!file)return;
    if(file.size>8*1024*1024)throw new Error('파일은 8MB 이하여야 합니다');
    const text=await file.text();if(sequence!==importSequence)return;
    imported=C02.importProbe(JSON.parse(text));importError=false;
    el('scenario').disabled=true;
    for(const id of ['start','end'])el(id).max=String(imported.durationMs/1000);
    el('start').value='0';el('end').value=String(imported.durationMs/1000);
    el('session-info').textContent='가져온 기록: '+(imported.durationMs/1000)+'초 / 속도 표본 '+imported.samples.length+'개 / 버퍼에서 삭제된 이벤트 '+imported.importAudit.droppedEvents+'개. 출처 미검증. 브레이크·조향각은 미수집.';
    keys.forEach(k=>el(k).value='');render();
  }catch(e){if(sequence!==importSequence)return;imported=null;current=null;el('scenario').disabled=false;el('session-info').textContent='가져오기 실패. 합성 예시로 돌아간 뒤 다시 선택하세요.';el('error').textContent=e.message;}
};
el('use-fixture').onclick=()=>{importSequence++;imported=null;importError=false;el('import-file').value='';el('scenario').disabled=false;el('start').max=el('end').max='80';el('start').value='35';el('end').value='45';el('session-info').textContent='합성 세션: 0–80초';render();};

// Quick notes are owner's descriptions, never inferred vehicle measurements.
for(const button of document.querySelectorAll('[data-note]'))button.onclick=()=>{
 const field=el(button.dataset.field),note=button.dataset.note;
 const lines=field.value.split('\n');
 if(!lines.includes(note))field.value=(field.value?field.value+'\n':'')+note;
 if(field.value.length>1500)field.value=field.value.slice(0,1500);
 button.setAttribute('aria-pressed',String(field.value.split('\n').includes(note)));
};
el('range-all').onclick=()=>{el('start').value='0';el('end').value=el('end').max;};
el('range-last').onclick=()=>{const end=Number(el('end').max);el('start').value=String(Math.max(0,end-30));el('end').value=String(end);};
