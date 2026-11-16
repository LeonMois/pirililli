// Pirililli client helpers: low-latency sounds via Web Audio (works on iPad once unlocked by the first tap)
// and keeping the screen awake.
(function () {
  if (window.urna) return;
  const AC = window.AudioContext || window.webkitAudioContext;
  const ctx = AC ? new AC() : null;
  const buffers = {};
  let wakeLock = null;

  async function load(name, url) {
    if (!ctx || !url) return;
    const res = await fetch(url);
    const data = await res.arrayBuffer();
    buffers[name] = await new Promise((ok, fail) => ctx.decodeAudioData(data, ok, fail));
  }

  function unlock() {
    if (ctx && ctx.state !== 'running') {
      ctx.resume();
      // playing a silent buffer inside a user gesture is what really unlocks audio on older iOS
      const s = ctx.createBufferSource();
      s.buffer = ctx.createBuffer(1, 1, 22050);
      s.connect(ctx.destination);
      s.start(0);
    }
    if ('wakeLock' in navigator && !wakeLock) {
      navigator.wakeLock.request('screen').then(l => {
        wakeLock = l;
        l.addEventListener('release', () => (wakeLock = null));
      }).catch(() => {});
    }
  }

  function play(name) {
    if (!ctx) return;
    const b = buffers[name];
    if (!b) return;
    const s = ctx.createBufferSource();
    s.buffer = b;
    s.connect(ctx.destination);
    s.start(0);
  }

  // click sound immediately on touch, without waiting for the server round trip
  document.addEventListener('pointerdown', e => {
    unlock();
    if (e.target.closest && e.target.closest('.key')) play('click');
  }, true);
  ['touchend', 'click'].forEach(t => document.addEventListener(t, unlock, true));
  document.addEventListener('touchstart', () => {}, { passive: true }); // enables :active on iOS
  document.addEventListener('keydown', e => {
    if (e.repeat || e.ctrlKey || e.metaKey) return;
    if (/^[0-9]$/.test(e.key) || ['Enter', 'Backspace', 'Delete', ' ', 'b', 'B'].includes(e.key)) play('click');
  }, true);
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') wakeLock = null; });
  // block pinch zoom / long-press menus on the machine
  document.addEventListener('gesturestart', e => e.preventDefault());
  document.addEventListener('contextmenu', e => { if (e.target.closest && e.target.closest('.stage')) e.preventDefault(); });

  window.urna = {
    init(confirmUrl, clickUrl) {
      load('confirm', confirmUrl).catch(console.error);
      load('click', clickUrl).catch(console.error);
    },
    play,
  };
})();
