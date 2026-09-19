/* ==========================================================================
   Citizen portal + screens shared with the other roles
   (report detail, bins, updates)
   ========================================================================== */

/* ------------------------------------------------------------------- home -- */
async function renderCitizenHome(){
  const first = (state.user.name || 'there').split(' ')[0];
  shell(`
    <section class="banner citizen">
      <h1>Hello ${esc(first)} — what needs clearing today?</h1>
      <p>Photograph it, drop a pin, send it. You will get an update at every step, and the last word on whether it was cleaned properly is yours.</p>
      <div class="actions">
        <button class="btn btn-primary" onclick="go('/report')">Report garbage</button>
        <button class="btn btn-line" style="color:#fff;border-color:rgba(255,255,255,.3)" onclick="go('/my-reports')">See my reports</button>
      </div>
    </section>

    <div class="head"><div><h2>Your activity</h2><p>Counts from every report you have filed.</p></div></div>
    <div class="grid-4" id="myStats">${skelCards(1)}</div>

    <div class="head"><div><h2>Still open</h2><p>Reports of yours that are not finished yet.</p></div>
      <button class="btn btn-line btn-sm" onclick="go('/my-reports')">View all</button></div>
    <div id="openList">${skelCards(2)}</div>

    <div class="head"><div><h2>Reported near you</h2><p>Check here before filing — someone may have already flagged it.</p></div>
      <button class="btn btn-line btn-sm" onclick="loadNearbyReports()">Use my location</button></div>
    <div class="card"><div class="map" id="nearMap"></div>
      <div class="map-legend">
        <span><i style="background:var(--vest)"></i>Waiting</span>
        <span><i style="background:var(--dry)"></i>Assigned</span>
        <span><i style="background:#e08700"></i>Being cleaned</span>
        <span><i style="background:var(--wet)"></i>Done</span>
      </div>
    </div>`);

  try{
    const p = payload(await api('/reports/my?page=0&size=100'));
    const rows = p.content || [];
    const open = rows.filter(r => !['VERIFIED','CLOSED','REJECTED'].includes(r.status));
    const waiting = rows.filter(r => ['CLEANING_COMPLETED','VERIFICATION_PENDING'].includes(r.status));
    const done = rows.filter(r => ['VERIFIED','CLOSED'].includes(r.status));

    byId('myStats').innerHTML = [
      ['Filed', rows.length, ''],
      ['Open', open.length, 'warn'],
      ['Needs your check', waiting.length, 'bad'],
      ['Closed', done.length, '']
    ].map(([label, n, cls]) => `<div class="metric ${cls}"><b>${n}</b><span>${label}</span>
      <div class="spark"><i style="width:${rows.length ? Math.round(n/rows.length*100) : 0}%"></i></div></div>`).join('');

    if(waiting.length){
      byId('openList').insertAdjacentHTML('beforebegin',
        `<div class="notice info">${waiting.length} cleanup${waiting.length>1?'s are':' is'} waiting for you to confirm the work.</div>`);
    }
    byId('openList').innerHTML = open.length
      ? `<div class="docket-grid">${open.slice(0,4).map(reportDocket).join('')}</div>`
      : emptyState('✓','Nothing pending','Every report you have filed has been closed out.',
          `<button class="btn btn-primary" onclick="go('/report')">Report something new</button>`);
  }catch(e){
    byId('myStats').innerHTML = '';
    byId('openList').innerHTML = errorState(errText(e));
  }
  loadNearbyReports(true);
}

async function loadNearbyReports(quiet){
  try{
    if(!quiet) toast('Finding your location…','info',1500);
    const pos = await locateMe().catch(() => null);
    const path = pos
      ? `/reports/nearby?latitude=${pos.lat}&longitude=${pos.lon}&radius=3`
      : '/reports?page=0&size=60';
    const res = payload(await api(path));
    const rows = Array.isArray(res) ? res : (res.content || []);
    drawMap('nearMap', rows.map(r => ({
      lat:r.latitude, lon:r.longitude, group:grp(r.status), label:'',
      title:`#${r.id} ${words(r.garbageType)}`,
      meta:`${words(r.status)}${r.distanceMetres != null ? ' · ' + fmtDist(r.distanceMetres) : ''}`,
      href:`#/reports/${r.id}`
    })), {zoom: pos ? 14 : 12, emptyText:'No open reports have valid coordinates yet.'});
  }catch(e){
    if(!quiet) toast(errText(e),'bad');
  }
}

