'use strict';

/** 같은 사이트 API 호출. 서버는 X-MathMap 헤더가 있는 JSON 요청만 받는다. */
async function api(method, url, body) {
  const opts = { method, headers: { 'X-MathMap': '1' }, credentials: 'same-origin' };
  if (body instanceof FormData) {
    opts.body = body;
  } else if (body !== undefined) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(url, opts);
  let data = {};
  try { data = await res.json(); } catch (e) { /* 빈 응답 */ }
  if (!res.ok) {
    const err = new Error(data.error || '요청을 처리하지 못했어요.');
    err.status = res.status;
    throw err;
  }
  return data;
}

function $(id) { return document.getElementById(id); }

/** /r/{방id} 또는 /t/{방id} 주소에서 방 id 꺼내기 */
function roomIdFromPath() {
  const m = location.pathname.match(/^\/[rt]\/([0-9a-z]{10})\/?$/);
  return m ? m[1] : null;
}

/** 학생에게 보낼 공유 주소 */
function shareUrl(roomId) {
  return location.origin + '/r/' + roomId;
}

/** 클립보드 복사 (안 되면 선택 상자로 보여줌) */
async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch (e) {
    window.prompt('아래 주소를 복사하세요', text);
    return false;
  }
}
function show(el, on) { el.classList.toggle('hidden', !on); }

/** 요소를 만들고 글자는 항상 textContent 로 넣는다 (HTML 해석 안 함) */
function el(tag, props, ...children) {
  const e = document.createElement(tag);
  if (props) {
    for (const [k, v] of Object.entries(props)) {
      if (k === 'class') e.className = v;
      else if (k === 'text') e.textContent = v;
      else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
      else e.setAttribute(k, v);
    }
  }
  for (const c of children) {
    if (c == null) continue;
    e.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return e;
}

/**
 * 끊기면 자동으로 다시 연결하는 실시간 연결.
 * 태블릿 화면이 꺼졌다 켜지거나 와이파이가 잠깐 끊겨도 계속 다시 시도한다.
 */
function connectSocket(path, onMessage, onRetry) {
  let ws = null;
  let stopped = false;
  let retry = 0;
  let pingTimer = null;
  let retryTimer = null;

  function open() {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    if (ws && ws.readyState <= 1) { try { ws.close(); } catch (e) { /* 무시 */ } }
    const my = new WebSocket(proto + '//' + location.host + path);
    ws = my;
    my.onopen = () => {
      if (my !== ws) return;
      retry = 0;
      clearInterval(pingTimer);
      pingTimer = setInterval(() => { if (my.readyState === 1) my.send('{"type":"ping"}'); }, 25000);
    };
    my.onmessage = (ev) => {
      if (my !== ws) return; // 예전 연결에서 온 메시지는 무시
      let msg;
      try { msg = JSON.parse(ev.data); } catch (e) { return; }
      onMessage(msg);
    };
    my.onclose = () => {
      if (my !== ws || stopped) return;
      clearInterval(pingTimer);
      retry++;
      if (onRetry && retry % 3 === 0) onRetry(retry);
      clearTimeout(retryTimer);
      retryTimer = setTimeout(open, Math.min(1000 * retry, 5000));
    };
  }
  // 화면이 열려 있는 동안 4분마다 서버에 신호 (무료 서버가 15분 동안 요청이 없으면 잠들기 때문)
  const keepAlive = setInterval(() => {
    if (!stopped) fetch('/api/ping', { cache: 'no-store', credentials: 'same-origin' }).catch(() => {});
  }, 4 * 60 * 1000);

  // 화면이 다시 켜지면 기다리지 않고 바로 다시 연결
  document.addEventListener('visibilitychange', () => {
    if (!stopped && document.visibilityState === 'visible' && ws && ws.readyState > 1) {
      clearTimeout(retryTimer);
      open();
    }
  });
  open();
  return {
    send(obj) { if (ws && ws.readyState === 1) { ws.send(JSON.stringify(obj)); return true; } return false; },
    stop() { stopped = true; clearInterval(keepAlive); clearInterval(pingTimer); clearTimeout(retryTimer); if (ws) ws.close(); },
  };
}

/**
 * 글자 안의 $...$ 부분을 수식으로 그려서 target 에 넣는다. (\$ 는 그냥 $ 글자)
 * 수식이 아닌 부분은 항상 텍스트로만 넣는다.
 */
function renderMath(target, text) {
  target.replaceChildren();
  if (!text) return target;
  const parts = [];
  let buf = '';
  let math = null;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === '\\' && text[i + 1] === '$') {
      if (math !== null) math += '\\$'; else buf += '$';
      i++;
    } else if (c === '$') {
      if (math === null) { if (buf) parts.push({ t: buf }); buf = ''; math = ''; }
      else { parts.push({ m: math }); math = null; }
    } else if (math !== null) {
      math += c;
    } else {
      buf += c;
    }
  }
  if (math !== null) buf += '$' + math; // 닫히지 않은 $ 는 글자로
  if (buf) parts.push({ t: buf });
  for (const p of parts) {
    if (p.t !== undefined) {
      target.append(document.createTextNode(p.t));
    } else if (window.katex && p.m.trim()) {
      const span = document.createElement('span');
      try {
        window.katex.render(p.m, span, { throwOnError: false, trust: false, strict: 'ignore' });
      } catch (e) {
        span.textContent = '$' + p.m + '$';
      }
      target.append(span);
    } else {
      target.append(document.createTextNode('$' + p.m + '$'));
    }
  }
  return target;
}

/** 수식이 들어간 요소 만들기 */
function mathEl(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  return renderMath(e, text);
}
