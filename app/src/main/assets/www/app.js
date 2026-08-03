/* Jarvis Web — Hybrid build, chat UI
   Same command routing / native bridge logic as before, adapted to a
   chat-bubble interface with a floating text+mic+send input bar.
*/

const $ = (id) => document.getElementById(id);
const dial = $('dial'), statusText = $('statusText');
const log = $('log'), micBtn = $('micBtn'), sendBtn = $('sendBtn'), textInput = $('textInput');
const inputBar = $('inputBar'), clock = $('clock');

let settings = JSON.parse(localStorage.getItem('jarvisSettings') || '{}');

// Auto-fill the AI backend URL from the app's build-time secret, if the
// user hasn't manually set one — so a freshly installed APK works
// out of the box with no setup step required.
if (!settings.proxyUrl && window.AndroidBridge && window.AndroidBridge.getDefaultProxyUrl) {
  const defaultUrl = window.AndroidBridge.getDefaultProxyUrl();
  if (defaultUrl) {
    settings.proxyUrl = defaultUrl;
    localStorage.setItem('jarvisSettings', JSON.stringify(settings));
  }
}

let wakeEnabled = settings.wakeEnabled || false;
const voiceLang = 'en-US';
let recognition = null;
let mode = 'idle'; // idle | wake-listening | recording | processing | speaking
let mediaRecorder = null;
let recordedChunks = [];
let autoStopTimer = null;

const WHISPER_LANG_CODE = 'en';

// ---------- native bridge hooks (called FROM native Kotlin code) ----------

window.onNativeSpeechDone = () => afterSpeak();

window.onNativeStateChange = (state) => {
  if (state === 'ACTIVE') setState('idle', 'Ready');
  else setState('idle', 'Standby');
};

window.onNativeTranscript = (text) => {
  addBubble('user', text);
  handleCommand(stripWakeWord(text.toLowerCase()));
};

window.onNativeNoSpeech = () => speak("Didn't catch that.");

// Native auto-stops recording (silence detection) — reset the mic icon/UI here.
window.onNativeRecordingStopped = () => {
  micBtn.classList.remove('recording');
  micBtn.textContent = '🎤';
  setState('processing', 'Transcribing');
};

// ---------- utility ----------

function setState(next, label) {
  mode = next;
  dial.className = 'dial ' + (next === 'idle' || next === 'wake-listening' ? 'idle' : next);
  statusText.textContent = label || next.replace('-', ' ');
}

function addBubble(who, text) {
  const row = document.createElement('div');
  row.className = 'row ' + who;
  const bubble = document.createElement('div');
  bubble.className = 'bubble';
  bubble.textContent = text;
  row.appendChild(bubble);
  log.appendChild(row);
  log.scrollTop = log.scrollHeight;
}

function stripWakeWord(text) {
  return text.replace(/^\s*(hey\s+)?jarvis[,:]?\s*/i, '').trim();
}

function pickVoice(voices) {
  const exact = voices.filter(v => v.lang === voiceLang);
  const langMatches = exact.length ? exact : voices.filter(v => v.lang && v.lang.startsWith(voiceLang.split('-')[0]));
  if (!langMatches.length) return null;
  const male = langMatches.find(v => /male/i.test(v.name) && !/female/i.test(v.name));
  return male || langMatches[0];
}

function speak(text) {
  addBubble('jarvis', text);
  setState('speaking', 'Speaking');

  if (window.AndroidBridge) {
    window.AndroidBridge.speak(text); // afterSpeak() fires via onNativeSpeechDone above
    return;
  }

  if ('speechSynthesis' in window) {
    const u = new SpeechSynthesisUtterance(text);
    u.rate = 1.0;
    u.pitch = 0.82;
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

// ---------- wake-word listening (browser fallback — native handles this in-app) ----------

const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;

function startWakeListening() {
  if (window.AndroidBridge) return; // native WakeWordService handles this in-app
  if (!SpeechRec) return;
  setState('wake-listening', 'Listening for wake word');
  recognition = new SpeechRec();
  recognition.continuous = false;
  recognition.interimResults = false;
  recognition.lang = 'en-US';

  recognition.onresult = (e) => {
    const heard = e.results[0][0].transcript.toLowerCase();
    if (heard.includes('jarvis')) {
      startRecording(6000);
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

// ---------- command recording + Whisper transcription (browser fallback) ----------

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
    setState('recording', autoStopMs ? 'Listening' : 'Recording…');

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
    addBubble('user', text);
    handleCommand(stripWakeWord(text.toLowerCase()));
  } catch (err) {
    speak("Couldn't reach the transcription service.");
  }
}

// ---------- mic button: tap to start, tap again to stop (auto-stop on silence too) ----------

micBtn.addEventListener('click', () => {
  if (window.AndroidBridge) {
    if (mode === 'recording') {
      window.AndroidBridge.stopRecording();
      micBtn.classList.remove('recording');
      micBtn.textContent = '🎤';
      setState('processing', 'Transcribing');
    } else {
      const started = window.AndroidBridge.startRecording(); // native watches for silence and auto-stops
      if (started) {
        micBtn.classList.add('recording');
        micBtn.textContent = '⏹';
        setState('recording', 'Listening…');
      }
    }
    return;
  }

  // Browser fallback (standalone website) — manual tap-to-stop only.
  if (mode === 'recording') {
    stopRecording();
    micBtn.classList.remove('recording');
    micBtn.textContent = '🎤';
  } else {
    startRecording(null);
    micBtn.classList.add('recording');
    micBtn.textContent = '⏹';
  }
});

// ---------- text chat: type + send, same command routing as voice ----------

function sendTypedMessage() {
  const text = textInput.value.trim();
  if (!text) return;
  addBubble('user', text);
  textInput.value = '';
  textInput.style.height = 'auto';
  handleCommand(stripWakeWord(text.toLowerCase()));
}

sendBtn.addEventListener('click', sendTypedMessage);
textInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendTypedMessage();
  }
});
// Auto-grow the textarea as the user types (up to the CSS max-height).
textInput.addEventListener('input', () => {
  textInput.style.height = 'auto';
  textInput.style.height = textInput.scrollHeight + 'px';
});

