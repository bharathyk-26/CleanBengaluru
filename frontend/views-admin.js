/* ==========================================================================
   Administrator screens
   An administrator dispatches, watches and closes. They never file a report and
   never attach the garbage photo — that evidence belongs to the resident who
   saw it. The only picture an administrator adds is the cleared site.
   ========================================================================== */

/* -------------------------------------------------------------- operations -- */
async function renderAdminOps(){
  shell(`
    <section class="banner admin">
      <h1>Where the city stands right now</h1>
      <p>Everything below is live from the field. Reports waiting on a decision are the ones to look at first.</p>
      <div class="actions">
        <button class="btn btn-primary admin" onclick="go('/admin/reports?status=REPORTED')">Reports awaiting review</button>
        <button class="btn btn-line" style="color:#fff;border-color:rgba(255,255,255,.3)" onclick="go('/admin/map')">Open the city map</button>
      </div>
    </section>
    <div id="ops">${skelCards(2)}</div>`);

  let d;
  try{ d = payload(await api('/admin/dashboard')); }
  catch(e){ byId('ops').innerHTML = errorState(errText(e)); return; }

  const total = Math.max(1, d.totalReports);
  const metric = (label, n, cls, of) =>
    `<div class="metric ${cls}"><b>${n ?? 0}</b><span>${label}</span>
      <div class="spark"><i style="width:${clamp(Math.round((n||0)/(of||total)*100),0,100)}%"></i></div></div>`;

  const catMax = Math.max(1, ...Object.values(d.reportsByCategory || {}));
  const areaMax = Math.max(1, ...Object.values(d.reportsByArea || {}));
  const bars = (obj, cls, max) => Object.keys(obj || {}).length
    ? `<div class="bars">${Object.entries(obj).sort((a,b) => b[1]-a[1]).slice(0,8).map(([k,v]) =>
        `<div class="bar-row ${cls}"><span class="lbl">${esc(words(k))}</span>
          <span class="track"><i style="width:${v/max*100}%"></i></span><strong>${v}</strong></div>`).join('')}</div>`
    : '<p class="muted">Nothing recorded yet.</p>';

  byId('ops').innerHTML = `
    <div class="head"><div><h2>Caseload</h2><p>Reports by where they sit in the process.</p></div></div>
    <div class="grid-4">
      ${metric('Reports filed', d.totalReports, '')}
      ${metric('Waiting on review', d.pendingReports, 'warn')}
      ${metric('With a crew', d.assignedReports, 'info')}
      ${metric('Being cleaned', d.inProgressReports, 'warn')}
      ${metric('Cleaned', d.completedReports, '')}
      ${metric('Sent back', d.reopenedReports, 'bad')}
      ${metric('Rejected', d.rejectedReports, 'bad')}
      ${metric('Average hours to resolve', d.averageResolutionHours ? Math.round(d.averageResolutionHours) : 0, 'info', 72)}
    </div>

    ${d.pendingReports ? `<div class="notice info" style="margin-top:16px">${d.pendingReports} report${d.pendingReports>1?'s are':' is'} waiting for a decision.
      <a href="#/admin/reports?status=REPORTED">Review them now</a>.</div>` : ''}

    <div class="head"><div><h2>Network</h2><p>Bins and people on the ground.</p></div></div>
    <div class="grid-4">
      ${metric('Bins in service', d.totalBins, '', Math.max(1,d.totalBins))}
      ${metric('Overflowing', d.overflowingBins, 'bad', Math.max(1,d.totalBins))}
      ${metric('Damaged', d.damagedBins, 'warn', Math.max(1,d.totalBins))}
      ${metric('Field workers', d.totalWorkers, 'info', Math.max(1,d.totalWorkers))}
    </div>

    <div class="row-2 stack" style="margin-top:22px;align-items:start">
      <section class="card"><h3 style="margin-bottom:14px">What is being reported</h3>${bars(d.reportsByCategory,'',catMax)}</section>
      <section class="card"><h3 style="margin-bottom:14px">Where it is being reported</h3>${bars(d.reportsByArea,'b-dry',areaMax)}</section>
    </div>

    <div class="head"><div><h2>Area cleanliness</h2>
      <p>A rolling score from open versus resolved reports. Useful for spotting drift, not for publishing.</p></div>
      <button class="btn btn-line btn-sm" onclick="go('/admin/map')">See on the map</button></div>
    <div class="card">${(d.areaScores || []).length
      ? `<div class="bars">${d.areaScores.map(a => `
          <div class="bar-row ${a.score >= 70 ? '' : a.score >= 40 ? 'b-vest' : 'b-dry'}">
            <span class="lbl">${esc(a.areaName)}<br><small class="muted">${a.openReports} open · ${a.resolvedReports} resolved</small></span>
            <span class="track"><i style="width:${clamp(Number(a.score)||0,0,100)}%"></i></span>
            <strong>${a.score}</strong>
          </div>`).join('')}</div>`
      : '<p class="muted">Scores appear once reports have been closed in a few areas.</p>'}</div>

    <div class="head"><div><h2>Crews</h2><p>Live workload per worker. Assign around the ones already loaded.</p></div></div>
    <div class="tbl-wrap"><table class="tbl">
      <thead><tr><th>Worker</th><th>Area</th><th>Open now</th><th>Closed</th><th>Total</th></tr></thead>
      <tbody>${(d.workerStats || []).length ? d.workerStats.map(w => `
        <tr><td><b>${esc(w.workerName)}</b></td><td>${esc(w.areaName || '—')}</td>
          <td>${w.activeTasks ? `<span class="tag t-work">${w.activeTasks} open</span>` : '<span class="muted">Free</span>'}</td>
          <td>${w.completedTasks}</td><td>${w.totalTasks}</td></tr>`).join('')
        : '<tr><td colspan="5" class="muted">No workers on the roster yet.</td></tr>'}</tbody>
    </table></div>`;
}

