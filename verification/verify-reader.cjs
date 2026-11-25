'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/reader.html'),'utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];new vm.Script(script);
const events={},windowEvents={},calls=[],feedback=[],trace=[],classes=new Set(),timers=new Map();
let now=10000,sequence=0,selected='',pointNode=null,onReady=null;
function classList(){const values=new Set();return {add:k=>values.add(k),remove:k=>values.delete(k),toggle(k,on){if(on)values.add(k);else values.delete(k);},contains:k=>values.has(k)};}
function node(kind,data){return {dataset:data,classList:classList(),closest(s){if(s==='.annotation')return kind==='annotation'?this:null;if(s==='[data-i]')return kind==='token'?this:null;if(s==='#text')return this;return null;}};}
const tokens=Array.from({length:10},(_,i)=>node('token',{i:String(i)}));
const nodes={caption:{textContent:''},text:{innerHTML:''},selectbar:{style:{}}};
const document={documentElement:{style:{}},body:{classList:{toggle(k,on){if(on)classes.add(k);else classes.delete(k);}}},
 getElementById:id=>nodes[id],addEventListener:(name,fn)=>events[name]=fn,querySelector:()=>null,
 querySelectorAll:s=>s==='.press-selected'?tokens.filter(t=>t.classList.contains('press-selected')):s==='[data-i]'?tokens:[],
 elementFromPoint:()=>pointNode};
const win={scrollY:143,getSelection:()=>({toString:()=>selected,removeAllRanges(){selected='';}}),scrollTo(x,y){this.scrollY=y;},addEventListener:(name,fn)=>windowEvents[name]=fn};
const ctx=vm.createContext({document,window:win,innerWidth:360,innerHeight:760,Date:{now:()=>now},
 AndroidReader:{select(s,e){calls.push(['select',s,e]);trace.push(['select',now]);},selectionReady(doc,page){feedback.push([doc,page,now]);trace.push(['ready',now]);if(onReady)onReady();},toggleToolbar:()=>calls.push(['toolbar']),turnPage:d=>calls.push(['page',d]),saveScroll:(...a)=>calls.push(['scroll',...a])},
 setTimeout(fn,delay){const id=++sequence;timers.set(id,{fn,due:now+delay});return id;},clearTimeout:id=>timers.delete(id)});
vm.runInContext(script,ctx);
function advance(ms){const end=now+ms;for(;;){const hit=[...timers].filter(([,t])=>t.due<=end).sort((a,b)=>a[1].due-b[1].due)[0];if(!hit)break;timers.delete(hit[0]);now=hit[1].due;hit[1].fn();}now=end;}
function event(type,options={}){events[type]({target:tokens[4],pointerId:1,pointerType:'touch',button:0,clientX:180,clientY:300,timeStamp:now,cancelable:true,preventDefault(){},...options});}
function reset(){windowEvents.blur();calls.length=0;feedback.length=0;trace.length=0;onReady=null;pointNode=null;advance(800);}
const words=['A','forest','of','trees','grows','near','the','old','river','today.'];
const page={blocks:[{tokens:words.map((t,i)=>({i,t}))}]};
const colors=['honey','sage','sky','rose','lavender','apricot'];
const marks=colors.map((color,i)=>({start:i,end:i,style:'highlight',color,visible:1,translation:'中文'+i}));
['underline','underline-double','underline-wavy'].forEach((style,i)=>marks.push({start:6+i,end:6+i,style,color:'sage',visible:1,translation:'线型'}));
win.Reader.init(page,marks,{font:20,docId:'test',page:0,caption:'Test'});advance(60);
assert.equal(classes.has('tools-visible'),false);
for(const c of colors)assert(nodes.text.innerHTML.includes('data-color="'+c+'"'));
for(const s of ['underline-double','underline-wavy'])assert(nodes.text.innerHTML.includes(s));
assert.equal((nodes.text.innerHTML.match(/data-i=/g)||[]).length,10);

reset();event('pointerdown');advance(80);event('pointerup');
assert.deepEqual(calls,[['toolbar']],'quick tap on an English word opens tools');
event('click');assert.deepEqual(calls,[['toolbar']],'compatibility click does not toggle twice');

reset();event('pointerdown');advance(999);event('pointerup');
assert.deepEqual(calls,[],'holding under 1 second never annotates');
assert.equal(feedback.length,0,'there is no readiness vibration before the annotation deadline');
advance(200);assert.deepEqual(calls,[],'release cancels delayed selection');

