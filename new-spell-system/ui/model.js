(function (root) {
  'use strict';
  const clamp = (value, low, high) => Math.min(high, Math.max(low, Number(value)));
  const cores = {
    thermal: { name: '热蚀', school: '火焰', glyph: '火', a: 6, b: 18, carriers: ['projectile', 'contact', 'area'] },
    frost: { name: '降温', school: '寒霜', glyph: '霜', a: 5, b: 15, carriers: ['projectile', 'contact', 'area'] },
    lunar: { name: '修复', school: '月辉', glyph: '月', a: 8, b: 20, carriers: ['projectile', 'contact', 'area', 'self', 'lock'] },
    curse: { name: '附着', school: '诅咒', glyph: '咒', a: 4, b: 12, carriers: ['projectile', 'contact', 'lock'] },
    summon: { name: '构成', school: '召唤', glyph: '构', a: 10, b: 30, carriers: ['area', 'lock'] },
    void: { name: '侵蚀', school: '虚无', glyph: '蚀', a: 12, b: 36, carriers: ['projectile', 'contact', 'area'] },
    space: { name: '连接', school: '空间', glyph: '隙', a: 14, b: 42, carriers: ['self', 'lock'] }
  };
  const carriers = { projectile: '投射', contact: '接触', area: '区域', self: '自体', lock: '锁定' };
  const structures = {
    plain: { name: '直通组织', short: '直通', glyph: 'Ⅰ', load: 0, branches: 1, description: '保留直接作用与少量周边扩散。' },
    split: { name: '三路分流', short: '分流', glyph: 'Ⅲ', load: 2, branches: 3, description: '既有作用量分配到三条通路，扩大覆盖。' },
    merge: { name: '聚束汇合', short: '汇合', glyph: '◆', load: 2.5, branches: 1, description: '作用集中到窄束，舍弃周边扩散。' }
  };
  const forms = {
    human: { name: '人类', safety: 100, failure: 1, offset: 1, note: '中性承载样本' },
    axolotl: { name: '荧光幼灵', safety: 80, failure: 1.15, offset: .85, note: '方向控制更好，热蚀承载更吃力' },
    familiar: { name: '使魔', safety: 200, failure: .8, offset: .9, note: '聚束更易控制，分流另有负担' }
  };
  function normalise(input) {
    return {
      title: String(input.title || '未命名术式').slice(0, 32),
      hypothesis: String(input.hypothesis || '').slice(0, 240),
      core: cores[input.core] ? input.core : 'thermal',
      carrier: carriers[input.carrier] ? input.carrier : 'projectile',
      structure: structures[input.structure] ? input.structure : 'plain',
      form: forms[input.form] ? input.form : 'human',
      intensity: clamp(input.intensity ?? 1, .5, 4),
      prep: Math.round(clamp(input.prep ?? 40, 0, 80)),
      range: clamp(input.range ?? 32, 4, 40),
      mastery: clamp(input.mastery ?? 0, 0, 1),
      burden: clamp(input.burden ?? 70, 0, 500)
    };
  }
  function validate(input) {
    const s = normalise(input), errors = [];
    if (!cores[s.core].carriers.includes(s.carrier)) errors.push('这个作用核尚无此载形的适配关系。');
    if (s.carrier === 'self' && s.structure === 'split') errors.push('自体载形没有可分配的多条目标通路。');
    if (s.core === 'summon' && s.structure === 'merge') errors.push('构成核需要先定义集中构成的合法目标。');
    return errors;
  }
  function quote(input) {
    const s = normalise(input), core = cores[s.core], structure = structures[s.structure];
    const path = s.core === 'space' ? (s.range / 16) ** 2 : s.range / 16;
    const normal = core.a * s.intensity + core.b * s.intensity ** 2 / (s.prep / 20 + .5)
      + 1.5 * path + 2 * structure.load;
    const floor = s.structure === 'split' ? .4 : s.structure === 'merge' ? .35 : .5;
    const adaptation = 1 - (1 - floor) * s.mastery;
    const budget = 9 * s.intensity;
    return { normal, adaptation, actual: normal * adaptation, budget, branches: structure.branches,
      direct: s.structure === 'plain' ? 6 * s.intensity : s.structure === 'split' ? budget / 3 : budget,
      splash: s.structure === 'plain' ? 3 * s.intensity : 0, load: structure.load, floor };
  }
  function modifiers(s) {
    const form = forms[s.form];
    let failure = form.failure;
    if (s.form === 'familiar') failure *= s.structure === 'merge' ? .8 : s.structure === 'split' ? 1.1 : 1;
    return { failure, offset: form.offset };
  }
  function risk(input, burden) {
    const s = normalise(input), q = quote(s), form = forms[s.form], m = modifiers(s);
    const excess = Math.max(0, Number(burden) - form.safety);
    if (excess === 0) return { excess: 0, failure: 0, offset: 0, angle: 0 };
    return {
      excess,
      failure: clamp(.03 * (excess / 10) ** 1.3 * (q.actual / 20) ** .7 * m.failure, 0, 1),
      offset: clamp(.08 * (excess / 10) * Math.sqrt(q.actual / 20) * m.offset, 0, 1),
      angle: Math.min(12, .18 * excess ** .7 * (q.actual / 20) ** .35)
    };
  }
  function plan(input) {
    const s = normalise(input), q = quote(s), checkpoints = [];
    for (let t = 0; t <= s.prep; t += 10) {
      const paid = q.actual * (s.prep === 0 ? 1 : t / s.prep);
      checkpoints.push({ tick: t, paid, burden: s.burden + paid, ...risk(s, s.burden + paid) });
    }
    return { checkpoints, cumulative: 1 - checkpoints.reduce((value, p) => value * (1 - p.failure), 1) };
  }
  function rng(seed) {
    let n = seed >>> 0;
    return () => { n = (Math.imul(n, 1664525) + 1013904223) >>> 0; return n / 4294967296; };
  }
  function simulate(input, seed) {
    const s = normalise(input), errors = validate(s);
    if (errors.length) return { invalid: true, errors };
    const q = quote(s), next = rng(seed), checkpoints = [];
    let paid = 0, yaw = 0, pitch = 0, failed = false, endTick = s.prep;
    for (let tick = 0; tick <= s.prep; tick++) {
      const billed = tick === s.prep ? tick : Math.floor(tick / 2) * 2;
      paid = q.actual * (s.prep === 0 ? 1 : billed / s.prep);
      if (tick % 10 !== 0) continue;
      const r = risk(s, s.burden + paid);
      failed = r.failure > 0 && next() < r.failure;
      const shifted = !failed && r.offset > 0 && next() < r.offset;
      if (shifted) { yaw += (next() * 2 - 1) * r.angle; pitch += (next() * 2 - 1) * r.angle; }
      checkpoints.push({ tick, paid, burden: s.burden + paid, ...r, failed, shifted, yaw, pitch });
      if (failed) { endTick = tick; break; }
    }
    return { id: String(Date.now()) + '-' + seed, seed, scheme: s, quote: q, checkpoints,
      status: failed ? 'failed' : 'complete', paid, endBurden: s.burden + paid, endTick,
      yaw, pitch, outputBudget: failed ? 0 : q.budget, created: new Date().toLocaleTimeString('zh-CN', { hour12: false }) };
  }
  const api = { cores, carriers, structures, forms, normalise, validate, quote, risk, plan, simulate };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.NeoMagicDesign = api;
})(typeof window !== 'undefined' ? window : globalThis);