/* ----------------------------------------------------------------- reports -- */
let adminRows = [], adminSearch = '';
async function renderAdminReports(status = ''){
  shell(`
    <div class="head"><div><h2>Reports</h2><p>Review what came in, send it to a crew, or reject it with a reason.</p></div>
      <div class="actions">
        <button class="btn btn-line btn-sm" onclick="exportReports()">Export CSV</button>
      </div></div>
    <div class="chips" style="margin-bottom:12px">
      ${[['','Everything'],['REPORTED','New'],['UNDER_REVIEW','Reviewing'],['ASSIGNED','With a crew'],
         ['CLEANING_IN_PROGRESS','Being cleaned'],['VERIFICATION_PENDING','Awaiting resident'],
         ['REOPENED','Sent back'],['VERIFIED','Closed'],['REJECTED','Rejected']].map(([k,l]) =>
        `<button class="chip ${status===k?'on':''}" onclick="go('/admin/reports${k?'?status='+k:''}')">${l}</button>`).join('')}
    </div>
    <input class="input" id="adminQ" placeholder="Filter by id, area, type or reporter" style="margin-bottom:14px" oninput="filterAdmin(this.value)">
    <div id="ar">${skelCards(4)}</div>`);

  try{
    const p = payload(await api('/admin/reports?page=0&size=200' + (status ? '&status=' + status : '')));
    adminRows = p.content || [];
    paintAdminRows();
  }catch(e){ byId('ar').innerHTML = errorState(errText(e)); }
}

function filterAdmin(v){ adminSearch = v.toLowerCase(); paintAdminRows(); }