// ---------- keep the input bar floating just above the on-screen keyboard ----------

function adjustInputBarForKeyboard() {
  if (!window.visualViewport) return;
  const offset = Math.max(0, window.innerHeight - window.visualViewport.height - window.visualViewport.offsetTop);
  inputBar.style.transform = offset > 0 ? `translateY(-${offset}px)` : '';
  log.scrollTop = log.scrollHeight;
}
if (window.visualViewport) {
  window.visualViewport.addEventListener('resize', adjustInputBarForKeyboard);
  window.visualViewport.addEventListener('scroll', adjustInputBarForKeyboard);
}

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
    if (window.AndroidBridge) {
      const ok = window.AndroidBridge.openApp(app);
      speak(ok ? `Opening ${app}.` : `Couldn't find ${app} installed.`);
    } else {
      openApp(app);
    }
    return;
  }

  if ((text.startsWith('tap ') || text.startsWith('click ')) && window.AndroidBridge) {
    const label = text.replace(/^tap |^click /, '').trim();
    const ok = window.AndroidBridge.tapElement(label);
    if (ok) {
      speak('Done.');
      window.AndroidBridge.hideGrid();
    } else {
      window.AndroidBridge.showGrid();
      speak("I don't see that labeled — say a number.");
    }
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
      speak(`Couldn't find weather for ${city}.`);
      return;
    }
    const { latitude, longitude, name } = geoData.results[0];

    const wRes = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current=temperature_2m,weather_code`);
    const wData = await wRes.json();
    const temp = Math.round(wData?.current?.temperature_2m);
    const desc = WEATHER_CODES[wData?.current?.weather_code] || 'normal';

    speak(`${name} is ${desc} right now, ${temp} degrees celsius.`);
  } catch {
    speak("Couldn't fetch the weather, check your internet.");
  }
}

function openApp(name) {
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
const wakeHint = $('wakeHint');

$('settingsBtn').onclick = () => {
  $('cityInput').value = settings.city || '';
  $('proxyInput').value = settings.proxyUrl || '';
  drawer.classList.add('open');
};
$('closeSettingsBtn').onclick = () => drawer.classList.remove('open');

$('wakeOnBtn').onclick = () => {
  wakeEnabled = true;
  if (window.AndroidBridge) window.AndroidBridge.toggleWake(true);
};
$('wakeOffBtn').onclick = () => {
  wakeEnabled = false;
  if (window.AndroidBridge) window.AndroidBridge.toggleWake(false);
};

if ($('accessibilityBtn')) {
  $('accessibilityBtn').onclick = () => {
    if (window.AndroidBridge) window.AndroidBridge.openAccessibilitySettings();
  };
}
if ($('overlayBtn')) {
  $('overlayBtn').onclick = () => {
    if (window.AndroidBridge) window.AndroidBridge.openOverlaySettings();
  };
}

$('saveSettingsBtn').onclick = () => {
  settings.city = $('cityInput').value.trim();
  settings.proxyUrl = $('proxyInput').value.trim();
  settings.wakeEnabled = wakeEnabled;
  localStorage.setItem('jarvisSettings', JSON.stringify(settings));
  drawer.classList.remove('open');
  wakeHint.textContent = 'Wake-word listening: ' + (wakeEnabled ? 'ON' : 'OFF — enable here');
  if (wakeEnabled && !window.AndroidBridge) startWakeListening();
  else if (!wakeEnabled) setState('idle', 'Standby');
};

// ---------- init ----------
wakeHint.textContent = 'Wake-word listening: ' + (settings.wakeEnabled ? 'ON' : 'OFF — enable here');
if (settings.wakeEnabled) {
  wakeEnabled = true;
  if (window.AndroidBridge) window.AndroidBridge.toggleWake(true);
  else startWakeListening();
}

// register service worker for installability (standalone website only)
if ('serviceWorker' in navigator && !window.AndroidBridge) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
                                   }
