(() => {
  'use strict';
  const data = window.SSCA_TUTORIAL;
  if (!data?.lessons?.length) return;
  const el = id => document.getElementById(id), video = el('video');
  const params = new URLSearchParams(location.search);
  const ui = {
    zh_cn: {form:'吸血蝙蝠',pending:'演示待重拍',title:'形态教程：',active:'主动技能',passive:'被动与特性',how:'如何触发',effect:'会发生什么',note:'小提示',previous:'上一页',next:'下一页',finish:'完成教程',contents:'目录',read:'返回页面',close:'关闭教程',back:'返回教程',enlarge:'点击此处放大视频',paired:'多视角实机对照',recipient:'受援方第一人称',observer:'实机演示：',self:'本人视角：',play:'播放演示',loading:'正在载入实机演示…',error:'视频加载失败，点击重试',hint:'视频可暂停和拖动进度 · 下方文字放大',scroll:'滚轮或上下键滚动文字',locked:'尚未解锁此能力；可以先了解触发方式。',cooldown:'基础冷却 {n} 秒 · ',closed:'教程已关闭',done:'教程已完成',return:'切回 Minecraft，继续你的冒险。',replay:'重新打开教程',wiki:'返回 Wiki'},
    en_us: {form:'Desmodus',pending:'Demonstration pending',title:'Form tutorial: ',active:'Active skill',passive:'Passive / trait',how:'How to use',effect:'What happens',note:'Tip',previous:'Previous',next:'Next',finish:'Finish',contents:'Contents',read:'Read page',close:'Close tutorial',back:'Back to tutorial',enlarge:'Click here to enlarge the video',paired:'Gameplay from multiple views',recipient:'Recipient first person',observer:'Gameplay: ',self:'First-person: ',play:'Play demonstration',loading:'Loading gameplay…',error:'Video failed to load. Retry',hint:'Pause or seek the video · Enlarge below',scroll:'Scroll or use Up / Down to read',locked:'Not unlocked yet. You can still read how it works.',cooldown:'Base cooldown {n}s · ',closed:'Tutorial closed',done:'Tutorial complete',return:'Return to Minecraft to continue your adventure.',replay:'Open tutorial again',wiki:'Back to Wiki'}
  };
  const keys = {primary:['主技能键','primary skill key'],secondary:['副技能键','secondary skill key'],sneak:['潜行键','sneak key'],use:['使用键','use key'],attack:['攻击键','attack key'],jump:['跳跃键','jump key'],sprint:['疾跑键','sprint key']};
  const state = {page:0,mode:'lesson',locale:params.get('lang')==='en_us'?'en_us':'zh_cn',indexScroll:0,rows:1,bodyScroll:0,scale:1,line:10,bodyHeight:1,portrait:false,soundBlocked:false,userMuted:false,volume:1};
  const clickSound = new Audio('ui/button-click.ogg'); clickSound.preload='none'; clickSound.volume=.4;
  const current = () => data.lessons[state.page], words = () => ui[state.locale];
  let mediaGeneration=0;
  let lessonState={};
  try { const parsed=JSON.parse(params.get('state')||'{}'); if(parsed&&typeof parsed==='object'&&!Array.isArray(parsed)) lessonState=parsed; } catch {}
  function rect(node,x,y,w,h){Object.assign(node.style,{left:x+'px',top:y+'px',width:w+'px',height:h+'px'});}
  function set(id,value){el(id).textContent=value;}
  function gesture(action){
    if(state.soundBlocked){state.soundBlocked=false;state.userMuted=false;video.muted=false;}
    clickSound.currentTime=0;clickSound.play().catch(()=>{});action();
  }
  function bind(id,action){el(id).onclick=()=>gesture(action);}
  function keyText(name){return (params.get(name)||keys[name][state.locale==='zh_cn'?0:1]).slice(0,80);}
  function copy(id,text){
    const fragment=document.createDocumentFragment();let cursor=0;
    for(const match of text.matchAll(/\{(primary|secondary|sneak|use|attack|jump|sprint)\}/g)){
      fragment.append(text.slice(cursor,match.index));const key=document.createElement('strong');key.textContent=keyText(match[1]);fragment.append(key);cursor=match.index+match[0].length;
    }
    fragment.append(text.slice(cursor));el(id).replaceChildren(fragment);
  }
  function bodyMaximum(){return Math.max(0,Math.ceil((el('body-content').scrollHeight-state.bodyHeight)/state.line));}
  function scrollbar(track,total,visible,offset){
    track.hidden=total<=visible||state.mode==='closed'||state.mode==='enlarged';
    const h=parseFloat(track.style.height)||1,thumb=Math.max(6,h*visible/Math.max(1,total));
    const y=(h-thumb)*offset/Math.max(1,total-visible);
    Object.assign(track.firstElementChild.style,{height:thumb+'px',top:(y-3)+'px'});
    track.setAttribute('aria-valuemin','0');track.setAttribute('aria-valuemax',String(Math.max(0,total-visible)));track.setAttribute('aria-valuenow',String(offset));
  }
  function updateBody(){
    state.bodyScroll=Math.max(0,Math.min(state.bodyScroll,bodyMaximum()));
    el('body-content').style.top=-state.bodyScroll*state.line+'px';
    scrollbar(el('body-scroll'),bodyMaximum()+Math.floor(state.bodyHeight/state.line),Math.floor(state.bodyHeight/state.line),state.bodyScroll);
    el('body-scroll').hidden||=state.mode!=='lesson';
    const details=lessonState[current().id], cd=Number(details?.cooldown);
    set('status',details?.locked===true?words().locked:(Number.isFinite(cd)&&cd>0&&cd<86400?words().cooldown.replace('{n}',cd.toFixed(1)):'')+(bodyMaximum()>0?words().scroll:words().hint));
  }
  function updateContents(){
    state.indexScroll=Math.max(0,Math.min(state.indexScroll,data.lessons.length-state.rows));
    const gap=state.portrait?38:25,height=state.portrait?34:22,entries=el('entries');
    entries.replaceChildren();
    for(let row=0;row<state.rows&&row+state.indexScroll<data.lessons.length;row++){
      const page=row+state.indexScroll,lesson=data.lessons[page],button=document.createElement('button');button.type='button';button.className='mc-button';
      button.textContent=(page+1)+'. '+lesson.text[state.locale].title+'  '+(lesson.kind==='ACTIVE'?words().active:words().passive);button.disabled=page===state.page;
      rect(button,0,row*gap,parseFloat(el('contents').style.width)-12,height);button.onclick=()=>gesture(()=>select(page));entries.append(button);
    }
    scrollbar(el('contents-scroll'),data.lessons.length,state.rows,state.indexScroll);el('contents-scroll').hidden||=state.mode!=='contents';
  }
  function layout(){
    const w=innerWidth,h=innerHeight;state.portrait=w<600&&h>w;
    state.scale=state.portrait?1:Math.max(1,Math.min(4,Math.floor(Math.min(w/320,h/240))));state.line=state.portrait?20:10;
    const s=state.scale,expanded=state.mode==='enlarged',closed=state.mode==='closed';
    const pw=Math.max(200,Math.min(expanded?760:closed?440:660,w/s-16));
    const ph=Math.max(160,Math.min(closed?200:state.portrait?h-16:expanded?460:370,h/s-16));
    document.documentElement.style.setProperty('--scale',s);document.documentElement.style.setProperty('--inv',1/s);
    document.documentElement.style.setProperty('--font',state.portrait?'14px':'8px');document.documentElement.style.setProperty('--line',state.line+'px');
    const panel=el('panel');panel.classList.toggle('portrait',state.portrait);panel.dataset.view=state.mode;
    Object.assign(panel.style,{left:Math.round((w-pw*s)/2)+'px',top:Math.round((h-ph*s)/2)+'px',width:pw+'px',height:ph+'px'});
    const contentTop=state.portrait?78:47,contentHeight=Math.max(24,ph-(state.portrait?170:91));
    rect(el('heading'),10,6,pw-(state.portrait?125:118),state.line+2);rect(el('counter'),pw-76,6,60,state.line+2);
    rect(el('language'),pw-(state.portrait?124:108),state.portrait?3:4,state.portrait?36:26,state.portrait?24:13);
    rect(el('kind-row'),9,state.portrait?34:22,pw-24,state.portrait?30:16);
    const dw=state.portrait?pw-24:Math.floor((pw-36)/2),dh=state.portrait?Math.min(255,Math.max(160,Math.floor(contentHeight*.46))):contentHeight;
    const mx=9,my=expanded?25:contentTop-3,mw=expanded?pw-24:dw,mh=expanded?ph-64:dh;
    rect(el('media-frame'),mx,my,mw,mh);
    const caption=mh>=62?(expanded?state.portrait?24:13:state.portrait?46:24):0;
    const vw=Math.max(1,Math.min(mw-8,(mh-caption-8)*16/9)),vh=vw*9/16;
    rect(el('player-box'),(mw-vw)/2-3,4+(mh-caption-8-vh)/2-3,vw,vh);
    el('view').hidden=!caption;el('view').style.top=(mh-caption-3)+'px';el('enlarge').hidden=expanded||!caption||Boolean(current().pending);
    const bx=state.portrait?9:9+dw+12,by=state.portrait?contentTop+dh+9:contentTop-3,bw=state.portrait?pw-24:pw-dw-36;
    state.bodyHeight=state.portrait?contentHeight-dh-12:contentHeight;
    rect(el('body'),bx,by,bw,state.bodyHeight);panel.classList.toggle('compact',state.bodyHeight<150);
    rect(el('body-scroll'),bx+bw-5,by,6,state.bodyHeight);
    set('close',state.portrait&&state.locale==='en_us'?'Close':words().close);
    const bh=state.portrait?42:20;rect(el('buttons'),9,ph-(state.portrait?54:31),pw-24,bh);
    rect(el('back'),9,ph-(state.portrait?54:31),pw-24,bh);rect(el('status'),10,ph-(state.portrait?83:44),pw-26,state.line+2);
    rect(el('contents'),9,contentTop-5,pw-24,contentHeight+2);rect(el('contents-scroll'),pw-24,contentTop-3,6,contentHeight);
    state.rows=Math.max(1,Math.floor(contentHeight/(state.portrait?38:25)));
    for(const id of ['kind-row','body','buttons','status'])el(id).hidden=expanded||closed||state.mode==='contents'&&['body','status'].includes(id);
    el('media-frame').hidden=state.mode==='contents'||closed;el('contents').hidden=state.mode!=='contents';
    el('counter').hidden=expanded||closed;el('language').hidden=expanded||closed;el('back').hidden=!expanded;el('closed').hidden=!closed;
    set('heading',expanded?current().text[state.locale].title:words().title+words().form);
    updateBody();updateContents();
  }
  function clearVideo(){mediaGeneration++;video.pause();video.removeAttribute('src');video.replaceChildren();video.load();}
  function mediaFailed(){
    if(state.mode==='closed')return;
    video.dataset.failed='true';el('loading').hidden=true;set('play-video',words().error);el('play-video').hidden=false;
  }
  function startVideo(){
    const lesson=current();state.soundBlocked=false;el('play-video').hidden=true;el('loading').hidden=false;
    clearVideo();const generation=mediaGeneration;let sourceErrors=0;video.dataset.failed='false';video.poster='media/'+lesson.media+'.jpg';video.muted=state.userMuted;video.volume=state.volume;
    video.hidden=Boolean(lesson.pending);
    if(lesson.pending){video.removeAttribute('poster');set('loading',words().pending);el('loading').hidden=false;el('play-video').hidden=true;return;}
    for(const [extension,type] of [['webm','video/webm'],['mp4','video/mp4']]){
      const source=document.createElement('source');source.src='media/'+lesson.media+'.'+extension;source.type=type;
      source.onerror=()=>{if(generation===mediaGeneration&&++sourceErrors===2)mediaFailed();};video.append(source);
    }
    video.load();
    video.play().catch(error=>{
      if(generation!==mediaGeneration||current()!==lesson||state.mode==='closed'||state.mode==='contents')return;
      if(error.name==='NotAllowedError'){
        state.soundBlocked=true;video.muted=true;
        video.play().catch(()=>{el('play-video').hidden=false;el('loading').hidden=true;});
      }else if(error.name!=='AbortError')mediaFailed();
    });
  }
  function render(reload=true){
    const t=words(),lesson=current(),text=lesson.text[state.locale];
    document.documentElement.lang=state.locale==='zh_cn'?'zh-CN':'en';document.documentElement.dataset.sscaTutorial='ready';document.documentElement.dataset.lesson=lesson.id;
    document.title=text.title+' · '+t.form+' - SSCA Wiki';
    set('counter',(state.page+1)+' / '+data.lessons.length);set('kind',lesson.kind==='ACTIVE'?t.active:t.passive);set('title',text.title);
    set('how-title',t.how);set('effect-title',t.effect);set('note-title',t.note);
    for(const id of ['trigger','effect','note'])copy(id,text[id]);
    for(const [id,key] of [['previous','previous'],['next',state.page===data.lessons.length-1?'finish':'next'],['index',state.mode==='contents'?'read':'contents'],['close','close'],['back','back'],['enlarge','enlarge'],['play-video','play'],['loading','loading'],['replay','replay'],['full-wiki','wiki']])set(id,t[key]);
    set('view',lesson.pending?t.pending:lesson.view==='paired'?t.paired:lesson.view==='target'?t.recipient:(lesson.view==='self'?t.self:t.observer)+t.form);set('language',state.locale==='zh_cn'?'EN':'中文');
    el('previous').disabled=state.page===0;layout();if(reload)startVideo();
  }
  function select(page,history=true){
    if(page<0)return;if(page>=data.lessons.length){closeTutorial(true);return;}
    state.page=page;state.mode='lesson';state.bodyScroll=0;
    if(history)window.history.pushState(null,'',location.pathname+location.search+'#'+current().id);render(true);
  }
  function toggleIndex(){
    state.mode=state.mode==='contents'?'lesson':'contents';if(state.mode==='contents')video.pause();set('index',state.mode==='contents'?words().read:words().contents);layout();
  }
  function closeTutorial(done=false){clearVideo();state.mode='closed';set('closed-title',done?words().done:words().closed);set('closed-message',words().return);layout();}
  function scroll(amount){if(state.mode==='contents'){state.indexScroll+=amount;updateContents();}else if(state.mode==='lesson'){state.bodyScroll+=amount;updateBody();}}
  bind('previous',()=>select(state.page-1));bind('next',()=>select(state.page+1));bind('index',toggleIndex);bind('close',()=>closeTutorial());
  bind('enlarge',()=>{state.mode='enlarged';layout();});bind('back',()=>{state.mode='lesson';layout();});bind('replay',()=>select(0));
  bind('language',()=>{state.locale=state.locale==='zh_cn'?'en_us':'zh_cn';params.set('lang',state.locale);history.replaceState(null,'',location.pathname+'?'+params+location.hash);render(true);});
  bind('play-video',()=>{el('play-video').hidden=true;if(video.dataset.failed==='true'||video.error||!video.querySelector('source'))startVideo();else video.play().catch(()=>{el('play-video').hidden=false;});});
  video.addEventListener('playing',()=>{el('loading').hidden=true;el('play-video').hidden=true;});
  video.addEventListener('loadeddata',()=>{el('loading').hidden=true;});
  video.addEventListener('error',mediaFailed);
  video.addEventListener('volumechange',()=>{if(state.soundBlocked&&!video.muted)state.soundBlocked=false;if(!state.soundBlocked){state.userMuted=video.muted;state.volume=video.volume;}});
  el('panel').addEventListener('wheel',event=>{if(event.target.closest('#player-box'))return;event.preventDefault();scroll(Math.sign(event.deltaY)*(state.mode==='contents'?1:3));},{passive:false});
  for(const id of ['body-scroll','contents-scroll']){
    const track=el(id);let drag;
    track.addEventListener('pointerdown',event=>{event.preventDefault();track.setPointerCapture(event.pointerId);drag={id:event.pointerId};move(event);});
    function move(event){
      if(!drag)return;const r=track.getBoundingClientRect(),fraction=Math.max(0,Math.min(1,(event.clientY-r.top)/r.height));
      if(id==='body-scroll'){state.bodyScroll=Math.round(fraction*bodyMaximum());updateBody();}else{state.indexScroll=Math.round(fraction*Math.max(0,data.lessons.length-state.rows));updateContents();}
    }
    track.addEventListener('pointermove',move);track.addEventListener('pointerup',()=>{drag=null;});track.addEventListener('pointercancel',()=>{drag=null;});
  }
  document.addEventListener('keydown',event=>{
    if(event.altKey||event.ctrlKey||event.metaKey||event.target===video&&event.key!=='Escape')return;
    if(state.mode==='closed')return;
    let action;
    if(event.key==='Escape')action=()=>{if(state.mode==='enlarged'){state.mode='lesson';layout();}else if(state.mode==='contents')toggleIndex();else closeTutorial();};
    else if(state.mode!=='enlarged'){
      const count=state.mode==='contents'?state.rows:Math.max(1,Math.floor(state.bodyHeight/state.line));
      action={ArrowRight:()=>select(state.page+1),ArrowLeft:()=>select(state.page-1),ArrowDown:()=>scroll(1),ArrowUp:()=>scroll(-1),PageDown:()=>scroll(count),PageUp:()=>scroll(-count),Home:()=>scroll(-10000),End:()=>scroll(10000)}[event.key];
    }
    if(action){event.preventDefault();gesture(action);}
  });
  function fromHash(){let id;try{id=decodeURIComponent(location.hash.slice(1));}catch{}const page=Math.max(0,data.lessons.findIndex(item=>item.id===id));if(page!==state.page||state.mode!=='lesson')select(page,false);}
  window.addEventListener('hashchange',fromHash);window.addEventListener('popstate',fromHash);
  window.addEventListener('resize',layout);document.fonts?.ready.then(layout);
  document.addEventListener('visibilitychange',()=>{if(document.hidden)video.pause();});window.addEventListener('pagehide',()=>video.pause());
  let id;try{id=decodeURIComponent(location.hash.slice(1));}catch{}state.page=Math.max(0,data.lessons.findIndex(item=>item.id===id));render(true);
})();