function paintAdminRows(){
  const rows = adminRows.filter(r => !adminSearch ||
    [r.id, r.areaName, r.garbageType, r.reporterName, r.address].join(' ').toLowerCase().includes(adminSearch));

  if(!rows.length){
    byId('ar').innerHTML = emptyState('❐','Nothing matches','No report fits that filter. Clear the search or pick another status.');
    return;
  }

  byId('ar').innerHTML = `<div class="tbl-wrap"><table class="tbl">
    <thead><tr><th>Report</th><th>Type</th><th>Area</th><th>Location</th><th>Status</th><th>Priority</th><th>Filed</th><th></th></tr></thead>
    <tbody>${rows.map(r => {
      const c = coordState(r.latitude, r.longitude);
      return `<tr>
        <td><b class="mono">RPT-${String(r.id).padStart(4,'0')}</b><br><small class="muted">${esc(r.reporterName || '')}</small></td>
        <td>${TYPE_ICON[r.garbageType] || ''} ${esc(words(r.garbageType))}</td>
        <td>${esc(r.areaName || '—')}</td>
        <td>${c.ok
          ? `<span class="mono">${c.lat.toFixed(4)}, ${c.lon.toFixed(4)}</span>${c.far ? '<br><span class="err-text">Outside the city</span>' : ''}`
          : '<span class="err-text">Not usable</span>'}</td>
        <td>${tag(r.status)}</td>
        <td>${tag(r.priority)}</td>
        <td class="nowrap">${esc(timeAgo(r.createdAt))}</td>
        <td><div class="actions" style="justify-content:flex-end">
          ${['REPORTED','REOPENED'].includes(r.status) ? `<button class="btn btn-line btn-sm" onclick="reviewReport(${r.id})">Review</button>` : ''}
          ${['REPORTED','UNDER_REVIEW','REOPENED'].includes(r.status) ? `
            <button class="btn btn-primary admin btn-sm" onclick="openAssign(${r.id})">Assign</button>
            <button class="btn btn-danger btn-sm" onclick="rejectReport(${r.id})">Reject</button>` : ''}
          <button class="btn btn-quiet btn-sm" onclick="go('/reports/${r.id}')">Open</button>
        </div></td>
      </tr>`;
    }).join('')}</tbody></table></div>
    <p class="hint" style="margin-top:10px">${rows.length} of ${adminRows.length} shown.</p>`;
}

async function reviewReport(id){
  try{ await api(`/admin/reports/${id}/review`, {method:'POST'}); toast('Moved to review','ok'); route(); }
  catch(e){ toast(errText(e),'bad'); }
}

/* Assignment shows each worker's live load, so dispatch is a choice rather than
   a guess at an id number. */
async function openAssign(reportId){
  openModal({title:'Send this to a crew', sub:'Loading the roster…', body: skelCards(3), foot:''});
  try{
    const [workers, stats] = await Promise.all([
      api('/admin/workers').then(payload),
      api('/admin/analytics/workers').then(payload).catch(() => [])
    ]);
    const load = {};
    (stats || []).forEach(s => load[s.workerId] = s);
    const list = (workers || []).filter(w => w.active !== false);

    openModal({
      title:'Send this to a crew',
      sub:`RPT-${String(reportId).padStart(4,'0')} — pick who goes`,
      body: list.length ? list.map(w => {
        const s = load[w.id] || {};
        const busy = s.activeTasks || 0;
        return `<button class="pick-row" onclick="doAssign(${reportId}, ${w.id})">
          <span class="avatar worker">${esc((w.name||'?').charAt(0).toUpperCase())}</span>
          <span><b>${esc(w.name)}</b><small>${esc(w.areaName || 'No fixed area')} · ${esc(w.email)}</small></span>
          <span class="load"><b>${busy}</b><small>open now</small></span>
        </button>`;
      }).join('') : '<p class="muted">No active workers on the roster. Create one under People first.</p>',
      foot:`<button class="btn btn-line" onclick="closeLayer()">Cancel</button>`
    });
  }catch(e){
    closeLayer();
    toast(errText(e),'bad');
  }
}
async function doAssign(reportId, workerId){
  closeLayer();
  try{
    await api(`/admin/reports/${reportId}/assign`, {method:'POST', body: JSON.stringify({workerId})});
    toast('Crew assigned and notified','ok');
    route();
  }catch(e){ toast(errText(e),'bad', 5000); }
}

async function rejectReport(id){
  const reason = await askText({title:'Reject this report',
    sub:'The resident sees this reason, so make it specific.',
    label:'Why is it being rejected?', placeholder:'Duplicate of RPT-0042, already assigned.',
    multiline:true, confirmLabel:'Reject report', danger:true});
  if(!reason) return;
  try{ await api(`/admin/reports/${id}/reject`, {method:'POST', body: JSON.stringify({reason})}); toast('Report rejected','ok'); route(); }
  catch(e){ toast(errText(e),'bad'); }
}