/* --------------------------------------------------------------- docket UI -- */
function reportDocket(r, opts = {}){
  const c = coordState(r.latitude, r.longitude);
  return `
    <article class="docket s-${grp(r.status)}">
      <div class="docket-top">
        <span class="docket-id">RPT-${String(r.id).padStart(4,'0')}</span>
        ${tag(r.status)} ${r.priority ? tag(r.priority) : ''}
        ${r.roadBlocked ? '<span class="tag t-bad">Road blocked</span>' : ''}
      </div>
      <h3>${TYPE_ICON[r.garbageType] || '•'} ${esc(words(r.garbageType))}</h3>
      <p class="desc">${esc(r.description || 'No description was added.')}</p>
      <div class="docket-meta">
        <span>⌖ ${esc(r.areaName || 'Area not set')}</span>
        <span>◔ ${esc(timeAgo(r.createdAt))}</span>
        ${c.ok ? `<span class="mono">${c.lat.toFixed(4)}, ${c.lon.toFixed(4)}</span>` : '<span class="err-text">Location invalid</span>'}
        ${r.workerName ? `<span>⚒ ${esc(r.workerName)}</span>` : ''}
      </div>
      <div class="docket-foot">
        ${opts.extra || ''}
        <button class="btn btn-line btn-sm" onclick="go('/reports/${r.id}')">Open</button>
      </div>
    </article>`;
}

/* ------------------------------------------------------------ report form -- */
function renderReportForm(){
  shell(`
    <div class="head"><div><h2>Report garbage</h2>
      <p>Three things matter: what it is, exactly where it is, and a photo.</p></div></div>

    <form onsubmit="submitReport(event)" class="stack">
      <section class="card">
        <h3 style="margin-bottom:12px">Where is it?</h3>
        <p class="small muted">Tap the map to place the pin, or use your current position. The pin is what the crew will drive to, so put it on the pile.</p>
        <div class="actions" style="margin:12px 0">
          <button type="button" class="btn btn-primary" onclick="useMyLocation()">Use my current location</button>
          <button type="button" class="btn btn-line" onclick="searchPlace()">Search for a place</button>
        </div>
        <div class="map pick" id="pickMap"></div>
        <div id="pickRead"><div class="geo-readout"><span>⌖</span><span class="where">No pin placed yet. Tap the map or use your current location.</span></div></div>
        <input type="hidden" id="lat" value=""><input type="hidden" id="lon" value="">
        <div class="row-2" style="margin-top:14px">
          <div class="field"><label for="area">Area or ward</label>
            <input class="input" id="area" placeholder="Jayanagar"></div>
          <div class="field"><label for="address">Landmark or street</label>
            <input class="input" id="address" placeholder="Opposite the bus stop"></div>
        </div>
      </section>

      <section class="card">
        <h3 style="margin-bottom:12px">What is there?</h3>
        <div class="field"><label>Type of waste</label>
          <div class="chips" id="typeChips">
            ${GARBAGE_TYPES.map((t,i) => `<button type="button" class="chip ${i===0?'on':''}" data-type="${t}" onclick="pickType('${t}')">${TYPE_ICON[t]} ${words(t)}</button>`).join('')}
          </div>
          <input type="hidden" id="gtype" value="${GARBAGE_TYPES[0]}">
        </div>
        <div class="field"><label for="desc">Describe it</label>
          <textarea class="textarea" id="desc" maxlength="500" placeholder="How big is it, how long has it been there, is it blocking anything?"></textarea>
          <span class="hint"><span id="descCount">0</span>/500</span></div>
        <div class="row-2">
          <div class="field"><label for="severity">How bad is it? <span id="sevLabel" class="muted">Moderate</span></label>
            <input class="input" id="severity" type="range" min="1" max="5" value="3" oninput="sevChange(this.value)"></div>
          <div class="field"><label>Is it blocking the road or footpath?</label>
            <div class="chips">
              <button type="button" class="chip on" id="blockNo" onclick="setBlocked(false)">No</button>
              <button type="button" class="chip" id="blockYes" onclick="setBlocked(true)">Yes, blocked</button>
            </div>
            <input type="hidden" id="blocked" value="false"></div>
        </div>
      </section>

      <section class="card">
        <h3 style="margin-bottom:12px">Photo</h3>
        <p class="small muted">A photo is what lets a supervisor judge the job without going there. It also becomes the "before" half of the comparison you will see when the work is done.</p>
        <div id="photoSlot" style="margin-top:12px">
          <div class="photo-drop" onclick="choosePhoto()" ondragover="event.preventDefault();this.classList.add('over')" ondragleave="this.classList.remove('over')" ondrop="dropPhoto(event)">
            <b>Add a photo</b>Take one now, or drop a file here
          </div>
        </div>
      </section>

      <div class="actions" style="justify-content:flex-end">
        <button type="button" class="btn btn-line" onclick="go('/home')">Cancel</button>
        <button class="btn btn-primary btn-lg" type="submit">Send report</button>
      </div>
    </form>`);

  locationPicker('pickMap','lat','lon', onPickMove);
  byId('desc').addEventListener('input', e => byId('descCount').textContent = e.target.value.length);
  useMyLocation(true);
}

