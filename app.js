/* Jarvis Web — Stage 1 (PWA)
   Voice loop, command routing, short replies.
   Honest limits (see chat): no cross-app control, no in-app editing —
   those need Stage 3 (native app + AccessibilityService).
*/

const $ = (id) => document.getElementById(id);
const dial = $('dial'), statusText = $('statusText'), subLabel = $('subLabel');
const log = $('log'), micBtn = $('micBtn'), wakeHint = $('wakeHint');
const clock = $('clock');

let settings = JSON.parse(localStorage.getItem('jarvisSettings') || '{}');
let wakeEnabled = settings.wakeEnabled || false;
let recognition = null;
let mode = 'idle'; // idle | wake-listening | command-listening | processing | speaking

// ---------- utility ----------

function setState(next, label) {
  mode = next;
  dial.className = 'dial ' + (next === 'idle' || next === 'wake-listening' ? 'idle' : next);
  statusText.textContent = label || next.replace('-', ' ');
}

function addEntry(who, text) {
  const el = document.createElement('div');
  el.className = 'entry ' + who;
  el.innerHTML = `<div class="tag">${who === 'user' ? 'YOU' : who === 'jarvis' ? 'JARVIS' : 'SYS'}</div><div class="msg"></div>`;
  el.querySelector('.msg').textContent = text;
  log.appendChild(el);
  log.scrollTop = log.scrollHeight;
}

function speak(text) {
  addEntry('jarvis', text);
  setState('speaking', 'Speaking');
  if ('speechSynthesis' in window) {
    const u = new SpeechSynthesisUtterance(text);
    u.rate = 1.05;
    u.onend = () => afterSpeak();
    speechSynthesis.cancel();
    speechSynthesis.speak(u);
  } else {
    setTimeout(afterSpeak, 700);
  }
}

function afterSpeak() {
  if (wakeEnabled) startWakeListening();
  else setState('idle', 'Standby');
}

function updateClock() {
  const d = new Date();
  clock.textContent = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}
setInterval(updateClock, 1000); updateClock();

// ---------- speech recognition ----------

const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;

function startWakeListening() {
  if (!SpeechRec) { subLabel.textContent = 'speech recognition unsupported'; return; }
  setState('wake-listening', 'Listening for wake word');
  subLabel.textContent = 'say "hey jarvis"';
  recognition = new SpeechRec();
  recognition.continuous = false;
  recognition.interimResults = false;
  recognition.lang = 'en-US';

  recognition.onresult = (e) => {
    const heard = e.results[0][0].transcript.toLowerCase();
    if (heard.includes('jarvis')) {
      listenForCommand();
    } else {
      if (wakeEnabled) restartWake();
    }
  };
  recognition.onerror = () => { if (wakeEnabled) restartWake(); };
  recognition.onend = () => { if (mode === 'wake-listening' && wakeEnabled) restartWake(); };

  try { recognition.start(); } catch (e) { /* already running */ }
}

function restartWake() {
  setTimeout(() => { if (wakeEnabled) startWakeListening(); }, 400);
}

function listenForCommand() {
  if (!SpeechRec) return;
  setState('command-listening', 'Listening');
  subLabel.textContent = 'go ahead';
  const rec = new SpeechRec();
  rec.continuous = false;
  rec.interimResults = false;
  rec.lang = 'en-US';
  rec.onresult = (e) => {
    const text = e.results[0][0].transcript;
    addEntry('user', text);
    handleCommand(text.toLowerCase());
  };
  rec.onerror = () => speak("Didn't catch that.");
  rec.onend = () => {};
  try { rec.start(); } catch (e) {}
}

micBtn.addEventListener('click', () => {
  if (!SpeechRec) { alert('Speech recognition not supported in this browser. Use Chrome on Android.'); return; }
  listenForCommand();
});

// ---------- command routing ----------

