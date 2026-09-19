/* ==========================================================================
   Field worker screens
   Built for one hand, outdoors, on a phone: the duty board, a route ordered by
   walking distance, an arrival check before a job can start, a running job
   timer, and a completion step that will not close without a photo.
   ========================================================================== */

const ARRIVE_RADIUS = 120;                                    // metres
const dailyGoal = () => Number(localStorage.getItem('cb_goal') || 6);

const ACTIVE_TASK = ['ASSIGNED','ACCEPTED','IN_PROGRESS'];
const isToday = v => v && new Date(v).toDateString() === new Date().toDateString();

function setDuty(on){
  state.onDuty = on;
  localStorage.setItem('cb_duty', on ? '1' : '0');
  toast(on ? 'On duty — new jobs will reach you' : 'Off duty — you will not be sent new jobs', on ? 'ok' : 'info');
  renderWorkerBoard();
}

async function fetchTasks(){
  return payload(await api('/workers/tasks?activeOnly=false')) || [];
}

/* ------------------------------------------------------------ duty board -- */
async function renderWorkerBoard(){
  const first = (state.user.name || 'there').split(' ')[0];
  shell(`
    ${state.queue.length ? `<div class="queue-flag">◷ ${state.queue.length} update${state.queue.length>1?'s':''} saved on this phone, waiting for signal.
      <button class="btn btn-sm btn-line" style="margin-left:auto" onclick="flushQueue()">Send now</button></div>` : ''}

    <div class="duty ${state.onDuty ? 'on' : ''}">
      <button class="switch ${state.onDuty ? 'on' : ''}" onclick="setDuty(${!state.onDuty})" aria-label="Duty status"><i></i></button>
      <div class="duty-copy">
        <b>${state.onDuty ? 'On duty' : 'Off duty'}</b>
        <span>${state.onDuty ? 'Visible to dispatch for new assignments.' : 'Turn this on when you start your shift.'}</span>
      </div>
      <div style="margin-left:auto" class="actions">
        <button class="btn btn-line btn-sm" onclick="go('/worker/route')">Plan my route</button>
      </div>
    </div>

    <section class="banner worker" style="margin-top:16px">
      <h1>Good to see you, ${esc(first)}.</h1>
      <p>Jobs below are yours. Accept what you can do, and close each one with a photo of the cleared spot — that photo is what lets the resident sign it off.</p>
    </section>

    <div class="head"><div><h2>Today</h2><p>Your shift at a glance.</p></div>
      <button class="btn btn-line btn-sm" onclick="changeGoal()">Change target</button></div>
    <div id="wStats">${skelCards(1)}</div>

    <div class="head"><div><h2>Your jobs</h2><p>Priority first, then oldest.</p></div>
      <button class="btn btn-line btn-sm" onclick="renderWorkerBoard()">Refresh</button></div>
    <div id="wTasks">${skelCards(3)}</div>`);

  let rows;
  try{ rows = await fetchTasks(); }
  catch(e){ byId('wTasks').innerHTML = errorState(errText(e)); byId('wStats').innerHTML = ''; return; }

  const active = rows.filter(t => ACTIVE_TASK.includes(t.taskStatus));
  const doneToday = rows.filter(t => t.taskStatus === 'COMPLETED' && isToday(t.completedAt));
  const running = rows.find(t => t.taskStatus === 'IN_PROGRESS');
  const goal = dailyGoal();
  const pct = clamp(Math.round(doneToday.length / goal * 100), 0, 100);
  const dash = 2 * Math.PI * 34;

  byId('wStats').innerHTML = `
    <div class="row-2">
      <div class="card ring-wrap">
        <div class="ring">
          <svg width="84" height="84"><circle class="bg" cx="42" cy="42" r="34"></circle>
            <circle class="fg" cx="42" cy="42" r="34" stroke-dasharray="${dash}" stroke-dashoffset="${dash - dash*pct/100}"></circle></svg>
          <b>${doneToday.length}</b>
        </div>
        <div>
          <h3>${doneToday.length} of ${goal} closed today</h3>
          <p class="muted small">${doneToday.length >= goal ? 'Target met. Anything else today is extra.' : `${goal - doneToday.length} more to hit your target.`}</p>
        </div>
      </div>
      <div class="grid-3" style="gap:12px">
        <div class="metric warn"><b>${active.length}</b><span>Open jobs</span></div>
        <div class="metric"><b>${rows.filter(t => t.taskStatus === 'COMPLETED').length}</b><span>Closed, all time</span></div>
        <div class="metric info"><b>${rows.filter(t => t.taskStatus === 'ASSIGNED').length}</b><span>Not accepted yet</span></div>
      </div>
    </div>
    ${running ? `<div class="card" style="margin-top:14px;border-color:var(--vest)">
      <div class="between">
        <div><h3>Job in progress — RPT-${String(running.reportId).padStart(4,'0')}</h3>
          <p class="muted small" style="margin:2px 0 0">${esc(running.areaName || '')} · started ${esc(timeAgo(running.startedAt))}</p></div>
        <div style="text-align:right">
          <div class="timer" id="liveTimer">00:00</div>
          <button class="btn btn-primary worker btn-sm" style="margin-top:6px" onclick="completeTask(${running.taskId})">Finish this job</button>
        </div>
      </div></div>` : ''}`;

  if(running) startTimer(running.startedAt);

  const order = {URGENT:0, CRITICAL:0, HIGH:1, MEDIUM:2, LOW:3};
  const sorted = [...rows].sort((a,b) => {
    const aa = ACTIVE_TASK.includes(a.taskStatus) ? 0 : 1;
    const bb = ACTIVE_TASK.includes(b.taskStatus) ? 0 : 1;
    if(aa !== bb) return aa - bb;
    const pa = order[a.priority] ?? 4, pb = order[b.priority] ?? 4;
    if(pa !== pb) return pa - pb;
    return new Date(a.assignedAt || 0) - new Date(b.assignedAt || 0);
  });

  byId('wTasks').innerHTML = sorted.length
    ? `<div class="docket-grid">${sorted.map(taskDocket).join('')}</div>`
    : emptyState('✓','No jobs assigned','When dispatch sends you a job it lands here. Keep your duty switch on so they can reach you.');
}

