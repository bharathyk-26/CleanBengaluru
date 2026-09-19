/* ==========================================================================
   CleanBengaluru — core
   State, API client, UI primitives, map helpers, router, app shell.
   Loaded first; view modules attach their own render functions after this.
   ========================================================================== */

const API = `${location.protocol}//${location.hostname}:8080/api`;
const app = document.getElementById('app');
const layer = document.getElementById('layer');
const toastBox = document.getElementById('toasts');

/* Bengaluru bounding box — anything outside this is almost certainly a typo
   or an untouched form field, and is flagged instead of silently mapped. */
const CITY = { lat: 12.9716, lon: 77.5946, minLat: 12.70, maxLat: 13.25, minLon: 77.30, maxLon: 77.95 };

const GARBAGE_TYPES = ['ROADSIDE_GARBAGE','OVERFLOWING_DUSTBIN','PLASTIC_WASTE','CONSTRUCTION_WASTE','GARDEN_WASTE','ILLEGAL_DUMPING','UNCLEAN_ROAD','MISSED_COLLECTION','DAMAGED_BIN'];
const REPORT_STATUSES = ['REPORTED','UNDER_REVIEW','ASSIGNED','WORKER_ACCEPTED','CLEANING_IN_PROGRESS','CLEANING_COMPLETED','VERIFICATION_PENDING','VERIFIED','CLOSED','REJECTED','REOPENED'];
const BIN_TYPES = ['DRY_WASTE','WET_WASTE','MIXED','RECYCLABLE','HAZARDOUS'];
const BIN_STATUSES = ['EMPTY','NORMAL','NEAR_FULL','FULL','OVERFLOWING','DAMAGED'];

const TYPE_ICON = {
  ROADSIDE_GARBAGE:'🛣', OVERFLOWING_DUSTBIN:'🗑', PLASTIC_WASTE:'🥤',
  CONSTRUCTION_WASTE:'🧱', GARDEN_WASTE:'🌿', ILLEGAL_DUMPING:'🚯',
  UNCLEAN_ROAD:'🧹', MISSED_COLLECTION:'🚛', DAMAGED_BIN:'🛠'
};

const state = {
  user: JSON.parse(localStorage.getItem('cb_user') || 'null'),
  token: localStorage.getItem('cb_token') || '',
  theme: localStorage.getItem('cb_theme') || 'light',
  onDuty: localStorage.getItem('cb_duty') === '1',
  queue: JSON.parse(localStorage.getItem('cb_queue') || '[]'),
  unread: 0,
  myPosition: null,
  maps: []
};

/* ------------------------------------------------------------- utilities -- */
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const words = v => String(v ?? '').replaceAll('_',' ').toLowerCase().replace(/^./, c => c.toUpperCase());
const num = v => (v === null || v === undefined || v === '' || Number.isNaN(Number(v))) ? null : Number(v);
const clamp = (v,a,b) => Math.min(b, Math.max(a, v));
const byId = id => document.getElementById(id);

