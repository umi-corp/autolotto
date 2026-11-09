// 테마는 <head> 의 인라인 스크립트가 먼저 정한다(직접 고른 값 → 시스템 설정)
(() => {
  const root = document.documentElement;
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;

  const store = {
    get: () => { try { return localStorage.getItem('theme'); } catch { return null; } },
    set: (v) => { try { localStorage.setItem('theme', v); } catch { /* 저장 못 해도 이번 화면은 바뀐다 */ } },
  };
  document.querySelectorAll('[data-theme-toggle]').forEach((btn) => btn.addEventListener('click', () => {
    root.dataset.theme = root.dataset.theme === 'light' ? 'dark' : 'light';
    store.set(root.dataset.theme);
  }));
  matchMedia('(prefers-color-scheme: light)').addEventListener?.('change', (e) => {
    if (!store.get()) root.dataset.theme = e.matches ? 'light' : 'dark';
  });

  const nav = document.querySelector('.site-nav');
  if (nav) {
    const onScroll = () => nav.classList.toggle('scrolled', scrollY > 8);
    onScroll();
    addEventListener('scroll', onScroll, { passive: true });
  }

  const draw = () => {
    const pool = Array.from({ length: 45 }, (_, i) => i + 1);
    const pick = Array.from({ length: 7 }, () => pool.splice(Math.floor(Math.random() * pool.length), 1)[0]);
    const bonus = pick.pop();
    return [...pick.sort((a, b) => a - b), bonus];
  };
  document.querySelectorAll('[data-balls]').forEach((list) => {
    const balls = list.querySelectorAll('.ball');
    draw().forEach((n, i) => {
      balls[i].textContent = n;
      balls[i].dataset.b = Math.min(5, Math.ceil(n / 10));
    });
  });

  // 다운로드 링크·버전을 최신 릴리스로 바꾼다. 실패하면 릴리스 페이지 링크가 그대로 남는다
  const links = document.querySelectorAll('[data-apk]');
  if (links.length) {
    fetch('https://api.github.com/repos/umi-corp/autolotto/releases/latest')
      .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
      .then((rel) => {
        const apks = (rel.assets || []).filter((a) => a.name.endsWith('.apk'));
        const apk = apks.find((a) => a.name.includes('universal')) || apks[0];
        if (!apk) return;
        links.forEach((a) => { a.href = apk.browser_download_url; });
        const meta = `${rel.tag_name} · Android 12+ · ${(apk.size / 1048576).toFixed(1)}MB`;
        document.querySelectorAll('[data-apk-meta]').forEach((el) => { el.textContent = meta; });
      })
      .catch(() => {});
  }

  // 누르면 카드가 화면 높이 80%로 커진 뒤 재생한다. 누르기 전에는 YouTube 에 연결하지 않는다(쿠키·통신 없음)
  const pop = (a) => {
    const from = a.getBoundingClientRect();
    const h = Math.min(innerHeight * 0.8, innerWidth * 0.92 * 16 / 9);
    const w = h * 9 / 16;
    const to = { left: (innerWidth - w) / 2, top: (innerHeight - h) / 2, width: w, height: h };
    const box = (r) => ({ left: `${r.left}px`, top: `${r.top}px`, width: `${r.width}px`, height: `${r.height}px` });
    const ms = reduce ? 0 : 320;
    const ease = 'cubic-bezier(.2, .8, .2, 1)';

    const shade = Object.assign(document.createElement('div'), { className: 'video-shade' });
    const frame = Object.assign(document.createElement('div'), { className: 'video-pop', role: 'dialog', ariaModal: 'true', ariaLabel: a.getAttribute('aria-label') });
    Object.assign(frame.style, box(to));
    frame.append(a.querySelector('img').cloneNode());
    const close = Object.assign(document.createElement('button'), { type: 'button', className: 'video-close', ariaLabel: a.dataset.close });
    close.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>';
    // 뒤 페이지는 키보드·화면 낭독기에서 빠진다
    const behind = [...document.body.children];
    behind.forEach((el) => { el.inert = true; });
    document.body.append(shade, frame, close);
    root.classList.add('video-open');
    a.style.visibility = 'hidden';
    shade.animate([{ opacity: 0 }, { opacity: 1 }], { duration: ms });
    close.animate([{ opacity: 0 }, { opacity: 1 }], { duration: ms, delay: ms / 2, fill: 'backwards' });
    let done = false;
    frame.animate([box(from), box(to)], { duration: ms, easing: ease }).finished.then(() => {
      // 펼치는 도중 닫으면 플레이어를 붙이지 않는다
      if (done) return;
      frame.append(Object.assign(document.createElement('iframe'), {
        src: `https://www.youtube-nocookie.com/embed/${a.dataset.yt}?autoplay=1&playsinline=1&rel=0`,
        title: a.getAttribute('aria-label'),
        allow: 'autoplay; encrypted-media; picture-in-picture; fullscreen',
        allowFullscreen: true,
      }));
    });
    close.focus();

    const shut = () => {
      if (done) return;
      done = true;
      removeEventListener('popstate', shut);
      removeEventListener('keydown', onKey);
      frame.querySelector('iframe')?.remove();
      shade.animate([{ opacity: 1 }, { opacity: 0 }], { duration: ms, fill: 'forwards' });
      close.remove();
      behind.forEach((el) => { el.inert = false; });
      frame.animate([box(to), box(a.getBoundingClientRect())], { duration: ms, easing: ease, fill: 'forwards' }).finished.then(() => {
        shade.remove();
        frame.remove();
        a.style.visibility = '';
        root.classList.remove('video-open');
        a.focus({ preventScroll: true });
      });
    };
    // 휴대폰 뒤로 가기로 닫히게 기록을 하나 쌓는다
    history.pushState({ video: true }, '');
    // 연속 클릭이 기록을 두 번 되돌려 페이지를 떠나지 않게 한 번만 되돌린다
    let leaving = false;
    const back = () => { if (!leaving) { leaving = true; history.back(); } };
    const onKey = (e) => { if (e.key === 'Escape') back(); };
    addEventListener('popstate', shut);
    addEventListener('keydown', onKey);
    shade.addEventListener('click', back);
    close.addEventListener('click', back);
  };
  // 영상을 연 채 새로고침하면 남는 기록을 지운다
  if (history.state?.video) history.replaceState(null, '');
  document.querySelectorAll('[data-yt]').forEach((a) => a.addEventListener('click', (e) => {
    e.preventDefault();
    pop(a);
  }));

})();
