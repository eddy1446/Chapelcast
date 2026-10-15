/* ChapelCast 1.2 media-suite enhancements */
(function(){
  const $=s=>document.querySelector(s);
  if(location.search.includes('view=output')) return;
  const css=`
  .cc-tools{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;margin-top:8px}
  .cc-tools button{min-height:44px}.cc-live{border-color:var(--good)!important;box-shadow:0 0 0 1px var(--good) inset}
  .cc-modal{position:fixed;inset:0;background:rgba(0,0,0,.72);z-index:50;display:flex;align-items:center;justify-content:center;padding:16px}
  .cc-modal>div{width:min(760px,100%);max-height:90vh;overflow:auto;background:var(--panel);border:1px solid var(--line);border-radius:14px;padding:16px}
  .cc-slides{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:8px;margin-top:10px}.cc-slide{min-height:100px;text-align:left;white-space:pre-wrap}.cc-slide.live{outline:2px solid var(--accent)}
  .cc-chip{display:inline-block;padding:4px 8px;border:1px solid var(--line);border-radius:999px;font-size:12px;margin:2px}
  @media(max-width:760px){.cc-tools{grid-template-columns:1fr 1fr}.cc-slides{grid-template-columns:1fr 1fr}}
  `; const st=document.createElement('style');st.textContent=css;document.head.appendChild(st);
  const panel=document.createElement('section');panel.className='panel';panel.id='ccToolsPanel';panel.innerHTML=`
    <b>Media tools</b><div class="cc-tools">
      <button id="ccPresenter">🎤 Presenter mode</button><button id="ccHotkeys">⌨ Hotkeys</button>
      <button id="ccSlides">🖥 Slide grid</button><button id="ccAnnounce">📢 Announcement</button>
      <button id="ccClock">🕐 Project clock</button><button id="ccLogo">🔲 Logo text</button>
    </div><div class="help" style="margin-top:7px">F1/F2/F3/F4 control Full/Lower/Blackout/Next. Space advances the current item.</div>`;
  const aside=document.querySelector('aside'); if(aside) aside.prepend(panel);
  function modal(title,body,close=true){const m=document.createElement('div');m.className='cc-modal';m.innerHTML=`<div><div class="top"><b>${title}</b>${close?'<button class="cc-x">×</button>':''}</div>${body}</div>`;document.body.appendChild(m);m.querySelector('.cc-x')?.addEventListener('click',()=>m.remove());return m}
  $('#ccPresenter').onclick=()=>{document.body.classList.toggle('cc-presenter');alert(document.body.classList.contains('cc-presenter')?'Presenter mode on — use keyboard shortcuts.':'Presenter mode off.')};
  $('#ccHotkeys').onclick=()=>modal('ChapelCast shortcuts',`<p><span class="cc-chip">F1</span> Full projection</p><p><span class="cc-chip">F2</span> Lower third</p><p><span class="cc-chip">F3</span> Blackout</p><p><span class="cc-chip">F4</span> Next</p><p><span class="cc-chip">Space</span> Next slide/item</p><p><span class="cc-chip">Alt ←/→</span> Previous / next</p><p><span class="cc-chip">Enter</span> Project selected Scripture result</p>`);
  $('#ccSlides').onclick=()=>{let body='<div class="cc-slides">';if(typeof queue!=='undefined'&&queue.length){queue.forEach((x,i)=>body+=`<button class="cc-slide" data-i="${i}"><b>${i+1}. ${x.title}</b><br>${x.text}</button>`)}else body+='<p>No service items yet.</p>';body+='</div>';let m=modal('Service slide grid',body);m.querySelectorAll('.cc-slide').forEach(b=>b.onclick=()=>{let x=queue[+b.dataset.i];if(x){S.text=x.text;S.ref=x.ref;cur=null;saveState();m.remove()}})};
  $('#ccAnnounce').onclick=()=>{let m=modal('Quick announcement',`<textarea id="ccAT" style="width:100%;min-height:140px" placeholder="Type announcement..."></textarea><button id="ccAP" class="good" style="width:100%;margin-top:8px">Project announcement</button>`);m.querySelector('#ccAP').onclick=()=>{let t=m.querySelector('#ccAT').value.trim();if(t){S.text=t;S.ref='ANNOUNCEMENT';saveState();m.remove()}}};
  $('#ccClock').onclick=()=>{S.text=new Date().toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'});S.ref='';saveState()};
  $('#ccLogo').onclick=()=>{let m=modal('Projection label',`<input id="ccLT" style="width:100%" placeholder="Church name / event"><button id="ccLP" style="width:100%;margin-top:8px">Project label</button>`);m.querySelector('#ccLP').onclick=()=>{let t=m.querySelector('#ccLT').value.trim();if(t){S.text=t;S.ref='';saveState();m.remove()}}};
  addEventListener('keydown',e=>{if(['INPUT','TEXTAREA','SELECT'].includes(document.activeElement.tagName))return;if(e.key==='F1'){e.preventDefault();mode('full')}if(e.key==='F2'){e.preventDefault();mode('lower')}if(e.key==='F3'){e.preventDefault();mode('blank')}if(e.key==='F4'||e.key===' '){e.preventDefault();step(1)}});
})();