function fmtDate(v){
  if(!v) return '—';
  const d = new Date(v);
  return d.toLocaleString('en-IN',{day:'numeric',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'});
}
function timeAgo(v){
  if(!v) return '—';
  const s = (Date.now() - new Date(v).getTime())/1000;
  if(s < 60) return 'just now';
  if(s < 3600) return `${Math.floor(s/60)} min ago`;
  if(s < 86400) return `${Math.floor(s/3600)} hr ago`;
  if(s < 604800) return `${Math.floor(s/86400)} d ago`;
  return fmtDate(v).split(',')[0];
}
function clock(ms){
  const t = Math.max(0, Math.floor(ms/1000));
  const h = String(Math.floor(t/3600)).padStart(2,'0');
  const m = String(Math.floor(t%3600/60)).padStart(2,'0');
  const s = String(t%60).padStart(2,'0');
  return h === '00' ? `${m}:${s}` : `${h}:${m}:${s}`;
}
function fileUrl(name){ return `${API}/files/${encodeURIComponent(name)}`; }

/* ------------------------------------------------------ status semantics -- */
const STATUS_GROUP = {
  REPORTED:'new', UNDER_REVIEW:'new', REOPENED:'bad',
  ASSIGNED:'go', WORKER_ACCEPTED:'go',
  CLEANING_IN_PROGRESS:'work',
  CLEANING_COMPLETED:'done', VERIFICATION_PENDING:'work', VERIFIED:'done', CLOSED:'done',
  REJECTED:'bad',
  /* assignment statuses */
  ACCEPTED:'go', IN_PROGRESS:'work', COMPLETED:'done',
  /* bin statuses */
  EMPTY:'done', NORMAL:'done', NEAR_FULL:'new', FULL:'work', OVERFLOWING:'bad', DAMAGED:'bad',
  /* priority */
  LOW:'hold', MEDIUM:'new', HIGH:'work', URGENT:'bad', CRITICAL:'bad'
};
const grp = s => STATUS_GROUP[s] || 'hold';
const tag = s => s ? `<span class="tag t-${grp(s)}">${esc(words(s))}</span>` : '';

/* ------------------------------------------------------------ coordinates -- */
function coordState(lat, lon){
  const la = num(lat), lo = num(lon);
  if(la === null || lo === null) return { ok:false, reason:'No location recorded on this report.' };
  if(Math.abs(la) > 90 || Math.abs(lo) > 180) return { ok:false, reason:'Coordinates are out of range.' };
  if(Math.abs(la) < 0.5 && Math.abs(lo) < 0.5) return { ok:false, reason:'Coordinates were never set (0, 0).' };
  const far = la < CITY.minLat || la > CITY.maxLat || lo < CITY.minLon || lo > CITY.maxLon;
  return { ok:true, lat:la, lon:lo, far, reason: far ? 'This point falls outside Bengaluru. Check it before dispatching a crew.' : '' };
}
function fmtCoord(lat, lon){
  const c = coordState(lat, lon);
  return c.ok ? `${c.lat.toFixed(5)}, ${c.lon.toFixed(5)}` : '—';
}
function haversine(a, b, c, d){
  const R = 6371000, r = Math.PI/180;
  const dLat = (c-a)*r, dLon = (d-b)*r;
  const h = Math.sin(dLat/2)**2 + Math.cos(a*r)*Math.cos(c*r)*Math.sin(dLon/2)**2;
  return 2*R*Math.asin(Math.sqrt(h));
}
const fmtDist = m => m == null ? '' : (m < 1000 ? `${Math.round(m)} m` : `${(m/1000).toFixed(1)} km`);

/* A single readout used everywhere a location is shown: coordinates, the
   resolved street name, a copy control and a directions link that can only be
   built from coordinates that actually passed validation. */
function geoReadout(lat, lon, address){
  const c = coordState(lat, lon);
  if(!c.ok){
    return `<div class="geo-readout geo-warn"><span>⚠</span><span class="where">${esc(c.reason)}</span></div>`;
  }
  const id = 'geo' + Math.random().toString(36).slice(2,8);
  return `
    <div class="geo-readout ${c.far ? 'geo-warn' : ''}" id="${id}">
      <span class="coords">${c.lat.toFixed(5)}, ${c.lon.toFixed(5)}</span>
      <span class="where">${esc(address || '')}<span class="rev"></span></span>
      ${c.far ? `<span class="small">${esc(c.reason)}</span>` : ''}
      <button class="btn btn-sm btn-line" onclick="copyText('${c.lat.toFixed(6)}, ${c.lon.toFixed(6)}')">Copy</button>
      <a class="btn btn-sm btn-line" target="_blank" rel="noopener"
         href="https://www.google.com/maps/dir/?api=1&destination=${c.lat},${c.lon}">Directions</a>
    </div>`;
}

/* Street name from coordinates. Fails quietly — the map and the numbers are
   the source of truth, the name is a convenience. */
const revCache = new Map();
async function reverseGeocode(lat, lon){
  const key = `${lat.toFixed(4)},${lon.toFixed(4)}`;
  if(revCache.has(key)) return revCache.get(key);
  try{
    const r = await fetch(`https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lon}&zoom=17`, {headers:{'Accept-Language':'en'}});
    const d = await r.json();
    const name = d.display_name ? d.display_name.split(',').slice(0,3).join(', ') : '';
    revCache.set(key, name);
    return name;
  }catch{ return ''; }
}

function copyText(t){
  navigator.clipboard?.writeText(t).then(() => toast('Copied to clipboard','ok'), () => toast('Could not copy','bad'));
}

/* ------------------------------------------------------------- api client -- */
async function api(path, opts = {}){
  const headers = opts.headers ? {...opts.headers} : {};
  if(state.token) headers.Authorization = `Bearer ${state.token}`;
  const isForm = opts.body instanceof FormData;
  if(!isForm && opts.body !== undefined) headers['Content-Type'] = 'application/json';

  let res;
  try{
    res = await fetch(API + path, {...opts, headers});
  }catch(e){
    throw new Error('Cannot reach the server. Check that the backend is running on port 8080.');
  }
  const text = await res.text();
  let body = {};
  try{ body = text ? JSON.parse(text) : {}; }catch{ body = {message:text}; }

  if(!res.ok){
    if(res.status === 401){ signOut(false); throw new Error('Your session expired. Sign in again.'); }
    if(res.status === 403) throw new Error('Your role does not have access to that action.');
    throw new Error(body.message || body.error || `Request failed (${res.status})`);
  }
  return body;
}
const payload = x => (x && 'data' in x) ? x.data : x;
const errText = e => e?.message || 'Something went wrong';

/* Field work happens in patchy coverage. Status changes that fail because the
   network dropped are parked locally and replayed when it returns. */
function queueAction(label, path, opts){
  state.queue.push({label, path, opts:{...opts, body: typeof opts.body === 'string' ? opts.body : undefined}, at:Date.now()});
  localStorage.setItem('cb_queue', JSON.stringify(state.queue));
}
async function flushQueue(){
  if(!state.queue.length || !navigator.onLine) return;
  const pending = [...state.queue];
  state.queue = [];
  localStorage.setItem('cb_queue','[]');
  let done = 0;
  for(const job of pending){
    try{ await api(job.path, job.opts); done++; }
    catch{ state.queue.push(job); }
  }
  localStorage.setItem('cb_queue', JSON.stringify(state.queue));
  if(done) toast(`${done} queued update${done>1?'s':''} sent`,'ok');
  if(typeof route === 'function') route();
}
window.addEventListener('online', flushQueue);

/* ------------------------------------------------------------------- toast -- */
function toast(msg, kind = 'info', ms = 3600){
  const el = document.createElement('div');
  el.className = `toast ${kind}`;
  el.textContent = msg;
  toastBox.appendChild(el);
  setTimeout(() => { el.style.opacity = '0'; setTimeout(() => el.remove(), 250); }, ms);
}

/* ------------------------------------------------------------------ modal -- */
function closeLayer(){ layer.innerHTML = ''; document.body.classList.remove('no-scroll'); }

function openModal({title, sub, body, foot, wide}){
  document.body.classList.add('no-scroll');
  layer.innerHTML = `
    <div class="scrim" onclick="if(event.target===this)closeLayer()">
      <div class="modal ${wide?'wide':''}" role="dialog" aria-modal="true">
        <div class="modal-head">
          <div><h2>${esc(title)}</h2>${sub?`<p>${esc(sub)}</p>`:''}</div>
          <button class="btn btn-quiet btn-icon" onclick="closeLayer()" aria-label="Close">✕</button>
        </div>
        <div class="modal-body">${body||''}</div>
        <div class="modal-foot">${foot||''}</div>
      </div>
    </div>`;
}

/* Replaces window.prompt / confirm, which cannot be styled and read as broken
   on mobile. Each returns a promise so call sites stay linear. */
function askText({title, sub, label, placeholder, value = '', multiline, required = true, confirmLabel = 'Save', danger}){
  return new Promise(resolve => {
    openModal({
      title, sub,
      body: `<div class="field"><label for="ask">${esc(label)}</label>
        ${multiline
          ? `<textarea class="textarea" id="ask" placeholder="${esc(placeholder||'')}">${esc(value)}</textarea>`
          : `<input class="input" id="ask" value="${esc(value)}" placeholder="${esc(placeholder||'')}">`}
        <span class="hint" id="askHint"></span></div>`,
      foot: `<button class="btn btn-line" id="askNo">Cancel</button>
             <button class="btn ${danger?'btn-danger':'btn-primary'}" id="askYes">${esc(confirmLabel)}</button>`
    });
    const input = byId('ask');
    input.focus();
    const done = v => { closeLayer(); resolve(v); };
    byId('askNo').onclick = () => done(null);
    byId('askYes').onclick = () => {
      const v = input.value.trim();
      if(required && !v){ byId('askHint').className = 'err-text'; byId('askHint').textContent = 'This cannot be left blank.'; input.focus(); return; }
      done(v);
    };
    input.onkeydown = e => { if(e.key === 'Enter' && !multiline) byId('askYes').click(); };
  });
}

function confirmBox({title, sub, confirmLabel = 'Confirm', danger}){
  return new Promise(resolve => {
    openModal({
      title, sub,
      body: '',
      foot: `<button class="btn btn-line" id="cNo">Cancel</button>
             <button class="btn ${danger?'btn-danger':'btn-primary'}" id="cYes">${esc(confirmLabel)}</button>`
    });
    byId('cNo').onclick = () => { closeLayer(); resolve(false); };
    byId('cYes').onclick = () => { closeLayer(); resolve(true); };
  });
}

/* --------------------------------------------------------------- pictures -- */
/* Phone photos are 4–8 MB. Downscaling in the browser keeps uploads quick on a
   field connection and keeps the server's storage sane. */
function compressImage(file, max = 1600, quality = 0.82){
  return new Promise(resolve => {
    if(!file || !file.type.startsWith('image/')) return resolve(file);
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, max / Math.max(img.width, img.height));
      if(scale === 1 && file.size < 900000) return resolve(file);
      const c = document.createElement('canvas');
      c.width = Math.round(img.width * scale);
      c.height = Math.round(img.height * scale);
      c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
      c.toBlob(b => resolve(b ? new File([b], file.name.replace(/\.\w+$/,'') + '.jpg', {type:'image/jpeg'}) : file), 'image/jpeg', quality);
    };
    img.onerror = () => resolve(file);
    img.src = URL.createObjectURL(file);
  });
}

