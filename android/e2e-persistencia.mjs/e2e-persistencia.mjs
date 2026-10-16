// Verificacion enfocada del guardado duradero (espejo nativo). Solo APK de depuracion (CDP).
import fs from 'node:fs';
const EV = process.env.EVIDENCE || 'evidence';
const MODE = process.env.MODE || 'verifica';
const log = (...a) => console.log('[e2e]', ...a);
const sleep = ms => new Promise(r => setTimeout(r, ms));
let tabs = null;
for (let i = 0; i < 12 && !tabs; i++) {
  try { tabs = await (await fetch('http://127.0.0.1:9222/json')).json(); }
  catch (e) { log('CDP no listo (' + i + ')'); await sleep(2000); }
}
if (!tabs) { log('SIN PAGINA DEPURABLE (CDP no responde)'); process.exit(1); }
const tab = tabs.find(t => t.type === 'page' && /^https?:/.test(t.url)) || tabs.find(t => t.type === 'page');
if (!tab) { log('SIN PAGINA DEPURABLE'); process.exit(1); }
log('pagina', tab.url);
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise(r => (ws.onopen = r));
let id = 0; const pend = {};
ws.onmessage = m => { const d = JSON.parse(m.data); if (d.id && pend[d.id]) pend[d.id](d); };
const send = (method, params = {}) => new Promise(r => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
const ev = async e => { const r = await send('Runtime.evaluate', { expression: e, awaitPromise: true, returnByValue: true }); return r.result.result?.value ?? JSON.stringify(r.result); };
await send('Runtime.enable');
const waitFor = async (expr, ms = 40000) => { for (let t = 0; t < ms; t += 1000) { try { if (await ev(expr) === true) return true; } catch {} await sleep(1000); } return false; };
const aseguraPagina = async () => {
  for (let i = 0; i < 6; i++) {
    const u = await ev('location.href');
    if (!u.includes('xesco-tejedor.github.io')) {
      await ev(`location.href='https://xesco-tejedor.github.io/Hilvan/'`); await sleep(15000);
      await waitFor(`document.readyState==='complete'`, 30000); continue;
    }
    const h = await ev(`(document.querySelector('h1')||{}).innerText||''`);
    if (h) return true;
    await ev('location.reload()'); await sleep(12000);
    await waitFor(`document.readyState==='complete'`, 30000);
  }
  return false;
};


if (MODE === 'escribe') {
  await waitFor(`document.readyState==='complete'`, 45000); await aseguraPagina();
  let parcheada = false;
  for (let i = 0; i < 16 && !parcheada; i++) {
    parcheada = await ev(`typeof save==='function' && save.toString().includes('guardarEstado')`);
    if (!parcheada) { log('la web aun no sirve la version con espejo (intento', i + 1, '): recargando'); await ev('location.reload()'); await sleep(15000); await waitFor(`document.readyState==='complete'`, 30000); await aseguraPagina(); }
  }
  log('web con espejo', parcheada);
  if (!parcheada) process.exit(1);
  log('puente guardarEstado', await ev(`typeof (window.HilvanAndroid||{}).guardarEstado`));
  await ev(`S={title:'Relato de prueba',segs:[{id:'seg1',kind:'inicio',text:'contenido persistencia xyz',escena:''}],bible:{chars:[{nombre:'Ana',rol:'',aspecto:''}],facts:[]},seed:7,params:{},genres:['Terror']}; ROSTER.length=0; ROSTER.push({id:'r1',nombre:'Ana',rol:'',aspecto:'',notas:'',de:''}); save(); rsave(); 'ok'`);
  log('espejo relato escrito', await ev(`(NAV.leerEstado('hilvan-v1')||'').includes('contenido persistencia xyz')`));
  log('espejo reparto escrito', await ev(`(NAV.leerEstado('hilvan-roster')||'').includes('Ana')`));
  process.exit(0);
}
if (MODE === 'verifica') {
  await waitFor(`document.readyState==='complete'`, 45000); await aseguraPagina(); await sleep(2500);
  log('localStorage sobrevivio al kill', await ev(`(localStorage.getItem('hilvan-v1')||'').includes('contenido persistencia xyz')`));
  log('relato restaurado tras force-stop', await ev(`typeof S!=='undefined' && !!S && Array.isArray(S.segs) && S.segs.length===1 && S.segs[0].text==='contenido persistencia xyz'`));
  log('reparto presente', await ev(`ROSTER.length===1 && ROSTER[0].nombre==='Ana'`));
  process.exit(0);
}
if (MODE === 'borra') {
  await waitFor(`document.readyState==='complete'`, 45000); await aseguraPagina(); await sleep(2000);
  await ev(`window.confirm=()=>true; resetStory(); 'ok'`); await sleep(500);
  log('local borrada', await ev(`localStorage.getItem('hilvan-v1')===null`));
  log('espejo borrado', await ev(`NAV.leerEstado('hilvan-v1')===null`));
  log('reparto sigue tras Nuevo', await ev(`ROSTER.length===1 && ROSTER[0].nombre==='Ana'`));
  process.exit(0);
}
if (MODE === 'verifica2') {
  await waitFor(`document.readyState==='complete'`, 45000); await aseguraPagina(); await sleep(2500);
  log('relato NO resucita', await ev(`typeof S==='undefined' || S===null || (Array.isArray(S.segs) && S.segs.length===0)`));
  log('reparto conservado tras Nuevo + kill', await ev(`ROSTER.length===1 && ROSTER[0].nombre==='Ana'`));
  process.exit(0);
}