reset();event('pointerdown');advance(1000);assert(tokens[4].classList.contains('press-selected'));
assert.deepEqual(feedback,[['test',0,now]],'the native readiness vibration fires only after the gesture is armed');
const readyAt=now;
event('pointerup');assert.deepEqual(calls,[['select',4,4]],'one second hold then release opens word');
assert.deepEqual(trace,[['ready',readyAt],['select',readyAt]],'release on the vibration tick selects immediately without another timeout');
assert(!tokens.some(t=>t.classList.contains('press-selected')));
event('click');assert.equal(calls.length,1);

reset();onReady=()=>event('pointerup');event('pointerdown');advance(1000);
assert.deepEqual(calls,[['select',4,4]],'releasing inside the readiness callback is already accepted');
assert.deepEqual(trace,[['ready',now],['select',now]],'arming happens before the native feedback callback');
advance(500);assert.equal(calls.length,1,'no delayed or duplicate popup after immediate release');

reset();event('pointerdown');now+=1000;event('pointerup');
assert.deepEqual(calls,[['select',4,4]],'an input release at the deadline works even if the timer was delayed');
assert.equal(feedback.length,1);advance(1);assert.equal(calls.length,1,'late timer cannot select twice');

reset();onReady=()=>event('pointerup');event('pointerdown');now+=1000;event('pointerup');
assert.deepEqual(calls,[['select',4,4]],'a reentrant release from a delayed readiness callback cannot open twice');

reset();event('pointerdown',{timeStamp:500});event('pointerup',{timeStamp:1500});
assert.deepEqual(calls,[['select',4,4]],'hold duration uses monotonic input timestamps, not wall-clock dispatch delay');

reset();event('pointerdown');now+=1500;event('pointerup',{clientY:350});
assert.deepEqual(calls,[],'a late release that moved is not a stationary long press');
assert.equal(feedback.length,0);

reset();event('pointerdown');advance(1000);event('pointercancel');event('pointerup');
assert.equal(feedback.length,1);assert.deepEqual(calls,[],'cancel after readiness still cannot open a popup');

reset();const phrase=node('annotation',{start:'2',end:'4'});event('pointerdown',{target:phrase});advance(1000);event('pointerup',{target:phrase});
assert.deepEqual(calls,[['select',2,4]],'saved phrase retains its range');

reset();event('pointerdown');advance(1000);pointNode=tokens[7];event('pointermove',{clientX:230});event('pointerup',{clientX:230});
assert.deepEqual(calls,[['select',4,7]],'hold then drag selects a phrase');

reset();event('pointerdown',{clientX:290});advance(100);event('pointermove',{clientX:75,clientY:307});event('pointerup',{clientX:75,clientY:307});
assert.deepEqual(calls,[['page',1]],'right-to-left swipe turns to next page');
event('click');assert.equal(calls.length,1);

reset();event('pointerdown',{clientX:65});advance(100);event('pointermove',{clientX:280,clientY:305});event('pointerup',{clientX:280,clientY:305});
assert.deepEqual(calls,[['page',-1]],'left-to-right swipe turns to previous page');

reset();event('pointerdown');advance(100);event('pointermove',{clientY:450});advance(1100);event('pointerup',{clientY:450});
assert.deepEqual(calls,[],'vertical scroll neither annotates nor turns page');

reset();event('pointerdown');event('pointerdown',{pointerId:2});advance(1800);event('pointerup',{clientX:60});event('pointerup',{pointerId:2,clientX:300});event('click');
assert.deepEqual(calls,[],'pinch or two-finger gesture does not turn page or open tools');

reset();event('pointerdown');advance(400);event('pointercancel');advance(1400);event('pointerup');
assert.deepEqual(calls,[],'cancelled gesture cannot open a word later');

reset();event('pointerdown',{clientX:5});advance(70);event('pointerup',{clientX:5});
assert.deepEqual(calls,[],'quick edge tap stays quiet');

win.Reader.configure({showChinese:false,toolsVisible:true,night:true});
assert(classes.has('hidden-zh')&&classes.has('tools-visible')&&classes.has('night'));
win.scrollY=302;win.Reader.updateAnnotations([{start:1,end:1,style:'highlight',color:'bad" onclick="x',visible:0,translation:'<img src=x onerror="x">'}]);
assert.equal(win.scrollY,302);assert(nodes.text.innerHTML.includes('data-color="honey"'));
assert(nodes.text.innerHTML.includes('&lt;img')&&!nodes.text.innerHTML.includes('<img'));
assert(nodes.text.innerHTML.includes(' hide"'));
console.log('PASS: vibration/release on the same tick; reentrant release; delayed timer/input timestamp boundaries; 999/1000 ms; phrase drag; swipes; scroll/pinch/cancel protection; colors, lines and Chinese annotation rendering.');
