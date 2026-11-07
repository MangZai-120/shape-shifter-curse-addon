(() => {
  'use strict';
  const params = new URLSearchParams(location.search);
  const lesson = window.SSCA_TUTORIAL?.lessons.find(item => item.id === params.get('lesson'));
  const video = document.getElementById('video');
  const play = document.getElementById('play-video');
  const english = params.get('lang') === 'en_us';
  document.documentElement.lang = english ? 'en' : 'zh-CN';
  if (!lesson) { play.textContent = english ? 'Video unavailable' : '暂无演示'; play.disabled = true; return; }
  video.poster = 'media/' + lesson.media + '.jpg';
  video.dataset.media = lesson.media;
  play.textContent = english ? '▶ Play' : '▶ 播放';
  play.onclick = () => {
    if (!video.querySelector('source')) {
      video.preload = 'metadata';
      for (const [extension, type] of [['webm', 'video/webm'], ['mp4', 'video/mp4']]) {
        const source = document.createElement('source');
        source.src = 'media/' + lesson.media + '.' + extension;
        source.type = type;
        video.append(source);
      }
      video.controls = true;
      video.load();
    }
    play.hidden = true;
    video.play().catch(() => { play.hidden = false; });
  };
  document.addEventListener('visibilitychange', () => { if (document.hidden) video.pause(); });
  document.documentElement.dataset.sscaPlayer = 'ready';
  // A fragment lets the native player observe readiness without a JavaScript bridge or global CEF handlers.
  history.replaceState(null, '', location.pathname + location.search + '#ssca-player-ready');
})();
