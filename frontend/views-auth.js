/* ==========================================================================
   Entrance — portal picker, sign in per role, citizen registration
   ========================================================================== */

const PORTALS = {
  citizen:{ key:'CITIZEN', name:'Citizen', mark:'', ico:'☗',
    line:'Report what you see. Follow it until it is gone.',
    demo:['citizen@cleanbengaluru.com','citizen123'] },
  worker:{ key:'WORKER', name:'Field worker', mark:'worker', ico:'⚒',
    line:'Your jobs for today, ordered by how close they are.',
    demo:['worker1@cleanbengaluru.com','worker123'] },
  admin:{ key:'ADMIN', name:'Administrator', mark:'admin', ico:'▦',
    line:'Dispatch crews, watch the map, close the loop.',
    demo:['admin@cleanbengaluru.com','admin123'] }
};

function gateLeft(){
  return `
    <div class="gate-left">
      <a class="brand" href="#/enter">
        <span class="brand-mark">CB</span>
        <span class="brand-text"><b>CleanBengaluru</b><span>Civic sanitation network</span></span>
      </a>
      <div class="gate-hero">
        <h1>Garbage reported in the morning should not still be there at night.</h1>
        <p>One record follows every pile from the moment a resident photographs it to the moment the same resident confirms it is gone.</p>
        <div class="gate-figures">
          <div><b>198</b><span>wards covered</span></div>
          <div><b>3</b><span>roles, one record</span></div>
          <div><b>0</b><span>reports closed without proof</span></div>
        </div>
      </div>
      <div></div>
    </div>`;
}

function renderEntrance(which){
  const p = PORTALS[which];

  if(!p){
    bare(`<div class="gate">${gateLeft()}
      <div class="gate-right"><div class="inner">
        <h2>Sign in</h2>
        <p class="muted" style="margin-bottom:22px">Pick the workspace that matches your role.</p>
        ${Object.entries(PORTALS).map(([k,v]) => `
          <a class="door ${v.mark}" href="#/enter/${k}">
            <span class="door-ico">${v.ico}</span>
            <span><b>${v.name}</b><span>${v.line}</span></span>
            <i>›</i>
          </a>`).join('')}
        <div class="demo-note">
          <div><b>New here?</b><span>Residents can create an account in under a minute.</span></div>
          <a class="btn btn-primary btn-sm" href="#/join">Create account</a>
        </div>
      </div></div></div>`);
    return;
  }

  bare(`<div class="gate">${gateLeft()}
    <div class="gate-right"><div class="inner">
      <a class="back" href="#/enter">‹ All workspaces</a>
      <h2>${p.name} sign in</h2>
      <p class="muted" style="margin-bottom:22px">${p.line}</p>
      <div id="authErr"></div>
      <form onsubmit="doSignIn(event,'${which}')">
        <div class="field">
          <label for="email">Email</label>
          <input class="input" id="email" type="email" autocomplete="username" required placeholder="you@example.com">
        </div>
        <div class="field">
          <label for="password">Password</label>
          <input class="input" id="password" type="password" autocomplete="current-password" required placeholder="Your password">
        </div>
        <button class="btn btn-primary ${p.mark} btn-block btn-lg" type="submit">Sign in</button>
      </form>
      ${which === 'citizen' ? `<p class="small muted" style="text-align:center;margin-top:14px">No account yet? <a href="#/join">Create one</a></p>` : ''}
      <div class="demo-note">
        <div><b>Demo login</b><span>Fills the form with the seeded ${p.name.toLowerCase()} account.</span></div>
        <button class="btn btn-line btn-sm" type="button" onclick="fillDemo('${which}')">Fill</button>
      </div>
    </div></div></div>`);
}

function fillDemo(which){
  const [e,pw] = PORTALS[which].demo;
  byId('email').value = e;
  byId('password').value = pw;
}

async function doSignIn(ev, which){
  ev.preventDefault();
  const btn = ev.target.querySelector('button[type=submit]');
  btn.disabled = true; btn.textContent = 'Checking…';
  try{
    const d = payload(await api('/auth/login', {method:'POST', body: JSON.stringify({
      email: byId('email').value.trim(), password: byId('password').value
    })}));
    const want = PORTALS[which].key;
    if(d.role !== want){
      throw new Error(`That account is registered as ${words(d.role).toLowerCase()}. Use the ${words(d.role).toLowerCase()} workspace instead.`);
    }
    saveSession(d);
    await refreshUnread();
    toast(`Signed in as ${d.name || d.email}`, 'ok');
    go(HOME_FOR[d.role] || '/home');
  }catch(e){
    byId('authErr').innerHTML = `<div class="notice bad">${esc(errText(e))}</div>`;
    btn.disabled = false; btn.textContent = 'Sign in';
  }
}

function renderJoin(){
  bare(`<div class="gate">${gateLeft()}
    <div class="gate-right"><div class="inner">
      <a class="back" href="#/enter/citizen">‹ Back to sign in</a>
      <h2>Create a citizen account</h2>
      <p class="muted" style="margin-bottom:22px">You will be able to file reports, watch them move, and confirm the cleanup yourself.</p>
      <div id="authErr"></div>
      <form onsubmit="doJoin(event)">
        <div class="field"><label for="jname">Full name</label>
          <input class="input" id="jname" required minlength="3" placeholder="Your name"></div>
        <div class="field"><label for="jemail">Email</label>
          <input class="input" id="jemail" type="email" required placeholder="you@example.com"></div>
        <div class="field"><label for="jpass">Password</label>
          <input class="input" id="jpass" type="password" minlength="6" required placeholder="At least 6 characters"></div>
        <div class="row-2">
          <div class="field"><label for="jphone">Phone</label>
            <input class="input" id="jphone" maxlength="10" inputmode="numeric" placeholder="10 digits"></div>
          <div class="field"><label for="jarea">Your area</label>
            <input class="input" id="jarea" placeholder="Jayanagar"></div>
        </div>
        <button class="btn btn-primary btn-block btn-lg" type="submit">Create account</button>
      </form>
    </div></div></div>`);
}

async function doJoin(ev){
  ev.preventDefault();
  const btn = ev.target.querySelector('button[type=submit]');
  btn.disabled = true; btn.textContent = 'Creating…';
  try{
    const d = payload(await api('/auth/register', {method:'POST', body: JSON.stringify({
      name: byId('jname').value.trim(), email: byId('jemail').value.trim(),
      password: byId('jpass').value, phone: byId('jphone').value.trim(),
      areaName: byId('jarea').value.trim(), role:'CITIZEN'
    })}));
    saveSession(d);
    toast('Account created','ok');
    go('/home');
  }catch(e){
    byId('authErr').innerHTML = `<div class="notice bad">${esc(errText(e))}</div>`;
    btn.disabled = false; btn.textContent = 'Create account';
  }
}