function exportReports(){
  if(!adminRows.length) return toast('Nothing to export','bad');
  const cols = ['id','garbageType','status','priority','areaName','address','latitude','longitude','reporterName','createdAt','resolvedAt'];
  const csv = [cols.join(',')].concat(adminRows.map(r =>
    cols.map(c => `"${String(r[c] ?? '').replace(/"/g,'""')}"`).join(','))).join('\n');
  const url = URL.createObjectURL(new Blob([csv], {type:'text/csv'}));
  const a = document.createElement('a');
  a.href = url; a.download = `cleanbengaluru-reports-${new Date().toISOString().slice(0,10)}.csv`;
  a.click(); URL.revokeObjectURL(url);
  toast('CSV downloaded','ok');
}

/* -------------------------------------------------------------- city map -- */
let mapFilter = 'open';
async function renderAdminMap(){
  shell(`
    <div class="head"><div><h2>City map</h2><p>Every report plotted from checked coordinates. Anything with a bad location is listed separately rather than dropped on the map.</p></div></div>
    <div class="chips" style="margin-bottom:14px">
      ${[['open','Open work'],['all','Everything'],['urgent','Urgent only'],['bins','Bins']].map(([k,l]) =>
        `<button class="chip ${mapFilter===k?'on':''}" onclick="setMapFilter('${k}')">${l}</button>`).join('')}
    </div>
    <div class="card"><div class="map tall" id="cityMap"></div>
      <div class="map-legend">
        <span><i style="background:var(--vest)"></i>Waiting on review</span>
        <span><i style="background:var(--dry)"></i>Assigned</span>
        <span><i style="background:#e08700"></i>Being cleaned</span>
        <span><i style="background:var(--wet)"></i>Closed</span>
        <span><i style="background:var(--hazard)"></i>Sent back or rejected</span>
      </div></div>
    <div id="badCoords"></div>`);

  try{
    let points = [], bad = [];
    if(mapFilter === 'bins'){
      const bins = payload(await api('/bins')) || [];
      points = bins.map(b => ({lat:b.latitude, lon:b.longitude, group:grp(b.status), title:b.code, meta:`${b.locationName} — ${words(b.status)}`}));
    }else{
      const p = payload(await api('/reports?page=0&size=300'));
      let rows = p.content || [];
      if(mapFilter === 'open') rows = rows.filter(r => !['VERIFIED','CLOSED','REJECTED'].includes(r.status));
      if(mapFilter === 'urgent') rows = rows.filter(r => ['URGENT','CRITICAL','HIGH'].includes(r.priority) || r.roadBlocked);
      bad = rows.filter(r => !coordState(r.latitude, r.longitude).ok);
      points = rows.map(r => ({
        lat:r.latitude, lon:r.longitude, group:grp(r.status),
        title:`RPT-${String(r.id).padStart(4,'0')} — ${words(r.garbageType)}`,
        meta:`${words(r.status)} · ${r.areaName || ''}`,
        href:`#/reports/${r.id}`
      }));
    }
    drawMap('cityMap', points, {scroll:true, emptyText:'No item in this filter has a usable location.'});

    byId('badCoords').innerHTML = bad.length ? `
      <div class="head"><div><h2>Bad locations</h2>
        <p>These ${bad.length} report(s) cannot be mapped or dispatched until someone fixes the coordinates.</p></div></div>
      <div class="tbl-wrap"><table class="tbl">
        <thead><tr><th>Report</th><th>Area</th><th>Recorded value</th><th></th></tr></thead>
        <tbody>${bad.map(r => `<tr>
          <td class="mono">RPT-${String(r.id).padStart(4,'0')}</td>
          <td>${esc(r.areaName || '—')}</td>
          <td class="mono">${esc(String(r.latitude))}, ${esc(String(r.longitude))}</td>
          <td><button class="btn btn-line btn-sm" onclick="go('/reports/${r.id}')">Open</button></td></tr>`).join('')}</tbody>
      </table></div>` : '';
  }catch(e){ byId('badCoords').innerHTML = errorState(errText(e)); }
}
function setMapFilter(k){ mapFilter = k; renderAdminMap(); }