function pickFile({capture} = {}){
  return new Promise(resolve => {
    const i = document.createElement('input');
    i.type = 'file'; i.accept = 'image/*';
    if(capture) i.capture = 'environment';
    i.onchange = () => resolve(i.files[0] || null);
    i.click();
  });
}

/* Drag-to-wipe comparison of the reported scene against the finished work. */
function compareBlock(before, after){
  if(!before && !after) return '<div class="empty"><div class="ico">📷</div><h3>No photos yet</h3><p>Photos appear here once the report and the cleanup are both documented.</p></div>';
  if(!after) return `<img class="photo" src="${fileUrl(before)}" alt="Reported condition">`;
  if(!before) return `<img class="photo" src="${fileUrl(after)}" alt="After cleaning">`;
  const id = 'cmp' + Math.random().toString(36).slice(2,8);
  setTimeout(() => wireCompare(id), 0);
  return `
    <div class="compare" id="${id}">
      <img src="${fileUrl(before)}" alt="Reported condition">
      <span class="cap l">Reported</span>
      <div class="after-layer"><img src="${fileUrl(after)}" alt="After cleaning"></div>
      <span class="cap r">After cleaning</span>
      <div class="handle"><b>↔</b></div>
    </div>`;
}
function wireCompare(id){
  const root = byId(id); if(!root) return;
  const layerEl = root.querySelector('.after-layer');
  const handle = root.querySelector('.handle');
  const inner = layerEl.querySelector('img');
  const size = () => { inner.style.width = root.clientWidth + 'px'; inner.style.height = root.clientHeight + 'px'; };
  const set = pct => { layerEl.style.width = pct + '%'; handle.style.left = pct + '%'; };
  const move = e => {
    const r = root.getBoundingClientRect();
    const x = (e.touches ? e.touches[0].clientX : e.clientX) - r.left;
    set(clamp(x / r.width * 100, 0, 100));
  };
  root.addEventListener('pointerdown', e => { root.setPointerCapture(e.pointerId); move(e); root.onpointermove = move; });
  root.addEventListener('pointerup', () => root.onpointermove = null);
  new ResizeObserver(size).observe(root);
  size(); set(50);
}

