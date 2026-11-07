(() => {
  'use strict';
  const data = window.SSCA_TUTORIAL;
  if (!data) return;
  const element = id => document.getElementById(id);
  const params = new URLSearchParams(location.search);
  let locale = params.get('lang') === 'en_us' ? 'en_us' : 'zh_cn';
  let current;
  const labels = {
    zh_cn: {
      form: '灵界之主', active: '主动技能', passive: '被动与特性', how: '如何触发', note: '记住这一点',
      previous: '← 上一项', next: '下一项 →', observer: '第二名玩家摄像', self: '本人第一人称',
      help: '点击播放，可暂停、拖动进度和重复观看。', enlarge: '放大视频', search: '查找技能或被动', play: '播放演示',
      footer: '每页介绍一个技能或被动，只包含 SSCA 新增或改动的内容。'
    },
    en_us: {
      form: 'Spirit Realm Lord', active: 'Active skills', passive: 'Passives and traits', how: 'How to use', note: 'Remember',
      previous: '← Previous', next: 'Next →', observer: 'Recorded by the second player', self: 'First-person view',
      help: 'Play, pause, seek or replay the demonstration.', enlarge: 'Enlarge video', search: 'Find a skill or passive', play: 'Play demonstration',
      footer: 'One skill or passive per page. Only SSCA additions and changes are included.'
    }
  };
  const keys = {
    primary: ['主技能键', 'primary skill key'], secondary: ['副技能键', 'secondary skill key'],
    sneak: ['潜行键', 'sneak key'], use: ['使用键', 'use key'], attack: ['攻击键', 'attack key'],
    jump: ['跳跃键', 'jump key'], sprint: ['疾跑键', 'sprint key']
  };

  function resolve(text) {
    return text.replace(/\{(primary|secondary|sneak|use|attack|jump|sprint)\}/g,
      (_, key) => (params.get(key) || keys[key][locale === 'zh_cn' ? 0 : 1]).slice(0, 80));
  }

  function selected() {
    let id;
    try { id = decodeURIComponent(location.hash.slice(1)); } catch { return data.lessons[0]; }
    return data.lessons.find(lesson => lesson.id === id) || data.lessons[0];
  }

  function navigation() {
    const query = element('search').value.trim().toLowerCase();
    element('lessons').replaceChildren();
    for (const lesson of data.lessons) {
      const title = lesson.text[locale].title;
      if (query && !title.toLowerCase().includes(query)) continue;
      const item = document.createElement('li');
      const link = document.createElement('a');
      link.href = '#' + lesson.id;
      link.textContent = title;
      if (lesson.id === current.id) { item.className = 'current'; link.setAttribute('aria-current', 'page'); }
      link.onclick = () => element('lessons').closest('.wy-nav-side').classList.remove('open');
      item.append(link);
      element('lessons').append(item);
    }
  }

  function removeLargeVideo() {
    const player = element('video-slot').querySelector('video');
    if (player) { player.pause(); player.removeAttribute('src'); player.replaceChildren(); player.load(); }
    element('video-slot').replaceChildren();
  }

  function closeDialog() {
    if (element('video-dialog').open) element('video-dialog').close();
    removeLargeVideo();
  }

  function prepareVideo(player) {
    player.controls = true;
    player.preload = 'metadata';
    if (player.querySelector('source')) return;
    for (const [extension, type] of [['webm', 'video/webm'], ['mp4', 'video/mp4']]) {
      const source = document.createElement('source');
      source.src = 'media/' + player.dataset.media + '.' + extension;
      source.type = type;
      player.append(source);
    }
    player.load();
  }

  function render() {
    closeDialog();
    current = selected();
    const strings = labels[locale];
    const text = current.text[locale];
    const index = data.lessons.indexOf(current);
    const video = element('video');
    video.pause();
    video.removeAttribute('src');
    video.replaceChildren();
    video.load();
    video.controls = false;
    video.preload = 'none';
    video.dataset.media = current.media;
    video.poster = 'media/' + current.media + '.jpg';
    element('play-video').hidden = false;
    element('play-video').textContent = '▶ ' + strings.play;
    element('title').textContent = text.title;
    element('effect').textContent = resolve(text.effect);
    element('trigger').textContent = resolve(text.trigger);
    element('note').textContent = resolve(text.note);
    element('kind').textContent = current.kind === 'ACTIVE' ? strings.active : strings.passive;
    element('how-title').textContent = strings.how;
    element('note-title').textContent = strings.note;
    element('form-name').textContent = strings.form;
    element('breadcrumb-form').textContent = strings.form;
    element('counter').textContent = (index + 1) + ' / ' + data.lessons.length;
    element('view').textContent = current.view === 'self' ? strings.self : strings.observer;
    element('video-help').textContent = strings.help;
    element('enlarge').textContent = strings.enlarge;
    element('search').placeholder = strings.search;
    element('footer-text').textContent = strings.footer;
    element('language').textContent = locale === 'zh_cn' ? 'English' : '简体中文';
    for (const [id, step, label] of [['previous', -1, strings.previous], ['next', 1, strings.next]]) {
      const link = element(id);
      const next = index + step;
      const valid = next >= 0 && next < data.lessons.length;
      link.textContent = label;
      link.setAttribute('aria-disabled', String(!valid));
      link.href = '#' + (valid ? data.lessons[next].id : current.id);
    }
    document.documentElement.lang = locale === 'zh_cn' ? 'zh-CN' : 'en';
    document.title = text.title + ' · ' + strings.form + ' - SSCA Wiki';
    navigation();
    document.documentElement.dataset.sscaTutorial = 'ready';
    window.scrollTo(0, 0);
  }

  element('play-video').onclick = () => {
    const video = element('video');
    const lesson = current.id;
    element('play-video').hidden = true;
    prepareVideo(video);
    video.play().catch(() => { if (current.id === lesson) element('play-video').hidden = false; });
  };
  element('search').addEventListener('input', navigation);
  window.addEventListener('hashchange', render);
  element('language').onclick = () => {
    locale = locale === 'zh_cn' ? 'en_us' : 'zh_cn';
    params.set('lang', locale);
    history.replaceState(null, '', location.pathname + '?' + params.toString() + location.hash);
    render();
  };
  element('menu').onclick = () => document.querySelector('.wy-nav-side').classList.toggle('open');
  element('enlarge').onclick = () => {
    const player = element('video');
    const large = player.cloneNode(true);
    player.pause();
    large.removeAttribute('id');
    large.addEventListener('loadedmetadata', () => { large.currentTime = player.currentTime; }, {once: true});
    prepareVideo(large);
    element('video-slot').replaceChildren(large);
    element('video-dialog').showModal();
  };
  element('close-video').onclick = closeDialog;
  element('video-dialog').addEventListener('click', event => { if (event.target === element('video-dialog')) closeDialog(); });
  element('video-dialog').addEventListener('close', removeLargeVideo);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { element('video').pause(); element('video-slot').querySelector('video')?.pause(); }
  });
  render();
})();