/* ------------------------------------------------------------ bin network -- */
async function renderAdminBins(){
  shell(`
    <div class="head"><div><h2>Bin network</h2><p>Add bins, change their state, and retire the ones that are gone.</p></div>
      <button class="btn btn-primary admin" onclick="openNewBin()">Add a bin</button></div>
    <div id="ab">${skelCards(3)}</div>`);
  try{
    const rows = payload(await api('/bins')) || [];
    const counts = BIN_STATUSES.map(s => [s, rows.filter(b => b.status === s).length]);
    byId('ab').innerHTML = `
      <div class="grid-3" style="margin-bottom:16px">
        ${counts.filter(([,n]) => n).map(([s,n]) =>
          `<div class="metric ${grp(s)==='bad'?'bad':grp(s)==='work'?'warn':''}"><b>${n}</b><span>${words(s)}</span></div>`).join('')}
      </div>
      <div class="docket-grid">${rows.map(b => `
        <article class="docket s-${grp(b.status)}">
          <div class="docket-top"><span class="docket-id">${esc(b.code)}</span>${tag(b.status)}${tag(b.binType)}</div>
          <h3>${esc(b.locationName)}</h3>
          <div class="docket-meta"><span>⌖ ${esc(b.areaName || '')}</span><span>${b.capacityLitres} L</span>
            <span>Emptied ${esc(timeAgo(b.lastCollectionAt))}</span></div>
          ${geoReadout(b.latitude, b.longitude, b.locationName)}
          <div class="docket-foot">
            <button class="btn btn-line btn-sm" onclick="changeBinStatus(${b.id},'${b.status}')">Status</button>
            <button class="btn btn-danger btn-sm" onclick="removeBin(${b.id},'${esc(b.code)}')">Remove</button>
          </div>
        </article>`).join('') || emptyState('▤','No bins yet','Add the first bin and it will appear on the citizen map straight away.')}</div>`;
  }catch(e){ byId('ab').innerHTML = errorState(errText(e)); }
}

/* Bins are placed on a map too. Typing coordinates into a box is how a bin ends
   up in the Gulf of Guinea. */
function openNewBin(){
  openModal({
    title:'Add a bin', sub:'Place it on the map, then fill in the details.', wide:true,
    body:`
      <div class="map pick" id="binPick" style="height:260px"></div>
      <input type="hidden" id="blat"><input type="hidden" id="blon">
      <div id="binRead">${geoReadout(CITY.lat, CITY.lon)}</div>
      <div class="row-2" style="margin-top:14px">
        <div class="field"><label for="bcode">Bin code</label><input class="input" id="bcode" placeholder="BLR-JAY-003"></div>
        <div class="field"><label for="bname">Location name</label><input class="input" id="bname" placeholder="Jayanagar 4th Block Complex"></div>
      </div>
      <div class="row-2">
        <div class="field"><label for="btype">Waste type</label>
          <select class="select" id="btype">${BIN_TYPES.map(t => `<option value="${t}">${words(t)}</option>`).join('')}</select></div>
        <div class="field"><label for="bcap">Capacity in litres</label><input class="input" id="bcap" type="number" value="240"></div>
      </div>
      <div class="field"><label for="barea">Area</label><input class="input" id="barea" placeholder="Jayanagar"></div>`,
    foot:`<button class="btn btn-line" onclick="closeLayer()">Cancel</button>
          <button class="btn btn-primary admin" onclick="createBin()">Add bin</button>`
  });
  setTimeout(() => locationPicker('binPick','blat','blon',
    (lat,lon) => byId('binRead').innerHTML = geoReadout(lat,lon)), 60);
}

async function createBin(){
  const c = coordState(byId('blat').value, byId('blon').value);
  if(!c.ok) return toast('Place the bin on the map first.','bad');
  const code = byId('bcode').value.trim(), locationName = byId('bname').value.trim();
  if(!code || !locationName) return toast('A bin needs a code and a location name.','bad');
  try{
    await api('/bins', {method:'POST', body: JSON.stringify({
      code, locationName, latitude:c.lat, longitude:c.lon,
      binType: byId('btype').value, capacityLitres: Number(byId('bcap').value) || 240,
      status:'NORMAL', areaName: byId('barea').value.trim()
    })});
    closeLayer(); toast('Bin added','ok'); renderAdminBins();
  }catch(e){ toast(errText(e),'bad', 5000); }
}

async function removeBin(id, code){
  const ok = await confirmBox({title:`Remove ${code}?`, sub:'It disappears from the citizen map immediately.', confirmLabel:'Remove', danger:true});
  if(!ok) return;
  try{ await api('/bins/' + id, {method:'DELETE'}); toast('Bin removed','ok'); renderAdminBins(); }
  catch(e){ toast(errText(e),'bad'); }
}