/* ------------------------------------------------------------------- maps -- */
/* Every map is built here so coordinate validation happens in exactly one
   place. Nothing is ever plotted from unchecked numbers. */
function pinIcon(group, label = ''){
  return L.divIcon({
    className:'', iconSize:[26,26], iconAnchor:[13,26], popupAnchor:[0,-24],
    html:`<div class="pin p-${group}"><span>${esc(label)}</span></div>`
  });
}
function tiles(map){
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom:19, attribution:'© OpenStreetMap contributors'
  }).addTo(map);
}
function disposeMaps(){ state.maps.forEach(m => { try{ m.remove(); }catch{} }); state.maps = []; }

/**
 * points: [{lat, lon, group, label, title, meta, href}]
 */
function drawMap(elId, points = [], opts = {}){
  const el = byId(elId); if(!el) return null;
  const good = points.map(p => ({...p, c: coordState(p.lat, p.lon)})).filter(p => p.c.ok);

  if(!good.length && !opts.center){
    el.innerHTML = `<div class="empty" style="border:0;background:transparent"><div class="ico">🗺</div><h3>Nothing to plot</h3><p>${esc(opts.emptyText || 'No valid coordinates were recorded for these items.')}</p></div>`;
    return null;
  }
  const map = L.map(el, {scrollWheelZoom: !!opts.scroll}).setView(opts.center || [CITY.lat, CITY.lon], opts.zoom || 12);
  tiles(map);
  state.maps.push(map);

  const markers = good.map(p => {
    const m = L.marker([p.c.lat, p.c.lon], {icon: pinIcon(p.group || 'go', p.label || '')}).addTo(map);
    m.bindPopup(`<b>${esc(p.title || '')}</b><br>${esc(p.meta || '')}
      ${p.href ? `<br><a href="${p.href}">Open</a>` : ''}
      <br><span style="font-family:monospace;font-size:11px">${p.c.lat.toFixed(5)}, ${p.c.lon.toFixed(5)}</span>`);
    if(p.onClick) m.on('click', p.onClick);
    return m;
  });

  if(state.myPosition){
    L.marker([state.myPosition.lat, state.myPosition.lon], {icon: pinIcon('me','●')})
      .addTo(map).bindPopup('<b>You are here</b>');
  }
  if(markers.length > 1) map.fitBounds(L.featureGroup(markers).getBounds().pad(0.18));
  else if(markers.length === 1) map.setView(markers[0].getLatLng(), opts.zoom || 16);

  setTimeout(() => map.invalidateSize(), 60);
  return map;
}

