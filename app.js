/* Jarvis Web — Stage 1 (PWA)
   Voice loop, command routing, short replies.
   Honest limits (see chat): no cross-app control, no in-app editing —
   those need Stage 3 (native app + AccessibilityService).

   Voice input: wake-word detection still uses the browser's built-in speech
   recognition (just listening for the single word "jarvis" in English, which
   it handles fine). Actual commands are recorded as audio and transcribed via
   Whisper (through the worker's /transcribe endpoint) for much better
   Gujarati/Gujlish accuracy than the browser's built-in recognizer offers.
*/

const $ = (id) => document.getElementById(id);
const dial = $('dial'), statusText = $('statusText'), subLabel = $('subLabel');
const log = $('log'), micBtn = $('micBtn'), wakeHint = $('wakeHint');
const clock = $('clock');

let settings = JSON.parse(localStorage.getItem('jarvisSettings') || '{}');
let wakeEnabled = settings.wakeEnabled || false;
let voiceLang = settings.voiceLang || 'gu-IN';
let recognition = null;
let mode = 'idle'; // idle | wake-listening | recording | processing | speaking
let mediaRecorder = null;
let recordedChunks = [];
let autoStopTimer = null;

const LANG_NAMES = { 'gu-IN': 'Gujarati', 'en-US': 'English' };
const WHISPER_LANG = { 'gu-IN': 'gu', 'en-US': 'en' };

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
    form.append('lang', WHISPER_LANG[voiceLang] || 'gu');

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
const langBtns = { 'gu-IN': $('langGuBtn'), 'en-US': $('langEnBtn') };

function highlightLangBtn() {
  Object.entries(langBtns).forEach(([code, btn]) => {
    btn.classList.toggle('primary', code === voiceLang);
  });
}

$('settingsBtn').onclick = () => {
  $('cityInput').value = settings.city || '';
  $('proxyInput').value = settings.proxyUrl || '';
  highlightLangBtn();
  drawer.classList.add('open');
};
$('closeSettingsBtn').onclick = () => drawer.classList.remove('open');
$('wakeOnBtn').onclick = () => { wakeEnabled = true; };
$('wakeOffBtn').onclick = () => { wakeEnabled = false; };

Object.entries(langBtns).forEach(([code, btn]) => {
  btn.onclick = () => { voiceLang = code; highlightLangBtn(); };
});

$('saveSettingsBtn').onclick = () => {
  settings.city = $('cityInput').value.trim();
  settings.proxyUrl = $('proxyInput').value.trim();
  settings.wakeEnabled = wakeEnabled;
  settings.voiceLang = voiceLang;
  localStorage.setItem('jarvisSettings', JSON.stringify(settings));
  drawer.classList.remove('open');
  wakeHint.textContent = 'Wake-word listening: ' + (wakeEnabled ? 'ON' : 'OFF — enable in settings') +
    ' · Voice: ' + (LANG_NAMES[voiceLang] || voiceLang);
  if (wakeEnabled) startWakeListening();
  else setState('idle', 'Standby');
};

// ---------- init ----------
wakeHint.textContent = 'Wake-word listening: ' + (settings.wakeEnabled ? 'ON' : 'OFF — enable in settings') +
  ' · Voice: ' + (LANG_NAMES[voiceLang] || voiceLang);
if (settings.wakeEnabled) {
  wakeEnabled = true;
  startWakeListening();
}

// register service worker for installability (PWA -> Stage 2 wrap later)
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}