function taskDocket(t){
  const c = coordState(t.latitude, t.longitude);
  const s = t.taskStatus;
  const near = state.myPosition && c.ok ? haversine(state.myPosition.lat, state.myPosition.lon, c.lat, c.lon) : null;
  return `
    <article class="docket s-${grp(s)}">
      <div class="docket-top">
        <span class="docket-id">JOB-${String(t.taskId).padStart(4,'0')} · RPT-${String(t.reportId).padStart(4,'0')}</span>
        ${tag(s)} ${tag(t.priority)}
      </div>
      <h3>${TYPE_ICON[t.garbageType] || '•'} ${esc(words(t.garbageType))}</h3>
      <p class="desc">${esc(t.description || 'No description was added by the resident.')}</p>
      <div class="docket-meta">
        <span>⌖ ${esc(t.areaName || 'Area not set')}</span>
        ${near != null ? `<span class="dist">${fmtDist(near)} away</span>` : ''}
        <span>◔ assigned ${esc(timeAgo(t.assignedAt))}</span>
        ${c.ok ? '' : '<span class="err-text">Location invalid — report it</span>'}
      </div>
      <div class="docket-foot">
        <button class="btn btn-quiet btn-sm" onclick="go('/tasks/${t.taskId}')">Details</button>
        ${c.ok ? `<a class="btn btn-line btn-sm" target="_blank" rel="noopener"
           href="https://www.google.com/maps/dir/?api=1&destination=${c.lat},${c.lon}">Navigate</a>` : ''}
        ${s === 'ASSIGNED' ? `
          <button class="btn btn-danger btn-sm" onclick="rejectTask(${t.taskId})">Can't take it</button>
          <button class="btn btn-primary worker btn-sm" onclick="taskAction(${t.taskId},'accept','Job accepted')">Accept</button>` : ''}
        ${s === 'ACCEPTED' ? `<button class="btn btn-primary worker btn-sm" onclick="startJob(${t.taskId}, ${c.ok ? c.lat : 'null'}, ${c.ok ? c.lon : 'null'})">Start cleaning</button>` : ''}
        ${s === 'IN_PROGRESS' ? `<button class="btn btn-primary worker btn-sm" onclick="completeTask(${t.taskId})">Finish job</button>` : ''}
      </div>
    </article>`;
}