/**
 * A map you place a pin on. Returns nothing; it writes into the two given
 * inputs and calls onMove. This is how a report gets its location now — typing
 * raw numbers into a box is what produced points in the middle of the ocean.
 */
function locationPicker(elId, latInput, lonInput, onMove){
  const el = byId(elId); if(!el) return;
  const start = [num(byId(latInput).value) ?? CITY.lat, num(byId(lonInput).value) ?? CITY.lon];
  const map = L.map(el).setView(start, 13);
  tiles(map);
  state.maps.push(map);

  const marker = L.marker(start, {draggable:true, icon: pinIcon('new','✚')}).addTo(map);
  const commit = ll => {
    byId(latInput).value = ll.lat.toFixed(6);
    byId(lonInput).value = ll.lng.toFixed(6);
    onMove?.(ll.lat, ll.lng);
  };
  map.on('click', e => { marker.setLatLng(e.latlng); commit(e.latlng); });
  marker.on('dragend', () => commit(marker.getLatLng()));
  setTimeout(() => map.invalidateSize(), 60);

  window.__picker = {map, marker, commit};
}
function movePicker(lat, lon, zoom = 17){
  const p = window.__picker; if(!p) return;
  p.marker.setLatLng([lat, lon]);
  p.map.setView([lat, lon], zoom);
  p.commit({lat, lng: lon});
}

function locateMe(){
  return new Promise((resolve, reject) => {
    if(!navigator.geolocation) return reject(new Error('This browser cannot read your location.'));
    navigator.geolocation.getCurrentPosition(
      p => { state.myPosition = {lat:p.coords.latitude, lon:p.coords.longitude, acc:p.coords.accuracy}; resolve(state.myPosition); },
      e => reject(new Error(e.code === 1 ? 'Location permission was declined. Allow it in the browser address bar, or place the pin by hand.' : 'Could not get a location fix. Try again outdoors.')),
      {enableHighAccuracy:true, timeout:12000, maximumAge:30000}
    );
  });
}