let reportPhoto = null;

function onPickMove(lat, lon){
  byId('pickRead').innerHTML = geoReadout(lat, lon);
  reverseGeocode(lat, lon).then(name => {
    const slot = byId('pickRead')?.querySelector('.where');
    if(slot && name){
      slot.textContent = name;
      const addr = byId('address');
      if(addr && !addr.value) addr.placeholder = name;
    }
  });
}

async function useMyLocation(quiet){
  try{
    const pos = await locateMe();
    movePicker(pos.lat, pos.lon, 18);
    if(!quiet) toast(`Pin placed, accurate to about ${Math.round(pos.acc)} m`,'ok');
  }catch(e){
    if(!quiet) toast(errText(e),'bad', 5000);
  }
}

async function searchPlace(){
  const q = await askText({title:'Search for a place', label:'Place, road or landmark', placeholder:'Indiranagar 100 Feet Road', confirmLabel:'Search'});
  if(!q) return;
  try{
    const r = await fetch(`https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&viewbox=${CITY.minLon},${CITY.maxLat},${CITY.maxLon},${CITY.minLat}&bounded=1&q=${encodeURIComponent(q)}`);
    const list = await r.json();
    if(!list.length) return toast('Nothing found in Bengaluru for that','bad');
    openModal({
      title:'Pick the right spot', sub:`${list.length} match${list.length>1?'es':''} inside the city`,
      body: list.map((p,i) => `<button class="pick-row" onclick="chooseFound(${p.lat},${p.lon})">
        <div><b>${esc(p.display_name.split(',')[0])}</b><small>${esc(p.display_name.split(',').slice(1,4).join(', '))}</small></div></button>`).join(''),
      foot:`<button class="btn btn-line" onclick="closeLayer()">Close</button>`
    });
  }catch{ toast('Place search is unavailable right now. Place the pin by hand.','bad'); }
}
function chooseFound(lat, lon){ closeLayer(); movePicker(lat, lon, 17); toast('Pin moved','ok'); }

function pickType(t){
  byId('gtype').value = t;
  byId('typeChips').querySelectorAll('.chip').forEach(c => c.classList.toggle('on', c.dataset.type === t));
}
function sevChange(v){
  byId('sevLabel').textContent = ['','Minor','Small','Moderate','Serious','Severe'][v];
}
function setBlocked(v){
  byId('blocked').value = String(v);
  byId('blockYes').classList.toggle('on', v);
  byId('blockNo').classList.toggle('on', !v);
}

