// 테스트용 가짜 Firebase (메모리 저장). window.__fb 로 상태 조작.
const G = (window.__fb = window.__fb || (() => { let st = []; let u = null; try { st = JSON.parse(sessionStorage.getItem('mockstore') || '[]'); u = JSON.parse(sessionStorage.getItem('mockuser') || 'null'); } catch (e) {} return { store: new Map(st), listeners: new Set(), user: u, authCbs: new Set(), seq: 0 }; })());
function persist() { try { sessionStorage.setItem('mockstore', JSON.stringify([...G.store])); sessionStorage.setItem('mockuser', JSON.stringify(G.user)); } catch (e) {} }

export const MODULE = 'mock';
// ---------- app ----------
export function initializeApp(cfg) { return { cfg }; }
// ---------- auth ----------
export function getAuth() { return G.auth || (G.auth = { get currentUser() { return G.user; }, languageCode: '' }); }
export class GoogleAuthProvider { setCustomParameters() {} }
export async function signInWithPopup() { G.user = G.nextUser || window.__fbNext; persist(); G.authCbs.forEach(cb => cb(G.user)); return { user: G.user }; }
export async function signInWithRedirect() { return signInWithPopup(); }
export async function getRedirectResult() { return null; }
export function onAuthStateChanged(a, cb) { G.authCbs.add(cb); setTimeout(() => cb(G.user), 0); return () => G.authCbs.delete(cb); }
export async function signInAnonymously() { G.user = { uid: 'anon' + Math.random().toString(36).slice(2, 8), isAnonymous: true }; persist(); G.authCbs.forEach(cb => cb(G.user)); return { user: G.user }; }
export async function signOut() { G.user = null; persist(); G.authCbs.forEach(cb => cb(null)); }
// ---------- firestore ----------
const clone = v => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));
export function initializeFirestore() { return {}; }
export function getFirestore() { return {}; }
export function persistentLocalCache() { return {}; }
export function persistentMultipleTabManager() { return {}; }
export function doc(db, ...p) { if (p[0] && p[0].kind === 'col') { const c = p[0]; return { kind: 'doc', path: c.path + '/' + p[1] }; } return { kind: 'doc', path: p.join('/') }; }
export function collection(db, ...p) { return { kind: 'col', path: p.join('/') }; }
export function where(f, op, v) { return { f, op, v }; }
export function query(col, ...w) { return { kind: 'col', path: col.path, where: w }; }
const idOf = path => path.split('/').pop();
const parentOf = path => path.split('/').slice(0, -1).join('/');
function docSnap(path) { const d = G.store.get(path); return { id: idOf(path), exists: () => d !== undefined, data: () => clone(d) }; }
function colSnap(q) {
  let docs = [...G.store.entries()].filter(([k]) => parentOf(k) === q.path);
  for (const w of q.where || []) docs = docs.filter(([, v]) => (w.op === '==' ? v[w.f] === w.v : true));
  docs = docs.map(([k, v]) => ({ id: idOf(k), data: () => clone(v) }));
  return { empty: docs.length === 0, docs, size: docs.length };
}
function notify() { persist(); queueMicrotask(() => { for (const l of [...G.listeners]) l.fire(); }); }
function check(op, path, data) { if (G.deny && G.deny(op, path, data)) { const e = new Error('Missing or insufficient permissions.'); e.code = 'permission-denied'; throw e; } }
export async function getDoc(ref) { return docSnap(ref.path); }
export async function setDoc(ref, d, opt) { check('set', ref.path, d); G.store.set(ref.path, opt && opt.merge ? { ...(G.store.get(ref.path) || {}), ...clone(d) } : clean(clone(d))); notify(); }
export async function updateDoc(ref, d) { check('update', ref.path, d); if (!G.store.has(ref.path)) { const e = new Error('no doc'); e.code = 'not-found'; throw e; } G.store.set(ref.path, { ...G.store.get(ref.path), ...clone(d) }); notify(); }
export async function deleteDoc(ref) { check('delete', ref.path); G.store.delete(ref.path); notify(); }
export async function addDoc(col, d) { const id = 'id' + Math.random().toString(36).slice(2, 10); await setDoc({ path: col.path + '/' + id }, d); return { id }; }
function clean(o) { if (o && typeof o === 'object') for (const k in o) if (o[k] === undefined) delete o[k]; return o; }
export function writeBatch() {
  const ops = [];
  return {
    set: (r, d, o) => ops.push(() => setDoc(r, d, o)), update: (r, d) => ops.push(() => updateDoc(r, d)), delete: r => ops.push(() => deleteDoc(r)),
    commit: async () => { for (const o of ops) await o(); }
  };
}
export function onSnapshot(ref, cb, err) {
  const l = { fire: () => { try { if (ref.kind === 'doc') cb(docSnap(ref.path)); else cb(colSnap(ref)); } catch (e) { console.error(e); } } };
  G.listeners.add(l); setTimeout(l.fire, 0); return () => G.listeners.delete(l);
}
