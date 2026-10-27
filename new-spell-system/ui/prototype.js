(function () {
  'use strict';
  const M = window.NeoMagicDesign, $ = id => document.getElementById(id);
  const key = 'ssca-neomagic-design-v2';
  const fresh = { title: '热蚀 · 对照术式', core: 'thermal', carrier: 'projectile', structure: 'plain',
    form: 'human', intensity: 1, prep: 40, range: 32, mastery: 0, burden: 70, hypothesis: '' };
  let saved = {};
  try { saved = JSON.parse(localStorage.getItem(key) || '{}'); } catch (_) { saved = {}; }
  let state = M.normalise({ ...fresh, ...saved.state });
  let baseline = saved.baseline ? M.normalise(saved.baseline) : null;
  let records = Array.isArray(saved.records) ? saved.records.slice(0, 32) : [];
  let selected = records[0]?.id || null, timer;
  const fmt = (n, places = 1) => Number(n).toFixed(places);
  const pct = n => n === 0 ? '0%' : `${fmt(n * 100, n < .01 ? 2 : 1)}%`;
  const safe = value => String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  function persist() {
    try { localStorage.setItem(key, JSON.stringify({ state, baseline, records,
      conclusion: $('conclusion').value, hypothesis: $('hypothesis').value })); }
    catch (_) { $('note-status').textContent = '浏览器未允许本地保存；可使用导出保存评审记录。'; }
  }
  function toast(message) {
    $('toast').textContent = message; $('toast').classList.add('visible');
    clearTimeout(timer); timer = setTimeout(() => $('toast').classList.remove('visible'), 3400);
  }
  function page(name) {
    document.querySelectorAll('.page').forEach(element => element.hidden = element.id !== `page-${name}`);
    document.querySelectorAll('.tabs button').forEach(element => element.classList.toggle('active', element.dataset.page === name));
    if (name === 'experiment') renderExperiments();
    if (name === 'notes') renderNotes();
  }
  function fillInputs() {
    for (const name of ['core', 'carrier', 'form', 'intensity', 'prep', 'range', 'burden', 'title']) $(name).value = state[name];
    $('mastery').value = state.mastery * 100;
    $('hypothesis').value = state.hypothesis;
  }
  function diagram() {
    const core = M.cores[state.core], selectedStructure = M.structures[state.structure];
    let ornaments = '';
    for (let i = 0; i < 24; i++) {
      const a = i * Math.PI / 12, x = 260 + 177 * Math.cos(a), y = 206 + 177 * Math.sin(a);
      ornaments += `<line x1="${x}" y1="${y}" x2="${260 + 170 * Math.cos(a)}" y2="${206 + 170 * Math.sin(a)}" stroke="#a59a7060"/>`;
    }
    const paths = state.structure === 'split'
      ? '<path d="M288 206 L358 206 L426 126 M358 206 L452 206 M358 206 L426 286"/>'
      : state.structure === 'merge' ? '<path d="M288 206 L447 206" stroke-width="4"/><path d="M337 185 L373 206 L337 227" stroke-width="1"/>'
      : '<path d="M288 206 L447 206"/><circle cx="447" cy="206" r="22" stroke-dasharray="2 5" stroke-width="1"/>';
    $('diagram').innerHTML = `<defs><radialGradient id="plate-glow"><stop stop-color="#95d4cd" stop-opacity=".11"/><stop offset="1" stop-color="#95d4cd" stop-opacity="0"/></radialGradient></defs>
      <circle cx="260" cy="206" r="187" fill="url(#plate-glow)"/>
      <g fill="none" stroke="#b8a67b"><circle cx="260" cy="206" r="183" opacity=".35"/><circle cx="260" cy="206" r="162" opacity=".56"/><circle cx="260" cy="206" r="117" opacity=".36"/><circle cx="260" cy="206" r="61" opacity=".68"/>
      <path d="M260 68 L380 275 L140 275 Z M260 344 L140 137 L380 137 Z" opacity=".26"/>
      <path d="M98 206 H198 M260 144 V86 M260 267 V326" opacity=".4"/></g>${ornaments}
      <g fill="none" stroke="#8fd2cf" stroke-width="2">${paths}<path d="M75 206 H230" stroke="#b6a578" stroke-width="1.5"/></g>
      <g fill="#172730" stroke="#c9b980"><circle cx="260" cy="206" r="32"/><rect x="239" y="46" width="42" height="42" transform="rotate(45 260 67)"/><rect x="239" y="326" width="42" height="42" transform="rotate(45 260 347)"/></g>
      <g fill="#d4c28d" text-anchor="middle" font-family="Microsoft YaHei,serif"><text x="260" y="218" font-size="32">${core.glyph}</text><text x="260" y="73" font-size="17">源</text><text x="260" y="353" font-size="17">${state.carrier === 'projectile' ? '投' : M.carriers[state.carrier][0]}</text></g>
      <g fill="#adc0be" text-anchor="middle" font-size="11"><text x="260" y="17">来源 · 组织作用</text><text x="260" y="401">${core.name} · ${M.carriers[state.carrier]} · ${selectedStructure.short}</text><text x="97" y="230">输入</text><text x="446" y="312">${selectedStructure.branches === 3 ? '三路分配' : state.structure === 'merge' ? '窄束终端' : '直接与周边'}</text></g>
      <g fill="#172730" stroke="#789994"><circle cx="151" cy="130" r="17"/><circle cx="151" cy="282" r="17"/><circle cx="369" cy="130" r="17"/><circle cx="369" cy="282" r="17"/></g>
      <g fill="#a4b5b2" text-anchor="middle" font-size="11"><text x="151" y="134">直通</text><text x="151" y="286">直通</text><text x="369" y="134">${state.structure === 'split' ? '分流' : '直通'}</text><text x="369" y="286">${state.structure === 'merge' ? '汇合' : '直通'}</text></g>`;
  }
  function render() {
    const q = M.quote(state), profile = M.forms[state.form], now = M.risk(state, state.burden), plan = M.plan(state);
    const errors = M.validate(state);
    document.querySelectorAll('[data-structure]').forEach(element => element.classList.toggle('active', element.dataset.structure === state.structure));
    $('validity').textContent = errors.length ? '需要调整结构' : '结构可解释';
    $('validity').classList.toggle('invalid', Boolean(errors.length));
    $('intensity-value').textContent = `${fmt(state.intensity, 2)} U`;
    $('prep-value').textContent = `${fmt(state.prep / 20)} 秒`;
    $('range-value').textContent = `${state.range} 格`;
    $('mastery-value').textContent = `${fmt(state.mastery * 100, 0)}%`;
    $('quote').textContent = `+${fmt(q.actual)}`;
    $('quote-detail').textContent = `正常负担 ${fmt(q.normal)} · 当前保留 ${fmt(q.adaptation * 100, 0)}%。准备方式改变实际报价。`;
    $('budget-total').textContent = fmt(q.budget);
    $('distribution-label').textContent = state.structure === 'split' ? '每路／通路数' : '直接／周边';
    $('distribution').textContent = state.structure === 'split' ? `${fmt(q.direct)} × 3` : `${fmt(q.direct)} / ${fmt(q.splash)}`;
    $('structure-load').textContent = fmt(q.load);
    $('structure-description').textContent = errors.length ? errors.join(' ') : M.structures[state.structure].description;
    $('run').disabled = errors.length > 0;
    $('form-note').textContent = profile.note;
    $('burden-value').textContent = fmt(state.burden);
    $('safety-value').textContent = profile.safety;
    $('burden').max = profile.safety + 150;
    $('burden-fill').style.width = `${Math.min(100, state.burden / (profile.safety + 150) * 100)}%`;
    $('burden-fill').style.background = now.excess > 60 ? '#cf806e' : now.excess > 0 ? '#c4aa71' : '#8eaeaa';
    $('safety-line').style.left = `${profile.safety / (profile.safety + 150) * 100}%`;
    $('risk-stage').textContent = now.excess === 0 ? '安全范围内' : `超出安全线 ${fmt(now.excess)} 点`;
    $('risk-stage').style.color = now.excess > 0 ? 'var(--red)' : 'var(--cyan)';
    $('risk-now').textContent = `本次检测 ${pct(now.failure)}`;
    $('projected').textContent = `完整支付后预计 ${fmt(state.burden + q.actual)}。条件不再变化时，整次预估 ${pct(plan.cumulative)}。`;
    $('load-baseline').disabled = !baseline;
    diagram();
  }
  function currentRecord() { return records.find(record => record.id === selected) || records[0]; }
  function renderExperiments() {
    $('record-count').textContent = records.length;
    $('history').innerHTML = records.length ? records.map(record => `<button class="history-item ${record.id === selected ? 'active' : ''}" data-record="${safe(record.id)}"><strong>${safe(record.scheme.title)}</strong><span>${safe(M.structures[record.scheme.structure].short)} · ${safe(record.created)} · <em class="${record.status === 'failed' ? 'failure' : ''}">${record.status === 'failed' ? '释放失败' : '演算完成'}</em></span></button>`).join('')
      : '<div class="empty-state">尚无记录<br>在构式页运行一次规则预览。</div>';
    document.querySelectorAll('[data-record]').forEach(element => element.onclick = () => { selected = element.dataset.record; renderExperiments(); });
    const record = currentRecord();
    if (!record) {
      $('comparison').innerHTML = '<div class="empty-state">保存一份基准方案，再制作变式。<br>这里会分别显示条件差异与结果。</div>';
      $('timeline').innerHTML = '<div class="empty-state">检测时间线将在预览后显示。</div>'; return;
    }
    const s = record.scheme, comparisonBase = baseline || s, qb = M.quote(comparisonBase);
    const differences = [];
    if (comparisonBase.form !== s.form) differences.push('形态');
    if (comparisonBase.burden !== s.burden) differences.push('起始负担');
    if (comparisonBase.intensity !== s.intensity) differences.push('作用规模');
    if (comparisonBase.prep !== s.prep) differences.push('准备时间');
    if (comparisonBase.range !== s.range) differences.push('路径距离');
    if (comparisonBase.core !== s.core) differences.push('作用核');
    if (comparisonBase.mastery !== s.mastery) differences.push('身体适应');
    if (comparisonBase.carrier !== s.carrier) differences.push('载形');
    const detail = scheme => `${safe(M.forms[scheme.form].name)} · 起始负担 ${fmt(scheme.burden)}<br>${fmt(scheme.intensity, 2)} U · ${fmt(scheme.prep / 20)} 秒 · ${scheme.range} 格`;
    $('comparison').innerHTML = `<div class="comparison-grid"><div><small>基准方案</small><h3>${safe(M.structures[comparisonBase.structure].name)}</h3><p>${detail(comparisonBase)}<br>实际报价 ${fmt(qb.actual)} · 作用预算 ${fmt(qb.budget)}</p></div><div><small>选中的演算记录</small><h3>${safe(M.structures[s.structure].name)} · ${record.status === 'failed' ? '释放失败' : '演算完成'}</h3><p>${detail(s)}<br>已支付 ${fmt(record.paid)} · 演算输出预算 ${fmt(record.outputBudget)}<br>累计偏移 ${fmt(Math.hypot(record.yaw, record.pitch), 2)}°</p></div></div><div class="condition-note ${differences.length ? 'warning' : ''}">${!baseline ? '尚未保存基准；当前仅展示这份记录。' : differences.length ? `比较条件有差异：${differences.join('、')}。先保留疑问，不能只把结果归因于组织方式。` : '形态、起始负担和主要参数一致，可用于比较组织方式；一次抽卡结果仍不构成确定结论。'}</div>`;
    $('timeline').innerHTML = `<table class="timeline"><thead><tr><th>时点</th><th>已支付</th><th>累计／超额</th><th>本次失败率</th><th>本次偏移率</th><th>观察</th></tr></thead><tbody>${record.checkpoints.map(point => `<tr class="${point.failed ? 'failed' : point.shifted ? 'shifted' : ''}"><td>${fmt(point.tick / 20)} 秒</td><td>${fmt(point.paid)}</td><td>${fmt(point.burden)} / +${fmt(point.excess)}</td><td>${pct(point.failure)}</td><td>${pct(point.offset)}</td><td>${point.failed ? '中断 · 完整CD' : point.shifted ? `偏移累计 ${fmt(Math.hypot(point.yaw, point.pitch), 2)}°` : point.excess === 0 ? '安全通过' : '本次通过'}</td></tr>`).join('')}</tbody></table>`;
  }
  function renderNotes() {
    const last = records[0];
    $('observations').innerHTML = last
      ? `<div class="observation">最近一份演算使用${safe(M.forms[last.scheme.form].name)}，起始负担${fmt(last.scheme.burden)}，已支付${fmt(last.paid)}。</div><div class="observation">组织：${safe(M.structures[last.scheme.structure].name)}。${last.status === 'failed' ? '预览中抽到失败，没有输出预算。' : `演算输出预算为${fmt(last.outputBudget)}；尚无游戏内实际伤害测量。`}</div><div class="observation">当前有${records.length}份演算记录。这些记录不能直接作为Minecraft中的实测证据。</div>`
      : '<div class="observation">尚无演算记录。先制作基准与变式，观察可记录的结果。</div>';
  }
  for (const [id, data] of Object.entries(M.cores)) $('core').add(new Option(`${data.name} · ${data.school}`, id));
  for (const [id, name] of Object.entries(M.carriers)) $('carrier').add(new Option(name, id));
  for (const [id, data] of Object.entries(M.forms)) $('form').add(new Option(`${data.name} · 安全阈值 ${data.safety}`, id));
  $('conclusion').value = saved.conclusion || '';
  fillInputs();
  for (const name of ['core', 'carrier', 'form', 'intensity', 'prep', 'range', 'mastery', 'burden', 'title']) {
    $(name).addEventListener('input', () => {
      state[name] = ['intensity', 'prep', 'range', 'burden'].includes(name) ? Number($(name).value) : name === 'mastery' ? Number($(name).value) / 100 : $(name).value;
      if (name === 'form') { state.burden = 0; $('burden').value = 0; }
      if (name === 'core' && !M.cores[state.core].carriers.includes(state.carrier)) {
        state.carrier = M.cores[state.core].carriers[0]; $('carrier').value = state.carrier;
      }
      state = M.normalise(state); render(); persist();
    });
  }
  document.querySelectorAll('[data-page]').forEach(element => element.onclick = () => page(element.dataset.page));
  document.querySelectorAll('[data-go]').forEach(element => element.onclick = () => page(element.dataset.go));
  document.querySelectorAll('[data-structure]').forEach(element => element.onclick = () => { state.structure = element.dataset.structure; render(); persist(); });
  $('save-baseline').onclick = () => { baseline = { ...state }; persist(); render(); toast('已保存基准及其形态、起始负担与参数。'); };
  $('load-baseline').onclick = () => { if (!baseline) return; state = { ...baseline }; fillInputs(); render(); persist(); toast('已载入基准，可以只改变一个变量制作变式。'); };
  $('run').onclick = () => {
    const record = M.simulate(state, Math.floor(Math.random() * 4294967296));
    if (record.invalid) { toast(record.errors.join(' ')); return; }
    records.unshift(record); records = records.slice(0, 32); selected = record.id;
    state.burden = record.endBurden; $('burden').value = state.burden;
    persist(); render(); page('experiment');
    toast(record.status === 'failed' ? '演算中发生释放失败，已支付负担保留。' : '演算完成。结果不代表游戏中的碰撞或伤害测量。');
  };
  $('recover').onclick = () => { state.burden = 0; $('burden').value = 0; render(); persist(); };
  $('use-sample').onclick = () => { state = M.normalise(fresh); baseline = { ...state }; fillInputs(); render(); persist(); page('craft'); };
  $('save-notes').onclick = () => { state.hypothesis = $('hypothesis').value; persist(); toast('已保存评审笔记。观察与解释仍分开。'); };
  $('export-notes').onclick = () => {
    const data = { type: 'SSCA UI design review, not Minecraft research progress', version: 2,
      baseline, draft: state, hypothesis: $('hypothesis').value, conclusion: $('conclusion').value, records };
    const link = document.createElement('a'); link.href = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }));
    link.download = 'neomagic-design-review.json'; link.click(); setTimeout(() => URL.revokeObjectURL(link.href), 500);
  };
  const principles = {
    '储存': '先积累，再按指定条件释放。储存必须有容量、期限与损耗；已经成立的储存效果，不继续接受本次准备阶段的抽卡。',
    '约束': '约束作用方向或边界，改善对应控制。收益需要付出额外构型负担或准备要求，不能让危险区无条件免疫失稳。',
    '跨越': '延长传递路径或连接距离。距离会带来可观察的传导代价，空间连接的路径模型与普通投射需要分别标定。',
    '转化': '在允许的作用类型之间转换。需要明确输入、输出与损耗，没有实际能力适配的组合不能构建。'
  };
  document.querySelectorAll('[data-principle]').forEach(element => element.onclick = () => {
    $('principle-title').textContent = element.dataset.principle;
    $('principle-text').textContent = principles[element.dataset.principle]; $('principle-dialog').showModal();
  });
  $('close-principle').onclick = () => $('principle-dialog').close();
  const query = new URLSearchParams(location.search);
  if (query.get('example') === '1') {
    baseline = M.normalise({ ...fresh, intensity: 1.5, burden: 102 });
    const first = M.simulate(baseline, 2211), second = M.simulate({ ...baseline, structure: 'split', title: '热蚀 · 分流变式' }, 4020);
    records = [second, first]; selected = second.id; state = { ...baseline, structure: 'split' }; fillInputs();
  }
  render();
  const initialPage = query.get('view');
  page(['observe', 'craft', 'experiment', 'notes'].includes(initialPage) ? initialPage : 'craft');
})();