/* --------------------------------------------------------------- lifecycle -- */
const LIFE = [
  {key:'reported', label:'Reported', match:['REPORTED']},
  {key:'review',   label:'Reviewed', match:['UNDER_REVIEW']},
  {key:'assigned', label:'Assigned', match:['ASSIGNED','WORKER_ACCEPTED']},
  {key:'cleaning', label:'Cleaning', match:['CLEANING_IN_PROGRESS']},
  {key:'check',    label:'Your check', match:['CLEANING_COMPLETED','VERIFICATION_PENDING']},
  {key:'closed',   label:'Closed', match:['VERIFIED','CLOSED']}
];
function lifecycleTrack(status){
  if(status === 'REJECTED'){
    return `<div class="track"><div class="track-line">
      <div class="stop done"><i></i><b>Reported</b></div>
      <div class="stop halt"><i></i><b>Rejected</b><span>Closed without work</span></div>
    </div></div>`;
  }
  let at = LIFE.findIndex(s => s.match.includes(status));
  if(status === 'REOPENED') at = 2;
  if(at < 0) at = 0;
  return `<div class="track"><div class="track-line">${LIFE.map((s,i) => {
    const cls = i < at ? 'done' : i === at ? 'now' : '';
    return `<div class="stop ${cls}"><i></i><b>${s.label}</b>${
      i === at && status === 'REOPENED' ? '<span>Sent back</span>' : ''}</div>`;
  }).join('')}</div></div>`;
}

/* ------------------------------------------------------------------ skeletons */
const skelCards = (n = 3) => `<div>${Array.from({length:n}, () => '<div class="skel skel-card"></div>').join('')}</div>`;
const skelLines = (n = 4) => `<div class="card">${Array.from({length:n}, (_,i) => `<div class="skel skel-line" style="width:${100 - i*12}%"></div>`).join('')}</div>`;
function emptyState(icon, title, text, action = ''){
  return `<div class="empty"><div class="ico">${icon}</div><h3>${esc(title)}</h3><p>${esc(text)}</p>${action}</div>`;
}
const errorState = msg => `<div class="empty"><div class="ico">⚠</div><h3>That did not load</h3><p>${esc(msg)}</p><button class="btn btn-line" onclick="route()">Try again</button></div>`;

/* ------------------------------------------------------------------- auth -- */
function go(path){ location.hash = path.startsWith('#') ? path : '#' + path; }
function signOut(announce = true){
  localStorage.removeItem('cb_token'); localStorage.removeItem('cb_user');
  state.token = ''; state.user = null;
  if(announce) toast('Signed out','ok');
  go('/enter');
}
function saveSession(d){
  state.token = d.token; state.user = d;
  localStorage.setItem('cb_token', d.token);
  localStorage.setItem('cb_user', JSON.stringify(d));
}
const role = () => state.user?.role || '';
const isAdmin = () => role() === 'ADMIN';
const isWorker = () => role() === 'WORKER';
const isCitizen = () => role() === 'CITIZEN';

/* ------------------------------------------------------------------ theme -- */
function applyTheme(){
  document.documentElement.setAttribute('data-theme', state.theme);
  document.querySelector('meta[name=theme-color]')?.setAttribute('content', state.theme === 'dark' ? '#0b1512' : '#0d1f19');
}
function toggleTheme(){
  state.theme = state.theme === 'dark' ? 'light' : 'dark';
  localStorage.setItem('cb_theme', state.theme);
  applyTheme();
  route();
}
applyTheme();

/* ------------------------------------------------------------------ shell -- */
const NAV = {
  CITIZEN: [
    {p:'/home',          i:'◈', t:'Overview', tab:'Home'},
    {p:'/report',        i:'✚', t:'Report garbage', tab:'Report'},
    {p:'/my-reports',    i:'❐', t:'My reports', tab:'Reports'},
    {p:'/bins',          i:'⌖', t:'Bins near me', tab:'Bins'},
    {p:'/notifications', i:'◔', t:'Updates', tab:'Updates'}
  ],
  WORKER: [
    {p:'/worker',        i:'◈', t:'Duty board', tab:'Duty'},
    {p:'/worker/route',  i:'➟', t:'My route', tab:'Route'},
    {p:'/worker/record', i:'★', t:'My record', tab:'Record'},
    {p:'/bins',          i:'⌖', t:'Bins', tab:'Bins'},
    {p:'/notifications', i:'◔', t:'Updates', tab:'Updates'}
  ],
  /* No "report garbage" entry. Administrators review and close work; they do
     not file reports or upload the garbage photo. */
  ADMIN: [
    {p:'/admin',         i:'◈', t:'Operations', tab:'Ops'},
    {p:'/admin/reports', i:'❐', t:'Reports', tab:'Reports'},
    {p:'/admin/map',     i:'⌖', t:'City map', tab:'Map'},
    {p:'/admin/bins',    i:'▤', t:'Bin network', tab:'Bins'},
    {p:'/admin/users',   i:'☗', t:'People', tab:'People'},
    {p:'/notifications', i:'◔', t:'Updates', tab:'Updates'}
  ]
};

