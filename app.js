/* Jarvis Web — Stage 1 (PWA)
   Voice loop, command routing, short replies.
   Honest limits (see chat): no cross-app control, no in-app editing —
   those need Stage 3 (native app + AccessibilityService).

   Voice input: wake-word detection uses the browser's built-in speech
   recognition (just listening for the word "jarvis"). Actual commands are
   recorded as audio and transcribed via Whisper (through the worker's
   /transcribe endpoint) for accurate English transcription.
*/

const $ = (id) => document.getElementById(id);
const dial = $('dial'), statusText = $('statusText'), subLabel = $('subLabel');
const log = $('log'), micBtn = $('micBtn'), wakeHint = $('wakeHint');
const clock = $('clock');

let settings = JSON.parse(localStorage.getItem('jarvisSettings') || '{}');
let wakeEnabled = settings.wakeEnabled || false;
const voiceLang = 'en-US';
let recognition = null;
let mode = 'idle'; // idle | wake-listening | recording | processing | speaking
let mediaRecorder = null;
let recordedChunks = [];
let autoStopTimer = null;

const WHISPER_LANG_CODE = 'en';

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

function pickVoice(voices) {
  const exact = voices.filter(v => v.lang === voiceLang);
  const langMatches = exact.length ? exact : voices.filter(v => v.lang && v.lang.startsWith(voiceLang.split('-')[0]));
  if (!langMatches.length) return null;
  // Prefer an explicitly male-labeled voice if one exists for this language.
  const male = langMatches.find(v => /male/i.test(v.name) && !/female/i.test(v.name));
  return male || langMatches[0];
}

let voiceWarningShown = false;

function speak(text) {
  addEntry('jarvis', text);
  setState('speaking', 'Speaking');
  if ('speechSynthesis' in window) {
    const u = new SpeechSynthesisUtterance(text);
    u.rate = 1.0;
    u.pitch = 0.82; // deeper, less "British narrator" default tone
    u.lang = voiceLang;
    const match = pickVoice(speechSynthesis.getVoices());
    if (match) u.voice = match;
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

// ---------- wake-word listening (browser recognizer, English keyword only) ----------

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
      startRecording(6000); // hands-free: auto-stop after 6s
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

// ---------- command recording + Whisper transcription ----------

async function startRecording(autoStopMs) {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    speak('Microphone access is not supported in this browser.');
    return;
  }
  if (!settings.proxyUrl) {
    speak('Set up the AI backend URL in settings first.');
    return;
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    recordedChunks = [];
    mediaRecorder = new MediaRecorder(stream);
    mediaRecorder.ondataavailable = (e) => { if (e.data.size > 0) recordedChunks.push(e.data); };
    mediaRecorder.onstop = () => {
      stream.getTracks().forEach(t => t.stop());
      clearTimeout(autoStopTimer);
      const blob = new Blob(recordedChunks, { type: mediaRecorder.mimeType || 'audio/webm' });
      transcribeAndHandle(blob);
    };
    mediaRecorder.start();
    setState('recording', autoStopMs ? 'Listening' : 'Recording — tap to stop');
    subLabel.textContent = autoStopMs ? 'go ahead' : 'tap the mic again to stop';

    if (autoStopMs) {
      autoStopTimer = setTimeout(() => stopRecording(), autoStopMs);
    }
  } catch (err) {
    speak("Couldn't access the microphone. Check permissions.");
  }
}

function stopRecording() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop();
  }
}

async function transcribeAndHandle(blob) {
  setState('processing', 'Transcribing');
  try {
    const form = new FormData();
    form.append('audio', blob, 'audio.webm');
    form.append('lang', WHISPER_LANG_CODE);

    const url = settings.proxyUrl.replace(/\/$/, '') + '/transcribe';
    const r = await fetch(url, { method: 'POST', body: form });
    const data = await r.json();
    const text = (data.text || '').trim();

    if (!text) { speak("Didn't catch that."); return; }
    addEntry('user', text);
    handleCommand(text.toLowerCase());
  } catch (err) {
    speak("Couldn't reach the transcription service.");
  }
}

// Tap-to-talk: first tap starts recording, second tap stops and sends.
micBtn.addEventListener('click', () => {
  if (mode === 'recording') {
    stopRecording();
  } else {
    startRecording(null); // no auto-stop; user taps again to finish
  }
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

  // Free-form fallback -> AI backend
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

const WEATHER_CODES = {
  0: 'clear sky', 1: 'mostly clear', 2: 'partly cloudy', 3: 'overcast',
  45: 'foggy', 48: 'foggy', 51: 'light drizzle', 53: 'drizzle', 55: 'heavy drizzle',
  61: 'light rain', 63: 'rain', 65: 'heavy rain', 71: 'light snow', 73: 'snow',
  75: 'heavy snow', 80: 'rain showers', 81: 'rain showers', 82: 'heavy rain showers',
  95: 'thunderstorm', 96: 'thunderstorm with hail', 99: 'severe thunderstorm',
};

async function weatherReply() {
  const city = settings.city || 'Ahmedabad';
  try {
    const geoRes = await fetch(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(city)}&count=1`);
    const geoData = await geoRes.json();
    if (!geoData.results || !geoData.results.length) {
      speak(`${city} maate weather nathi malyu.`);
      return;
    }
    const { latitude, longitude, name } = geoData.results[0];

    const wRes = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current=temperature_2m,weather_code`);
    const wData = await wRes.json();
    const temp = Math.round(wData?.current?.temperature_2m);
    const desc = WEATHER_CODES[wData?.current?.weather_code] || 'normal';

    speak(`${name} ma atyare ${desc} che, temperature ${temp} degrees celsius che.`);
  } catch {
    speak("Weather fetch nathi thayu, internet check karje.");
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
wakeHint.textContent = 'Wake-word listening: ' + (settings.wakeEnabled ? 'ON' : 'OFF — enable in settings');
if (settings.wakeEnabled) {
  wakeEnabled = true;
  startWakeListening();
}

// register service worker for installability (PWA -> Stage 2 wrap later)
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
   }