let timerHandle;
function startTimer(startedAt){
  clearInterval(timerHandle);
  const from = new Date(startedAt || Date.now()).getTime();
  const tick = () => { const el = byId('liveTimer'); if(!el) return clearInterval(timerHandle); el.textContent = clock(Date.now() - from); };
  tick(); timerHandle = setInterval(tick, 1000);
}

async function changeGoal(){
  const v = await askText({title:'Daily target', sub:'Only you see this. It drives the ring on your board.',
    label:'Jobs per shift', value:String(dailyGoal()), confirmLabel:'Save'});
  if(!v) return;
  localStorage.setItem('cb_goal', String(clamp(Number(v) || 6, 1, 40)));
  renderWorkerBoard();
}

/* ---------------------------------------------------------- task actions -- */
async function taskAction(id, action, msg){
  try{
    await api(`/tasks/${id}/${action}`, {method:'PUT'});
    toast(msg, 'ok');
    route();
  }catch(e){
    if(!navigator.onLine){
      queueAction(msg, `/tasks/${id}/${action}`, {method:'PUT'});
      toast('No signal — saved on this phone and will send itself.','info', 5000);
      route();
    }else toast(errText(e),'bad');
  }
}

async function rejectTask(id){
  const reason = await askText({title:"Hand this job back",
    sub:'Dispatch reassigns it. Say why so it goes to the right crew.',
    label:'Reason', placeholder:'Needs a truck — too large to clear by hand.',
    multiline:true, confirmLabel:'Hand it back', danger:true});
  if(!reason) return;
  try{
    await api(`/tasks/${id}/reject`, {method:'PUT', body: JSON.stringify({reason})});
    toast('Handed back to dispatch','ok');
    route();
  }catch(e){ toast(errText(e),'bad'); }
}

/* A job cannot be started from the depot. If a location fix puts the worker
   well away from the site, they are told the distance and asked to confirm —
   which keeps the timer and the record honest. */
async function startJob(id, lat, lon){
  if(lat != null && lon != null){
    try{
      const pos = await locateMe();
      const d = haversine(pos.lat, pos.lon, lat, lon);
      if(d > ARRIVE_RADIUS){
        const ok = await confirmBox({
          title:'You look about ' + fmtDist(d) + ' from the site',
          sub:'The timer starts now and the site is logged as attended. Start anyway?',
          confirmLabel:'Start anyway'});
        if(!ok) return;
      }else{
        toast(`Arrival confirmed — ${fmtDist(d)} from the pin`,'ok');
      }
    }catch{ /* no fix available; let the work continue */ }
  }
  taskAction(id, 'start', 'Cleaning started — timer running');
}

const QUICK_NOTES = ['Cleared and swept','Needed a truck','Bin replaced','Recurring spot — needs a permanent bin','Blocked access, cleared what I could'];