/* ---------------------------------------------------------------- people -- */
let peopleFilter = '';
async function renderAdminPeople(){
  shell(`
    <div class="head"><div><h2>People</h2><p>Residents, field workers and administrators on this system.</p></div>
      <button class="btn btn-primary admin" onclick="openNewStaff()">Add staff</button></div>
    <div class="chips" style="margin-bottom:16px">
      ${[['','Everyone'],['WORKER','Field workers'],['ADMIN','Administrators'],['CITIZEN','Residents']].map(([k,l]) =>
        `<button class="chip ${peopleFilter===k?'on':''}" onclick="setPeopleFilter('${k}')">${l}</button>`).join('')}
    </div>
    <div id="people">${skelCards(4)}</div>`);
  try{
    let rows = payload(await api('/admin/users' + (peopleFilter ? '?role=' + peopleFilter : ''))) || [];
    byId('people').innerHTML = rows.length ? `<div class="tbl-wrap"><table class="tbl">
      <thead><tr><th>Name</th><th>Role</th><th>Contact</th><th>Area</th><th>Status</th><th></th></tr></thead>
      <tbody>${rows.map(u => `<tr>
        <td><b>${esc(u.name)}</b></td>
        <td>${tag(u.role)}</td>
        <td>${esc(u.email)}<br><small class="muted">${esc(u.phone || '')}</small></td>
        <td>${esc(u.areaName || '—')}</td>
        <td>${u.active === false ? '<span class="tag t-bad">Deactivated</span>' : '<span class="tag t-done">Active</span>'}</td>
        <td>${u.role !== 'CITIZEN' ? `<button class="btn btn-line btn-sm" onclick="toggleUser(${u.id},${u.active !== false})">
          ${u.active === false ? 'Reactivate' : 'Deactivate'}</button>` : ''}</td>
      </tr>`).join('')}</tbody></table></div>`
      : emptyState('☗','Nobody here','No account matches that filter.');
  }catch(e){ byId('people').innerHTML = errorState(errText(e)); }
}
function setPeopleFilter(k){ peopleFilter = k; renderAdminPeople(); }

function openNewStaff(){
  openModal({
    title:'Add a staff account', sub:'Field workers and administrators are created here. Residents sign themselves up.',
    body:`
      <div class="field"><label for="sname">Full name</label><input class="input" id="sname" placeholder="Name"></div>
      <div class="field"><label for="semail">Email</label><input class="input" id="semail" type="email" placeholder="name@cleanbengaluru.com"></div>
      <div class="field"><label for="spass">Temporary password</label><input class="input" id="spass" type="text" placeholder="At least 6 characters"></div>
      <div class="row-2">
        <div class="field"><label for="srole">Role</label>
          <select class="select" id="srole"><option value="WORKER">Field worker</option><option value="ADMIN">Administrator</option></select></div>
        <div class="field"><label for="sphone">Phone</label><input class="input" id="sphone" maxlength="10" placeholder="10 digits"></div>
      </div>
      <div class="field"><label for="sarea">Assigned area</label><input class="input" id="sarea" placeholder="Koramangala"></div>`,
    foot:`<button class="btn btn-line" onclick="closeLayer()">Cancel</button>
          <button class="btn btn-primary admin" onclick="createStaff()">Create account</button>`
  });
}
async function createStaff(){
  const name = byId('sname').value.trim(), email = byId('semail').value.trim(), password = byId('spass').value;
  if(!name || !email || password.length < 6) return toast('Name, email and a password of at least 6 characters are required.','bad');
  try{
    await api('/admin/users', {method:'POST', body: JSON.stringify({
      name, email, password, role: byId('srole').value,
      phone: byId('sphone').value.trim(), areaName: byId('sarea').value.trim()
    })});
    closeLayer(); toast('Account created','ok'); renderAdminPeople();
  }catch(e){ toast(errText(e),'bad', 5000); }
}
async function toggleUser(id, active){
  try{ await api(`/admin/users/${id}/active?active=${!active}`, {method:'PUT'}); toast(active ? 'Account deactivated' : 'Account reactivated','ok'); renderAdminPeople(); }
  catch(e){ toast(errText(e),'bad'); }
}