async function choosePhoto(){
  const f = await pickFile({capture:true});
  if(f) setPhoto(await compressImage(f));
}
async function dropPhoto(ev){
  ev.preventDefault();
  const f = ev.dataTransfer.files[0];
  if(f) setPhoto(await compressImage(f));
}
function setPhoto(f){
  reportPhoto = f;
  byId('photoSlot').innerHTML = `
    <img class="photo" src="${URL.createObjectURL(f)}" alt="Photo you are about to send">
    <div class="actions" style="margin-top:10px">
      <button type="button" class="btn btn-line btn-sm" onclick="choosePhoto()">Replace</button>
      <button type="button" class="btn btn-danger btn-sm" onclick="clearPhoto()">Remove</button>
      <span class="hint" style="align-self:center">${(f.size/1024/1024).toFixed(1)} MB after compression</span>
    </div>`;
}
function clearPhoto(){ reportPhoto = null; renderReportForm(); }

async function submitReport(ev){
  ev.preventDefault();
  const c = coordState(byId('lat').value, byId('lon').value);
  if(!c.ok){ toast('Place the pin on the map first.','bad'); byId('pickMap').scrollIntoView({behavior:'smooth'}); return; }
  if(c.far){
    /* The server rejects these too — telling the resident here saves a round trip. */
    await confirmBox({title:'That pin is outside Bengaluru',
      sub:'Only locations inside the city can be sent to a ward crew. Move the pin onto the spot and try again.',
      confirmLabel:'Move the pin'});
    byId('pickMap').scrollIntoView({behavior:'smooth'});
    return;
  }

  const btn = ev.target.querySelector('button[type=submit]');
  const build = force => {
    const fd = new FormData();
    fd.append('garbageType', byId('gtype').value);
    fd.append('description', byId('desc').value);
    fd.append('latitude', c.lat);
    fd.append('longitude', c.lon);
    fd.append('address', byId('address').value);
    fd.append('areaName', byId('area').value);
    fd.append('severity', byId('severity').value);
    fd.append('roadBlocked', byId('blocked').value);
    fd.append('forceCreate', String(force));
    if(reportPhoto) fd.append('image', reportPhoto);
    return fd;
  };

  btn.disabled = true; btn.textContent = 'Sending…';
  try{
    const d = payload(await api('/reports', {method:'POST', body: build(false)}));
    if(d.created === false && d.possibleDuplicate){
      const dup = d.possibleDuplicate;
      const ok = await confirmBox({
        title:'This may already be reported',
        sub:`Report #${dup.id} (${words(dup.status)}) was filed ${Math.round(d.duplicateDistanceMetres || 0)} m away. Adding a second record splits the crew's attention.`,
        confirmLabel:'File it anyway'
      });
      if(!ok){ toast('Not filed. Open the existing report to follow it.','info'); return go('/reports/' + dup.id); }
      await api('/reports', {method:'POST', body: build(true)});
    }
    reportPhoto = null;
    toast('Report sent','ok');
    go('/my-reports');
  }catch(e){
    toast(errText(e),'bad', 5000);
  }finally{
    btn.disabled = false; btn.textContent = 'Send report';
  }
}