async function completeTask(id){
  const file = await pickFile({capture:true});
  if(!file) return toast('A photo of the cleared spot is required to close a job.','bad', 5000);
  const image = await compressImage(file);

  openModal({
    title:'Close this job',
    sub:'This photo becomes the "after" half of what the resident sees.',
    body:`
      <img class="photo" id="cPrev" src="${URL.createObjectURL(image)}" alt="Cleared site">
      <div class="field" style="margin-top:14px">
        <label for="cNotes">Note for the record</label>
        <textarea class="textarea" id="cNotes" placeholder="Anything the supervisor should know."></textarea>
        <div class="chips" style="margin-top:8px">
          ${QUICK_NOTES.map(n => `<button type="button" class="chip" onclick="addNote('${n.replace(/'/g,"\\'")}')">${n}</button>`).join('')}
        </div>
      </div>`,
    foot:`<button class="btn btn-line" onclick="closeLayer()">Cancel</button>
          <button class="btn btn-primary worker" id="cGo">Close job</button>`
  });

  byId('cGo').onclick = async () => {
    const btn = byId('cGo'); btn.disabled = true; btn.textContent = 'Sending…';
    const fd = new FormData();
    fd.append('image', image);
    const notes = byId('cNotes').value.trim();
    if(notes) fd.append('notes', notes);
    try{
      await api(`/tasks/${id}/complete`, {method:'PUT', body: fd});
      closeLayer();
      toast('Job closed — the resident has been asked to confirm it','ok', 5000);
      route();
    }catch(e){
      btn.disabled = false; btn.textContent = 'Close job';
      toast(errText(e),'bad', 5000);
    }
  };
}
function addNote(text){
  const el = byId('cNotes');
  el.value = el.value ? el.value.replace(/\s*$/,'') + '. ' + text : text;
  el.focus();
}

/* ---------------------------------------------------------- task detail -- */
async function renderTaskDetail(id){
  shell(`<a class="back" href="#/worker">‹ Duty board</a><div id="tDetail">${skelLines(5)}</div>`);
  let t;
  try{ t = payload(await api('/tasks/' + id)); }
  catch(e){ byId('tDetail').innerHTML = errorState(errText(e)); return; }

  const c = coordState(t.latitude, t.longitude);
  byId('tDetail').innerHTML = `
    <div class="between">
      <div>
        <span class="docket-id">JOB-${String(t.taskId).padStart(4,'0')}</span>
        <h1 style="font-size:clamp(23px,3vw,32px);margin:4px 0">${TYPE_ICON[t.garbageType] || '•'} ${esc(words(t.garbageType))}</h1>
        <p class="muted">Report RPT-${String(t.reportId).padStart(4,'0')} · filed ${esc(timeAgo(t.reportCreatedAt))}</p>
      </div>
      <div class="actions">${tag(t.taskStatus)} ${tag(t.priority)}</div>
    </div>

    <div class="row-2 wide-left stack" style="margin-top:16px;align-items:start">
      <section class="card">
        <h3 style="margin-bottom:10px">Get there</h3>
        <div class="map mini" id="tMap"></div>
        ${geoReadout(t.latitude, t.longitude, t.address)}
        <p class="muted" style="margin-top:14px">${esc(t.description || 'The resident did not add a description.')}</p>
        <div class="docket-meta"><span>⌖ ${esc(t.areaName || '')}</span><span>Assigned ${esc(fmtDate(t.assignedAt))}</span></div>
        <div class="actions" style="margin-top:12px">
          ${t.taskStatus === 'ASSIGNED' ? `<button class="btn btn-primary worker" onclick="taskAction(${t.taskId},'accept','Job accepted')">Accept job</button>
            <button class="btn btn-danger" onclick="rejectTask(${t.taskId})">Hand it back</button>` : ''}
          ${t.taskStatus === 'ACCEPTED' ? `<button class="btn btn-primary worker" onclick="startJob(${t.taskId}, ${c.ok?c.lat:'null'}, ${c.ok?c.lon:'null'})">Start cleaning</button>` : ''}
          ${t.taskStatus === 'IN_PROGRESS' ? `<button class="btn btn-primary worker" onclick="completeTask(${t.taskId})">Finish job</button>` : ''}
        </div>
      </section>
      <section class="card">
        <h3 style="margin-bottom:10px">What it looked like</h3>
        ${compareBlock(t.beforeImage, t.afterImage)}
        ${t.workerNotes ? `<p class="muted small" style="margin-top:10px"><b>Your note:</b> ${esc(t.workerNotes)}</p>` : ''}
        ${t.completedAt ? `<p class="hint">Closed ${esc(fmtDate(t.completedAt))}</p>` : ''}
      </section>
    </div>`;
  drawMap('tMap', [{lat:t.latitude, lon:t.longitude, group:grp(t.taskStatus), title:`JOB-${t.taskId}`, meta:words(t.taskStatus)}], {zoom:17});
}

/* ------------------------------------------------------------- my route -- */
/* Jobs ordered nearest-first from where the worker is standing, walked as a
   chain rather than straight-line from home. Saves doubling back across a ward. */
async function renderWorkerRoute(){
  shell(`
    <div class="head"><div><h2>My route</h2>
      <p>Your open jobs put in the order that covers the least ground, starting from where you are now.</p></div>
      <button class="btn btn-primary worker btn-sm" onclick="renderWorkerRoute()">Recalculate</button></div>
    <div id="routeBody">${skelLines(4)}</div>`);

  let rows;
  try{ rows = (await fetchTasks()).filter(t => ACTIVE_TASK.includes(t.taskStatus)); }
  catch(e){ byId('routeBody').innerHTML = errorState(errText(e)); return; }

  const points = rows.map(t => ({t, c: coordState(t.latitude, t.longitude)})).filter(p => p.c.ok);
  const broken = rows.length - points.length;

  if(!points.length){
    byId('routeBody').innerHTML = emptyState('➟','Nothing to route',
      rows.length ? 'Your open jobs have no usable coordinates, so they cannot be put in order.' : 'You have no open jobs right now.');
    return;
  }

  let start;
  try{ start = await locateMe(); }
  catch(e){ start = {lat: CITY.lat, lon: CITY.lon}; toast('Using the city centre as the start point — allow location for a better route.','info', 5000); }

  /* nearest-neighbour chain */
  const left = [...points], chain = [];
  let cur = start, total = 0;
  while(left.length){
    let best = 0, bestD = Infinity;
    left.forEach((p,i) => {
      const d = haversine(cur.lat, cur.lon, p.c.lat, p.c.lon);
      if(d < bestD){ bestD = d; best = i; }
    });
    const next = left.splice(best,1)[0];
    next.legDistance = bestD;
    total += bestD;
    chain.push(next);
    cur = {lat: next.c.lat, lon: next.c.lon};
  }

  const walkMin = Math.round(total / 1000 / 4.5 * 60);
  byId('routeBody').innerHTML = `
    ${broken ? `<div class="notice bad">${broken} job${broken>1?'s have':' has'} no usable coordinates and could not be routed. Open ${broken>1?'them':'it'} and tell dispatch.</div>` : ''}
    <div class="grid-3" style="margin-bottom:16px">
      <div class="metric"><b>${chain.length}</b><span>Stops</span></div>
      <div class="metric warn"><b>${(total/1000).toFixed(1)}</b><span>km of travel</span></div>
      <div class="metric info"><b>${walkMin}</b><span>minutes walking</span></div>
    </div>
    <div class="card" style="margin-bottom:16px"><div class="map" id="routeMap"></div></div>
    <div class="card">${chain.map((p,i) => `
      <div class="route-step ${p.legDistance < ARRIVE_RADIUS ? 'arrived' : ''}">
        <span class="route-num">${i+1}</span>
        <div class="rs-body">
          <div class="between" style="gap:8px">
            <b>${esc(words(p.t.garbageType))}</b>
            <span class="dist">${fmtDist(p.legDistance)}</span>
          </div>
          <small>${esc(p.t.areaName || '')} — ${esc(words(p.t.taskStatus))} · ${esc(words(p.t.priority))} priority</small>
          <div class="actions" style="margin-top:8px">
            <a class="btn btn-line btn-sm" target="_blank" rel="noopener" href="https://www.google.com/maps/dir/?api=1&destination=${p.c.lat},${p.c.lon}">Navigate</a>
            <button class="btn btn-quiet btn-sm" onclick="go('/tasks/${p.t.taskId}')">Details</button>
            ${p.t.taskStatus === 'ACCEPTED' ? `<button class="btn btn-primary worker btn-sm" onclick="startJob(${p.t.taskId}, ${p.c.lat}, ${p.c.lon})">Start</button>` : ''}
          </div>
        </div>
      </div>`).join('')}</div>`;

  const map = drawMap('routeMap', chain.map((p,i) => ({
    lat:p.c.lat, lon:p.c.lon, group:grp(p.t.taskStatus), label:String(i+1),
    title:`Stop ${i+1} — ${words(p.t.garbageType)}`, meta:p.t.areaName || ''
  })), {zoom:14});
  if(map){
    L.polyline([[start.lat,start.lon], ...chain.map(p => [p.c.lat, p.c.lon])],
      {color:'#f2b705', weight:4, opacity:.85, dashArray:'2 8'}).addTo(map);
  }
}

/* ------------------------------------------------------------- my record -- */
async function renderWorkerRecord(){
  shell(`
    <div class="head"><div><h2>My record</h2><p>What you have closed, and how steadily.</p></div></div>
    <div id="recBody">${skelLines(4)}</div>`);

  let rows;
  try{ rows = await fetchTasks(); }
  catch(e){ byId('recBody').innerHTML = errorState(errText(e)); return; }

  const done = rows.filter(t => t.taskStatus === 'COMPLETED' && t.completedAt);
  const durations = done.filter(t => t.startedAt).map(t => new Date(t.completedAt) - new Date(t.startedAt)).filter(d => d > 0 && d < 8*3600*1000);
  const median = durations.length ? durations.sort((a,b)=>a-b)[Math.floor(durations.length/2)] : 0;

  /* consecutive days ending today or yesterday */
  const days = new Set(done.map(t => new Date(t.completedAt).toDateString()));
  let streak = 0, cursor = new Date();
  if(!days.has(cursor.toDateString())) cursor.setDate(cursor.getDate() - 1);
  while(days.has(cursor.toDateString())){ streak++; cursor.setDate(cursor.getDate() - 1); }

  const byType = {};
  done.forEach(t => byType[t.garbageType] = (byType[t.garbageType] || 0) + 1);
  const topType = Object.entries(byType).sort((a,b) => b[1]-a[1])[0];
  const max = Math.max(1, ...Object.values(byType));

  const marks = [
    {label:'First job closed', got: done.length >= 1},
    {label:'10 jobs closed', got: done.length >= 10},
    {label:'50 jobs closed', got: done.length >= 50},
    {label:'Three days running', got: streak >= 3},
    {label:'A full week running', got: streak >= 7},
    {label:'Median under 30 minutes', got: median > 0 && median < 30*60*1000}
  ];

  byId('recBody').innerHTML = `
    <div class="grid-4" style="margin-bottom:16px">
      <div class="metric"><b>${done.length}</b><span>Jobs closed</span></div>
      <div class="metric warn"><b>${streak}</b><span>Day streak</span></div>
      <div class="metric info"><b>${median ? clock(median) : '—'}</b><span>Median time on site</span></div>
      <div class="metric"><b>${rows.filter(t => ACTIVE_TASK.includes(t.taskStatus)).length}</b><span>Still open</span></div>
    </div>

    <div class="row-2 stack" style="align-items:start">
      <section class="card">
        <h3 style="margin-bottom:12px">What you clear most</h3>
        ${Object.keys(byType).length ? `<div class="bars">${Object.entries(byType).sort((a,b)=>b[1]-a[1]).map(([k,v]) =>
          `<div class="bar-row b-vest"><span class="lbl">${esc(words(k))}</span>
            <span class="track"><i style="width:${v/max*100}%"></i></span><strong>${v}</strong></div>`).join('')}</div>`
          : '<p class="muted">Nothing closed yet.</p>'}
        ${topType ? `<p class="hint" style="margin-top:12px">Most of your work is ${words(topType[0]).toLowerCase()}.</p>` : ''}
      </section>
      <section class="card">
        <h3 style="margin-bottom:12px">Marks</h3>
        <div class="streak">${marks.map(m =>
          `<span class="badge-pill ${m.got ? 'earned' : 'locked'}">${m.got ? '★' : '☆'} ${m.label}</span>`).join('')}</div>
        <p class="hint" style="margin-top:14px">These are yours alone. Nothing here is shared with dispatch or used to rank crews.</p>
      </section>
    </div>

    <div class="head"><div><h2>Recently closed</h2></div></div>
    ${done.length ? `<div class="docket-grid">${done.slice(0,6).map(t => `
      <article class="docket s-done">
        <div class="docket-top"><span class="docket-id">JOB-${String(t.taskId).padStart(4,'0')}</span>${tag('COMPLETED')}</div>
        <h3>${esc(words(t.garbageType))}</h3>
        <div class="docket-meta"><span>⌖ ${esc(t.areaName || '')}</span><span>◔ ${esc(timeAgo(t.completedAt))}</span>
          ${t.startedAt ? `<span>${clock(new Date(t.completedAt) - new Date(t.startedAt))} on site</span>` : ''}</div>
        <div class="docket-foot"><button class="btn btn-line btn-sm" onclick="go('/tasks/${t.taskId}')">Open</button></div>
      </article>`).join('')}</div>` : emptyState('★','Nothing closed yet','Your finished jobs collect here with the time you spent on each.')}`;
}
