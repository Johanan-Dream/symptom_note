const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const C02=require('./core.js');
function setup(){
 const html=fs.readFileSync(__dirname+'/index.html','utf8');const elements={};
 for(const [,id] of html.matchAll(/id="([^"]+)"/g))elements[id]={value:'',textContent:'',innerHTML:'',max:'80'};
 elements.scenario.value='normal';elements.start.value='0';elements.end.value='80';
 const buttons=[...html.matchAll(/data-field="([^"]+)" data-note="([^"]+)"/g)].map(([,field,note])=>({dataset:{field,note},setAttribute(k,v){this[k]=v;}}));
 vm.runInNewContext(fs.readFileSync(__dirname+'/app.js','utf8'),{C02,document:{getElementById:id=>elements[id],querySelectorAll:()=>buttons},setTimeout});
 return {elements,buttons};
}
test('quick selection keeps typed descriptions, avoids repeated notes, creates owner-attributed card',()=>{
 const {elements:e,buttons:b}=setup();e.symptom.value='직접 적은 설명';b[0].onclick();b[0].onclick();
 assert.equal(e.symptom.value,'직접 적은 설명\n달그락 소리가 들림');assert.equal(b[0]['aria-pressed'],'true');
 assert.equal(e.build.onclick(),true);assert.match(e.report.innerHTML,/직접 적은 설명/);
});
test('last thirty seconds never produces negative bounds for short recordings',()=>{
 const {elements:e}=setup();e.end.max='0.863312792';e['range-last'].onclick();
 assert.equal(e.start.value,'0');assert.equal(e.end.value,'0.863312792');
 e.end.max='80';e['range-last'].onclick();assert.equal(e.start.value,'50');
 e['range-all'].onclick();assert.equal(e.start.value,'0');assert.equal(e.end.value,'80');
});
test('clear resets quick notes and selection state',()=>{
 const {elements:e,buttons:b}=setup();b[0].onclick();e.clear.onclick();assert.equal(e.symptom.value,'');assert.equal(b[0]['aria-pressed'],'false');
});