async function handleCommand(text) {
  setState('processing', 'Processing');

  if (text.includes('weather')) return void weatherReply();
  if (text.includes('time')) return void speak(new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
  if (text.includes('date')) return void speak(new Date().toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' }));

  if (text.includes('vibrate')) {
    if (navigator.vibrate) { navigator.vibrate(200); return void speak('Done.'); }
    return void speak('Vibration not supported here.');
  }

  if (text.startsWith('copy ')) {
    const payload = text.replace('copy ', '');
    try { await navigator.clipboard.writeText(payload); speak('Copied.'); }
    catch { speak("Couldn't access clipboard."); }
    return;
  }

  if (text.startsWith('remind me') || text.startsWith('note ')) {
    if (Notification && Notification.permission !== 'granted') await Notification.requestPermission();
    if (Notification && Notification.permission === 'granted') {
      new Notification('Jarvis note', { body: text });
      speak('Noted.');
    } else {
      speak('Notifications are blocked.');
    }
    return;
  }

  if (text.startsWith('open ')) {
    const app = text.replace('open ', '').trim();
    openApp(app);
    return;
  }

  // Free-form fallback -> optional proxy backend
  if (settings.proxyUrl) {
    try {
      const r = await fetch(settings.proxyUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt: text })
      });
      const data = await r.json();
      speak(data.reply || "No reply from backend.");
    } catch {
      speak("Can't reach the AI backend.");
    }
    return;
  }

  speak("I don't have a command for that yet.");
}

async function weatherReply() {
  try {
    const city = settings.city ? encodeURIComponent(settings.city) : '';
    const r = await fetch(`https://wttr.in/${city}?format=%C+%t`);
    const text = await r.text();
    speak(text.trim() || 'Weather unavailable.');
  } catch {
    speak("No internet, can't fetch weather.");
  }
}

function openApp(name) {
  // Best-effort deep links for a few common apps via Android intent URIs.
  // This ONLY works in Chrome on Android, and only for apps that register
  // these intent filters. There's no universal "open any app" from a
  // webpage — that's a Stage 3 (native) capability.
  const map = {
    whatsapp: 'intent://send#Intent;scheme=whatsapp;package=com.whatsapp;end',
    youtube: 'intent://#Intent;package=com.google.android.youtube;end',
    maps: 'intent://#Intent;package=com.google.android.apps.maps;end',
    gmail: 'intent://#Intent;package=com.google.android.gm;end'
  };
  const uri = map[name];
  if (uri) {
    speak(`Opening ${name}.`);
    window.location.href = uri;
  } else {
    speak(`I can't open ${name} from a webpage. That needs the native app version.`);
  }
}

// ---------- settings drawer ----------

const drawer = $('drawer');
$('settingsBtn').onclick = () => {
  $('cityInput').value = settings.city || '';
  $('proxyInput').value = settings.proxyUrl || '';
  drawer.classList.add('open');
};
$('closeSettingsBtn').onclick = () => drawer.classList.remove('open');
$('wakeOnBtn').onclick = () => { wakeEnabled = true; };
$('wakeOffBtn').onclick = () => { wakeEnabled = false; };

$('saveSettingsBtn').onclick = () => {
  settings.city = $('cityInput').value.trim();
  settings.proxyUrl = $('proxyInput').value.trim();
  settings.wakeEnabled = wakeEnabled;
  localStorage.setItem('jarvisSettings', JSON.stringify(settings));
  drawer.classList.remove('open');
  wakeHint.textContent = 'Wake-word listening: ' + (wakeEnabled ? 'ON' : 'OFF — enable in settings');
  if (wakeEnabled) startWakeListening();
  else setState('idle', 'Standby');
};

// ---------- init ----------
if (settings.wakeEnabled) {
  wakeEnabled = true;
  wakeHint.textContent = 'Wake-word listening: ON';
  startWakeListening();
}

// register service worker for installability (PWA -> Stage 2 wrap later)
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}
