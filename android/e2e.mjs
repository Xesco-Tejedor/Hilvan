// Controla el WebView real del emulador por CDP (solo APK de depuracion). Pruebas de Hilvan.
import { execSync } from 'node:child_process';
import fs from 'node:fs';
const EV = process.env.EVIDENCE || 'evidence';
const MODE = process.env.MODE || 'main';
const log = (...a) => console.log('[e2e]', ...a);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const shot = n => fs.writeFileSync(`${EV}/${n}.png`, execSync('adb exec-out screencap -p', { maxBuffer: 1 << 26 }));
let tabs = null;
for (let i = 0; i < 12 && !tabs; i++) {
  try { tabs = await (await fetch('http://127.0.0.1:9222/json')).json(); }
  catch (e) { log('CDP no listo (' + i + '):', String(e).slice(0, 60)); await sleep(2000); }
}
if (!tabs) { log('SIN PAGINA DEPURABLE (CDP no responde)'); process.exit(1); }
const tab = tabs.find(t => t.type === 'page' && /^https?:/.test(t.url)) || tabs.find(t => t.type === 'page');
if (!tab) { log('SIN PAGINA DEPURABLE'); process.exit(1); }
log('pagina', tab.url);
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise(r => (ws.onopen = r));
ws.onclose = () => log('CONEXION DE DEPURACION CERRADA (la app se cerro o recargo)');
let id = 0; const pend = {};
ws.onmessage = m => { const d = JSON.parse(m.data); if (d.id && pend[d.id]) pend[d.id](d);
  if (d.method === 'Runtime.exceptionThrown') log('EXC', JSON.stringify(d.params.exceptionDetails).slice(0, 200)); };
const send = (method, params = {}) => new Promise(r => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
const ev = async e => { const r = await send('Runtime.evaluate', { expression: e, awaitPromise: true, returnByValue: true }); return r.result.result?.value ?? JSON.stringify(r.result); };
await send('Runtime.enable');
const waitFor = async (expr, ms = 40000) => { for (let t = 0; t < ms; t += 1000) { try { if (await ev(expr) === true) return true; } catch {} await sleep(1000); } return false; };

if (MODE === 'set') { await waitFor(`document.readyState==='complete'`, 45000); await aseguraPagina(); await ev(`localStorage.setItem('hilvan_e2e_marca','sobrevive-'+${JSON.stringify(process.env.MARK || 'x')})`); log('persistencia: marcador escrito', await ev(`localStorage.getItem('hilvan_e2e_marca')`)); process.exit(0); }
if (MODE === 'get') { await waitFor(`document.readyState==='complete'`, 45000); await aseguraPagina(); log('persistencia: marcador tras reinicio', await ev(`localStorage.getItem('hilvan_e2e_marca')`)); process.exit(0); }
if (MODE === 'online') { await waitFor(`document.readyState==='complete'`, 30000); log('red recuperada: titulo', await ev('document.title')); process.exit(0); }

const aseguraPagina = async () => {
  for (let i = 0; i < 6; i++) {
    const h = await ev(`(document.querySelector('h1')||{}).innerText||''`);
    if (h) { if (i > 0) log('carga recuperada tras reintento', i); return true; }
    log('pagina no cargada (intento', i + 1, '): recargando');
    await ev('location.reload()'); await sleep(12000);
    await waitFor(`document.readyState==='complete'`, 30000);
  }
  return false;
};

if (MODE === 'main') {
  await waitFor(`document.readyState==='complete'`, 45000);
  await aseguraPagina();
  log('origen', await ev('location.origin'));
  log('UA', await ev('navigator.userAgent'));
  log('titulo', await ev('document.title'));
  log('cabecera h1', await ev(`(document.querySelector('h1')||{}).innerText||''`));
  log('puente dictar/leer/guardar/imprimir', await ev(`JSON.stringify(['dictar','pararDictar','leer','pararLectura','guardar','imprimir'].map(m=>typeof (window.HilvanAndroid||{})[m]))`));
  log('boton dictar presente', await ev(`!![...document.querySelectorAll('button')].find(b=>/Dictar/.test(b.textContent))`));
  // Lectura en voz alta nativa: el puente confirma con hilvanFinLectura al terminar la ultima pausa
  await ev(`window.__fin=0; window.hilvanFinLectura=()=>{window.__fin++}; 'ok'`);
  await ev(`window.HilvanAndroid.leer(JSON.stringify([{t:'Prueba de voz de Hilván.',pitch:1,rate:1,pause:200},{t:'segunda frase más lenta',pitch:1.1,rate:0.9,pause:100}])); 'ok'`);
  log('tts: callback fin de lectura', await waitFor('window.__fin>=1', 40000));
  // Dictado: sin microfono real debe terminar el ciclo (no-match/timeout) sin tumbar la app
  await ev(`window.__dfin=0; window.__dic=0; window.hilvanDictadoFin=()=>{window.__dfin++}; window.hilvanDictado=t=>{window.__dic++}; 'ok'`);
  await ev(`window.HilvanAndroid.dictar(); 'ok'`);
  log('dictado: ciclo termina sin audio (no-match/timeout/cancel esperado)', await waitFor('window.__dfin>=1', 100000));
  log('dictado: textos reconocidos (0 esperable sin micro)', await ev('window.__dic'));
  shot('02-tras-voz');
  process.exit(0);
}
if (MODE === 'exportprint') {
  await ev(`window.HilvanAndroid.guardar('hilvan-e2e-prueba.txt','text/plain','contenido e2e Hilván ✓'); 'ok'`);
  await sleep(1500);
  await ev(`window.HilvanAndroid.imprimir('Hilván e2e','<html><body><h1>Prueba de impresión</h1></body></html>'); 'ok'`);
  await sleep(4000);
  process.exit(0);
}
if (MODE === 'api36') {
  await waitFor(`document.readyState==='complete'`, 45000); await sleep(1000);
  log('api36 titulo', await ev('document.title'));
  log('api36 cabecera visible bajo la barra de estado', await ev(`(()=>{const h=document.querySelector('h1');return !!h&&h.getBoundingClientRect().top>=0&&h.getBoundingClientRect().height>0})()`));
  log('api36 puente', await ev(`typeof (window.HilvanAndroid||{}).leer`));
  process.exit(0);
}
if (MODE === 'offline') {
  await sleep(3000);
  log('sin red: app renderiza desde cache', await ev(`document.title.includes('Hilv')`));
  log('sin red: cabecera', await ev(`(document.querySelector('h1')||{}).innerText||''`));
  process.exit(0);
}
log('MODO DESCONOCIDO', MODE); process.exit(1);