/* -------------------------------------------------------------- my reports -- */
let myFilter = '';
async function renderMyReports(){
  shell(`
    <div class="head"><div><h2>My reports</h2><p>Everything you have filed, newest first.</p></div>
      <button class="btn btn-primary" onclick="go('/report')">Report garbage</button></div>
    <div class="chips" style="margin-bottom:16px">
      ${[['','All'],['open','Open'],['check','Needs my check'],['done','Closed']].map(([k,l]) =>
        `<button class="chip ${myFilter===k?'on':''}" onclick="setMyFilter('${k}')">${l}</button>`).join('')}
    </div>
    <div id="mine">${skelCards(3)}</div>`);

  try{
    const p = payload(await api('/reports/my?page=0&size=100'));
    let rows = p.content || [];
    if(myFilter === 'open') rows = rows.filter(r => !['VERIFIED','CLOSED','REJECTED'].includes(r.status));
    if(myFilter === 'check') rows = rows.filter(r => ['CLEANING_COMPLETED','VERIFICATION_PENDING'].includes(r.status));
    if(myFilter === 'done') rows = rows.filter(r => ['VERIFIED','CLOSED'].includes(r.status));

    byId('mine').innerHTML = rows.length
      ? `<div class="docket-grid">${rows.map(r => reportDocket(r)).join('')}</div>`
      : emptyState('❐','Nothing here yet','Reports you file show up on this page and stay until they are closed.',
          `<button class="btn btn-primary" onclick="go('/report')">Report garbage</button>`);
  }catch(e){ byId('mine').innerHTML = errorState(errText(e)); }
}
function setMyFilter(k){ myFilter = k; renderMyReports(); }