function shell(content){
  disposeMaps();
  const r = role();
  document.body.dataset.role = r;
  const here = location.hash.replace(/^#/,'') || '/';
  const items = NAV[r] || [];
  const accent = r === 'WORKER' ? 'worker' : r === 'ADMIN' ? 'admin' : '';
  const initial = (state.user?.name || state.user?.email || 'U').charAt(0).toUpperCase();
  const sub = r === 'WORKER' ? 'Field operations' : r === 'ADMIN' ? 'Control room' : 'Citizen portal';

  /* Longest match wins, so /admin/reports lights up Reports and not Operations. */
  const matches = n => here === n.p || here.startsWith(n.p + '/') || here.startsWith(n.p + '?');
  const active = items.filter(matches).sort((a,b) => b.p.length - a.p.length)[0];

  const navHtml = items.map(n =>
    `<button class="${n === active ? 'on' : ''}" onclick="go('${n.p}')">
       <i class="nav-ico">${n.i}</i><span>${n.t}</span>
       ${n.p === '/notifications' && state.unread ? `<span class="nav-count">${state.unread}</span>` : ''}
     </button>`).join('');

  const tabHtml = items.slice(0,5).map(n =>
    `<button class="${n === active ? 'on' : ''}" onclick="go('${n.p}')">
       <em>${n.i}</em>${n.tab}
       ${n.p === '/notifications' && state.unread ? '<span class="dot"></span>' : ''}
     </button>`).join('');

  app.innerHTML = `
    <aside class="rail">
      <a class="brand" href="#/">
        <span class="brand-mark ${accent}">CB</span>
        <span class="brand-text"><b>CleanBengaluru</b><span>${sub}</span></span>
      </a>
      <nav>${navHtml}</nav>
      <div class="rail-foot">
        <div class="who">
          <div class="avatar ${accent}">${esc(initial)}</div>
          <div><b>${esc(state.user?.name || state.user?.email || '')}</b><span>${esc(words(r))}</span></div>
        </div>
        <div class="rail-actions">
          <button class="btn btn-line btn-sm" onclick="toggleTheme()" title="Switch theme">${state.theme === 'dark' ? '☀' : '☾'}</button>
          <button class="btn btn-line btn-sm" onclick="openPalette()" title="Search (press /)">⌕</button>
          <button class="btn btn-line btn-sm" onclick="signOut()">Sign out</button>
        </div>
      </div>
    </aside>
    <div class="wrap"><main class="page">${content}</main></div>
    <footer class="foot">
      <span>CleanBengaluru — report, clean, verify.</span>
      <span>Press <b>/</b> to jump anywhere${state.queue.length ? ` · ${state.queue.length} update(s) waiting to send` : ''}</span>
    </footer>
    <nav class="tabbar">${tabHtml}</nav>`;
}

function bare(content){ disposeMaps(); document.body.dataset.role = ''; app.innerHTML = content; }

/* -------------------------------------------------------- command palette -- */
function openPalette(){
  const r = role();
  const cmds = [
    ...(NAV[r] || []).map(n => ({label:n.t, hint:'Page', run:() => go(n.p)})),
    {label:'Switch to ' + (state.theme === 'dark' ? 'light' : 'dark') + ' theme', hint:'View', run:toggleTheme},
    {label:'Sign out', hint:'Account', run:() => signOut()}
  ];
  if(isCitizen()) cmds.unshift({label:'Report garbage now', hint:'Action', run:() => go('/report')});
  if(isWorker()) cmds.unshift({label:'Build my route', hint:'Action', run:() => go('/worker/route')});
  if(isAdmin()) cmds.unshift({label:'Reports waiting for review', hint:'Action', run:() => go('/admin/reports?status=REPORTED')});

  document.body.classList.add('no-scroll');
  layer.innerHTML = `<div class="cmdk" onclick="if(event.target===this)closeLayer()">
    <div class="cmdk-box"><input id="cq" placeholder="Search pages and actions" autocomplete="off"><div class="cmdk-list" id="cl"></div></div>
  </div>`;
  let cursor = 0, shown = cmds;
  const paint = () => {
    byId('cl').innerHTML = shown.length
      ? shown.map((c,i) => `<div class="cmdk-item ${i===cursor?'on':''}" data-i="${i}">${esc(c.label)}<small>${esc(c.hint)}</small></div>`).join('')
      : '<div class="cmdk-item">Nothing matches that</div>';
    byId('cl').querySelectorAll('[data-i]').forEach(el => el.onclick = () => { closeLayer(); shown[+el.dataset.i].run(); });
  };
  paint();
  const q = byId('cq'); q.focus();
  q.oninput = () => { const v = q.value.toLowerCase(); shown = cmds.filter(c => c.label.toLowerCase().includes(v)); cursor = 0; paint(); };
  q.onkeydown = e => {
    if(e.key === 'ArrowDown'){ cursor = Math.min(cursor+1, shown.length-1); paint(); e.preventDefault(); }
    if(e.key === 'ArrowUp'){ cursor = Math.max(cursor-1, 0); paint(); e.preventDefault(); }
    if(e.key === 'Enter' && shown[cursor]){ closeLayer(); shown[cursor].run(); }
    if(e.key === 'Escape') closeLayer();
  };
}
document.addEventListener('keydown', e => {
  if(!state.user) return;
  const typing = /input|textarea|select/i.test(document.activeElement?.tagName || '');
  if(e.key === '/' && !typing){ e.preventDefault(); openPalette(); }
  if(e.key === 'Escape' && layer.innerHTML) closeLayer();
});

/* ----------------------------------------------------------- notifications -- */
async function refreshUnread(){
  if(!state.user) return;
  try{
    const rows = payload(await api('/notifications')) || [];
    state.unread = rows.filter(n => !n.read).length;
  }catch{}
}

/* ------------------------------------------------------------------ router -- */
const HOME_FOR = {ADMIN:'/admin', WORKER:'/worker', CITIZEN:'/home'};

async function route(){
  closeLayer();
  const raw = location.hash.replace(/^#/,'') || '/';
  const [path, qs] = raw.split('?');
  const q = new URLSearchParams(qs || '');
  const seg = path.split('/').filter(Boolean);

  const publicPath = path === '/enter' || path.startsWith('/enter/') || path === '/join';
  if(!state.user && !publicPath) return go('/enter');
  if(state.user && publicPath) return go(HOME_FOR[role()] || '/home');

  if(path === '/enter' || path.startsWith('/enter/')) return renderEntrance(seg[1]);
  if(path === '/join') return renderJoin();

  if(path === '/' ) return go(HOME_FOR[role()] || '/home');

  /* Filing a report is a citizen action. Administrators are redirected. */
  if(path === '/report'){
    if(isAdmin()){ toast('Administrators review reports rather than file them.','info'); return go('/admin/reports'); }
    return renderReportForm();
  }

  if(path === '/home') return isCitizen() ? renderCitizenHome() : go(HOME_FOR[role()]);
  if(path === '/my-reports') return renderMyReports();
  if(path === '/bins') return renderBins();
  if(path === '/notifications') return renderNotifications();
  if(seg[0] === 'reports' && seg[1]) return renderReportDetail(seg[1]);

  if(path === '/worker') return renderWorkerBoard();
  if(path === '/worker/route') return renderWorkerRoute();
  if(path === '/worker/record') return renderWorkerRecord();
  if(seg[0] === 'tasks' && seg[1]) return renderTaskDetail(seg[1]);

  if(path === '/admin') return renderAdminOps();
  if(path === '/admin/reports') return renderAdminReports(q.get('status') || '');
  if(path === '/admin/map') return renderAdminMap();
  if(path === '/admin/bins') return renderAdminBins();
  if(path === '/admin/users') return renderAdminPeople();

  shell(emptyState('🧭','No such page','That address does not exist in this app.',
    `<button class="btn btn-primary" onclick="go('${HOME_FOR[role()] || '/home'}')">Back to start</button>`));
}

window.addEventListener('hashchange', route);
window.addEventListener('load', async () => {
  await refreshUnread();
  flushQueue();
  route();
});
