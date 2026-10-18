(() => {
  const lum = c => { const m = c.match(/[\d.]+/g); if (!m) return null; const [r, g, b, a] = m.map(Number); const f = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return { L: 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b), a: a == null ? 1 : a, rgb: [r, g, b] }; };
  const bgOf = el => { let e = el; while (e) { const cs = getComputedStyle(e); const c = lum(cs.backgroundColor); if (c && c.a > 0.5) return c; e = e.parentElement; } return lum('rgb(255,255,255)'); };
  const out = [];
  for (const el of document.querySelectorAll('body *')) {
    if (!el.offsetParent && getComputedStyle(el).position !== 'fixed') continue;
    const txt = [...el.childNodes].filter(n => n.nodeType === 3).map(n => n.textContent.trim()).join('').trim();
    if (!txt) continue;
    const r = el.getBoundingClientRect(); if (r.width < 2 || r.height < 2 || r.bottom < 0 || r.top > 4000) continue;
    const cs = getComputedStyle(el); const fg = lum(cs.color); const bg = bgOf(el); if (!fg) continue;
    let op = 1, e = el; while (e) { op *= +getComputedStyle(e).opacity; e = e.parentElement; }
    // 불투명도 반영: 글자색을 배경과 섞기
    const mix = fg.rgb.map((v, i) => v * fg.a * op + bg.rgb[i] * (1 - fg.a * op)); const f2 = lum('rgb(' + mix.join(',') + ')');
    const cr = (Math.max(f2.L, bg.L) + 0.05) / (Math.min(f2.L, bg.L) + 0.05);
    const size = parseFloat(cs.fontSize), bold = +cs.fontWeight >= 600;
    const need = size >= 18.5 || (size >= 14 && bold) ? 3 : 4.5;
    if (cr < need && !el.closest('[disabled]') && !(el.tagName === 'BUTTON' && el.disabled)) out.push(cr.toFixed(2) + ' ' + size + 'px ' + el.tagName.toLowerCase() + '.' + [...el.classList].join('.') + ' "' + txt.slice(0, 24) + '"');
  }
  return [...new Set(out)];
})()