/* ----------------------------------------------------------- report detail -- */
async function renderReportDetail(id){
  shell(`<a class="back" href="#" onclick="history.back();return false">‹ Back</a><div id="detail">${skelLines(5)}</div>`);
  let r;
  try{ r = payload(await api('/reports/' + id)); }
  catch(e){ byId('detail').innerHTML = errorState(errText(e)); return; }

  const c = coordState(r.latitude, r.longitude);
  const mine = r.reporterId === state.user.userId;
  const canVerify = (isCitizen() && mine || isAdmin()) && ['CLEANING_COMPLETED','VERIFICATION_PENDING'].includes(r.status);
  const canDelete = isCitizen() && mine && ['REPORTED','UNDER_REVIEW'].includes(r.status);

  /* Administrators never attach the garbage photo — that is the resident's
     evidence. What an administrator can do here is record the finished work:
     the cleared photo, and closing the job out. */
  const adminCleanup = isAdmin() && r.assignmentId && !['VERIFIED','CLOSED','REJECTED'].includes(r.status);

  byId('detail').innerHTML = `
    <div class="between" style="margin-bottom:6px">
      <div>
        <span class="docket-id">RPT-${String(r.id).padStart(4,'0')}</span>
        <h1 style="font-size:clamp(24px,3vw,34px);margin:4px 0">${TYPE_ICON[r.garbageType] || '•'} ${esc(words(r.garbageType))}</h1>
        <p class="muted">Filed ${esc(fmtDate(r.createdAt))} by ${esc(r.reporterName || 'a resident')}</p>
      </div>
      <div class="actions">${tag(r.status)} ${tag(r.priority)}</div>
    </div>

    <div class="card" style="margin:16px 0">${lifecycleTrack(r.status)}</div>

    ${r.status === 'REJECTED' && r.rejectionReason ? `<div class="notice bad">Rejected — ${esc(r.rejectionReason)}</div>` : ''}
    ${r.status === 'REOPENED' ? `<div class="notice bad">This was sent back because the cleanup was not accepted. Reopened ${r.reopenCount || 1} time(s).</div>` : ''}

    <div class="row-2 wide-left stack" style="align-items:start">
      <section class="card">
        <h3 style="margin-bottom:10px">Location</h3>
        <div class="map mini" id="oneMap"></div>
        ${geoReadout(r.latitude, r.longitude, r.address)}
        <div class="docket-meta" style="margin:12px 0 0">
          <span>⌖ ${esc(r.areaName || 'Area not recorded')}</span>
          ${r.roadBlocked ? '<span class="err-text">Blocking the road</span>' : ''}
          <span>Severity ${r.severity ?? '—'}/5</span>
          ${r.duplicateCount ? `<span>${r.duplicateCount} similar report(s)</span>` : ''}
        </div>
        <h3 style="margin:18px 0 6px">Description</h3>
        <p class="muted">${esc(r.description || 'The resident did not add a description.')}</p>
        ${r.workerName ? `<h3 style="margin:18px 0 6px">Crew</h3>
          <p class="muted">${esc(r.workerName)} — ${esc(words(r.assignmentStatus || 'assigned'))}</p>` : ''}
      </section>

      <section class="card">
        <h3 style="margin-bottom:10px">Evidence</h3>
        ${compareBlock(r.beforeImage, r.afterImage)}
        ${r.beforeImage && r.afterImage ? '<p class="hint" style="margin-top:8px">Drag across the photo to wipe between before and after.</p>' : ''}

        ${adminCleanup ? `
          <div style="border-top:1px solid var(--line-soft);margin-top:16px;padding-top:16px">
            <h3 style="margin-bottom:6px">Record the cleared site</h3>
            <p class="small muted">Upload the photo of the cleared location. The resident's original photo stays untouched as the "before" record.</p>
            <div class="actions" style="margin-top:10px">
              <button class="btn btn-primary admin btn-sm" onclick="adminUploadCleared(${r.assignmentId}, ${r.id})">
                ${r.afterImage ? 'Replace cleared photo' : 'Upload cleared photo'}
              </button>
              ${r.afterImage && r.status !== 'CLEANING_COMPLETED' ? `
                <button class="btn btn-line btn-sm" onclick="adminCloseJob(${r.assignmentId}, ${r.id})">Mark cleaning complete</button>` : ''}
            </div>
          </div>` : ''}
      </section>
    </div>

    ${canVerify ? `
      <section class="card" style="margin-top:16px;border-color:var(--wet)">
        <h3>Was it actually cleaned?</h3>
        <p class="muted" style="max-width:56ch">Compare the two photos above. If the site is clear, close it. If it is not, send it back with a reason and the crew returns.</p>
        <div class="actions" style="margin-top:12px">
          <button class="btn btn-primary" onclick="verifyReport(${r.id}, true)">Yes, it is clean</button>
          <button class="btn btn-danger" onclick="verifyReport(${r.id}, false)">No, send it back</button>
        </div>
      </section>` : ''}

    <div class="actions" style="margin-top:16px;justify-content:flex-end">
      ${c.ok ? `<a class="btn btn-line" target="_blank" rel="noopener" href="https://www.openstreetmap.org/?mlat=${c.lat}&mlon=${c.lon}#map=17/${c.lat}/${c.lon}">Open in OpenStreetMap</a>` : ''}
      ${canDelete ? `<button class="btn btn-danger" onclick="deleteReport(${r.id})">Delete this report</button>` : ''}
    </div>`;

  drawMap('oneMap', [{lat:r.latitude, lon:r.longitude, group:grp(r.status), title:`#${r.id}`, meta:words(r.status)}], {zoom:17});
  if(c.ok && !r.address){
    reverseGeocode(c.lat, c.lon).then(n => { const w = byId('detail').querySelector('.geo-readout .where'); if(w && n) w.textContent = n; });
  }
}

async function verifyReport(id, cleaned){
  let body = {cleaned:true};
  if(!cleaned){
    const reason = await askText({
      title:'Send it back', sub:'Tell the crew what is still wrong. This goes straight to them.',
      label:'What is still there?', placeholder:'The pile was moved to the side, not removed.',
      multiline:true, confirmLabel:'Send back', danger:true});
    if(!reason) return;
    body = {cleaned:false, reason};
  }
  try{
    await api(`/reports/${id}/verify`, {method:'POST', body: JSON.stringify(body)});
    toast(cleaned ? 'Closed — thank you' : 'Sent back to the crew','ok');
    renderReportDetail(id);
  }catch(e){ toast(errText(e),'bad'); }
}

async function deleteReport(id){
  const ok = await confirmBox({title:'Delete this report?', sub:'It disappears for everyone. This cannot be undone.', confirmLabel:'Delete', danger:true});
  if(!ok) return;
  try{ await api('/reports/' + id, {method:'DELETE'}); toast('Report deleted','ok'); go('/my-reports'); }
  catch(e){ toast(errText(e),'bad'); }
}

/* Administrator uploads only the cleared-site photo. */
async function adminUploadCleared(assignmentId, reportId){
  const f = await pickFile();
  if(!f) return;
  const fd = new FormData();
  fd.append('image', await compressImage(f));
  try{
    await api(`/tasks/${assignmentId}/photo`, {method:'PUT', body: fd});
    toast('Cleared photo saved','ok');
    renderReportDetail(reportId);
  }catch(e){ toast(errText(e),'bad', 5000); }
}

async function adminCloseJob(assignmentId, reportId){
  const notes = await askText({title:'Mark cleaning complete', label:'Note for the record',
    placeholder:'Cleared and swept; bin replaced.', multiline:true, required:false, confirmLabel:'Mark complete'});
  if(notes === null) return;
  const fd = new FormData();
  if(notes) fd.append('notes', notes);
  try{
    await api(`/tasks/${assignmentId}/complete`, {method:'PUT', body: fd});
    toast('Marked complete — the resident can now verify it','ok');
    renderReportDetail(reportId);
  }catch(e){ toast(errText(e),'bad', 5000); }
}

/* ------------------------------------------------------------------- bins -- */
let binView = 'map', binFilter = '';
async function renderBins(useNearest = false){
  shell(`
    <div class="head"><div><h2>Bins</h2><p>Where the public bins are and what state they are in.</p></div>
      <div class="actions">
        <button class="btn btn-line btn-sm ${binView==='map'?'btn-ink':''}" onclick="setBinView('map')">Map</button>
        <button class="btn btn-line btn-sm ${binView==='list'?'btn-ink':''}" onclick="setBinView('list')">List</button>
        <button class="btn btn-primary btn-sm" onclick="renderBins(true)">Nearest to me</button>
      </div></div>
    <div class="chips" style="margin-bottom:16px">
      ${[['','All'],...BIN_STATUSES.map(s => [s, words(s)])].map(([k,l]) =>
        `<button class="chip ${binFilter===k?'on':''}" onclick="setBinFilter('${k}')">${l}</button>`).join('')}
    </div>
    <div id="binBody">${skelCards(3)}</div>`);

  let rows = [];
  try{
    let near = null;
    if(useNearest){
      near = await locateMe().catch(() => null);
      if(!near) toast('Could not read your location — showing every bin.','info');
    }
    const path = near ? `/bins/nearby?latitude=${near.lat}&longitude=${near.lon}&radius=3` : '/bins';
    rows = payload(await api(path)) || [];
  }catch(e){ byId('binBody').innerHTML = errorState(errText(e)); return; }

  if(binFilter) rows = rows.filter(b => b.status === binFilter);
  if(!rows.length){ byId('binBody').innerHTML = emptyState('▤','No bins match','Try a different status filter, or clear it to see the whole network.'); return; }

  if(binView === 'map'){
    byId('binBody').innerHTML = `<div class="card"><div class="map" id="binMap"></div>
      <div class="map-legend">
        <span><i style="background:var(--wet)"></i>Empty or normal</span>
        <span><i style="background:var(--vest)"></i>Filling up</span>
        <span><i style="background:#e08700"></i>Full</span>
        <span><i style="background:var(--hazard)"></i>Overflowing or damaged</span>
      </div></div>`;
    drawMap('binMap', rows.map(b => ({
      lat:b.latitude, lon:b.longitude, group:grp(b.status),
      title:b.code, meta:`${b.locationName} — ${words(b.status)}${b.distanceMetres != null ? ', ' + fmtDist(b.distanceMetres) : ''}`
    })), {emptyText:'None of these bins have usable coordinates.'});
  }else{
    byId('binBody').innerHTML = `<div class="docket-grid">${rows.map(b => `
      <article class="docket s-${grp(b.status)}">
        <div class="docket-top"><span class="docket-id">${esc(b.code)}</span>${tag(b.status)}${tag(b.binType)}</div>
        <h3>${esc(b.locationName)}</h3>
        <div class="docket-meta">
          <span>⌖ ${esc(b.areaName || '')}</span>
          <span>${b.capacityLitres} L</span>
          ${b.distanceMetres != null ? `<span class="dist">${fmtDist(b.distanceMetres)} away</span>` : ''}
          <span>Last emptied ${esc(timeAgo(b.lastCollectionAt))}</span>
        </div>
        ${geoReadout(b.latitude, b.longitude, b.locationName)}
        ${isWorker() || isAdmin() ? `<div class="docket-foot">
          <button class="btn btn-line btn-sm" onclick="changeBinStatus(${b.id},'${b.status}')">Update status</button></div>` : ''}
      </article>`).join('')}</div>`;
  }
}
function setBinView(v){ binView = v; renderBins(); }
function setBinFilter(k){ binFilter = k; renderBins(); }

async function changeBinStatus(id, current){
  openModal({
    title:'Update bin status', sub:'Pick what the bin looks like right now.',
    body: BIN_STATUSES.map(s => `<button class="pick-row ${s===current?'on':''}" onclick="doBinStatus(${id},'${s}')">
      <div><b>${words(s)}</b><small>${{
        EMPTY:'Just emptied', NORMAL:'Room to spare', NEAR_FULL:'Will need collection soon',
        FULL:'Needs collection today', OVERFLOWING:'Spilling onto the street', DAMAGED:'Broken, needs repair or replacement'
      }[s]}</small></div>${tag(s)}</button>`).join(''),
    foot:`<button class="btn btn-line" onclick="closeLayer()">Cancel</button>`
  });
}
async function doBinStatus(id, status){
  closeLayer();
  try{
    await api(`/bins/${id}/status`, {method:'PUT', body: JSON.stringify({status, collected: status === 'EMPTY'})});
    toast('Bin updated','ok');
    route();
  }catch(e){ toast(errText(e),'bad'); }
}

/* ---------------------------------------------------------------- updates -- */
async function renderNotifications(){
  shell(`
    <div class="head"><div><h2>Updates</h2><p>What changed on the reports and jobs you are part of.</p></div>
      <button class="btn btn-line btn-sm" onclick="markAllRead()">Mark all read</button></div>
    <div id="notes">${skelCards(4)}</div>`);
  try{
    const rows = payload(await api('/notifications')) || [];
    state.unread = rows.filter(n => !n.read).length;
    byId('notes').innerHTML = rows.length
      ? `<div class="docket-list">${rows.map(n => `
          <article class="docket ${n.read ? '' : 's-new'}">
            <div class="docket-top">
              <strong style="font:650 15px var(--body)">${esc(n.title || 'Update')}</strong>
              ${n.read ? '' : '<span class="tag t-new">New</span>'}
              <span class="docket-id" style="margin-left:auto">${esc(timeAgo(n.createdAt))}</span>
            </div>
            <p class="desc" style="-webkit-line-clamp:4">${esc(n.message || n.body || '')}</p>
            <div class="docket-foot">
              ${n.reportId ? `<button class="btn btn-line btn-sm" onclick="go('/reports/${n.reportId}')">Open report</button>` : ''}
              ${n.read ? '' : `<button class="btn btn-line btn-sm" onclick="markRead(${n.id})">Mark read</button>`}
            </div>
          </article>`).join('')}</div>`
      : emptyState('◔','No updates','When a report of yours moves forward, you will see it here first.');
  }catch(e){ byId('notes').innerHTML = errorState(errText(e)); }
}
async function markRead(id){
  try{ await api(`/notifications/${id}/read`, {method:'PUT'}); renderNotifications(); }
  catch(e){ toast(errText(e),'bad'); }
}
async function markAllRead(){
  try{ await api('/notifications/read-all', {method:'PUT'}); state.unread = 0; renderNotifications(); }
  catch(e){ toast(errText(e),'bad'); }
}
