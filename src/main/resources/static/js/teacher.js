'use strict';

(function () {
  const roomId = roomIdFromPath();
  const base = '/api/teacher/rooms/' + roomId;
  let socket = null;
  let state = null;
  let editingId = null;
  let uploadedImageId = null;

  // ───────── 시작 ─────────
  async function init() {
    if (!roomId) { location.href = '/'; return; }
    try {
      await api('GET', '/api/teacher/me');
    } catch (e) {
      location.href = '/'; // 로그인은 첫 화면에서
      return;
    }
    startApp();
  }

  function showError(title) {
    if (socket) { socket.stop(); socket = null; }
    show($('app'), false);
    show($('top-actions'), false);
    show($('share-bar'), false);
    $('error-title').textContent = title;
    show($('error-view'), true);
  }

  function startApp() {
    show($('app'), true);
    show($('top-actions'), true);
    show($('share-bar'), true);
    $('share-url').textContent = shareUrl(roomId);
    resetEditor();
    socket = connectSocket('/ws/teacher?room=' + roomId, (msg) => {
      if (msg.type === 'dashboard') render(msg);
      else if (msg.type === 'closed') showError('삭제된 방이에요');
    }, async () => {
      try {
        const rooms = await api('GET', '/api/teacher/rooms');
        if (!rooms.some(r => r.id === roomId)) showError('없는 방이에요');
      } catch (e) {
        if (e.status === 401) location.href = '/';
      }
    });
  }

  $('copy-btn').addEventListener('click', async () => {
    if (await copyText(shareUrl(roomId))) {
      $('copy-btn').textContent = '복사됨';
      setTimeout(() => { $('copy-btn').textContent = '주소 복사'; }, 1500);
    }
  });

  async function act(fn) {
    try {
      await fn();
    } catch (e) {
      if (e.status === 401) { location.href = '/'; return; }
      alert(e.message);
    }
  }

  // ───────── 상단 ─────────
  $('reset-btn').addEventListener('click', () => {
    if (!confirm('새 수업을 시작할까요?\n접속한 학생과 점수가 모두 지워지고, 문제는 다시 출제 전 상태가 됩니다.\n(필요하면 먼저 엑셀을 다운로드하세요)')) return;
    act(() => api('POST', base + '/reset'));
  });

  $('export-btn').addEventListener('click', () => {
    // 같은 사이트 세션 쿠키로 받는 일반 다운로드 (GET 이라 상태를 바꾸지 않음)
    window.location.href = base + '/export';
  });

  $('next-btn').addEventListener('click', () => act(() => api('POST', base + '/next', {})));
  $('close-btn').addEventListener('click', () => act(() => api('POST', base + '/close')));

  // ───────── 우측 패널 접기 ─────────
  const SIDE_KEY = 'mathmap.sideCollapsed';
  function setSide(collapsed) {
    $('side').classList.toggle('collapsed', collapsed);
    $('app').classList.toggle('side-collapsed', collapsed);
    $('side-toggle').textContent = collapsed ? '◀' : '▶';
    $('side-toggle').setAttribute('aria-expanded', String(!collapsed));
    try { localStorage.setItem(SIDE_KEY, collapsed ? '1' : '0'); } catch (e) { /* 저장 불가 */ }
  }
  $('side-toggle').addEventListener('click', () => setSide(!$('side').classList.contains('collapsed')));
  try { setSide(localStorage.getItem(SIDE_KEY) === '1'); } catch (e) { setSide(false); }

  // ───────── 화면 그리기 ─────────
  function render(s) {
    state = s;
    $('room-name').textContent = s.roomName;
    document.title = s.roomName + ' · mathMap 선생님';
    renderCurrent(s);
    renderQuestions(s);
    renderStudents(s);
  }

  function renderCurrent(s) {
    const box = $('current-box');
    box.replaceChildren();
    const cur = s.questions.find(q => q.id === s.currentId);
    $('close-btn').disabled = !(cur && cur.status === 'OPEN');
    const pending = s.questions.filter(q => q.status === 'PENDING').length;
    $('next-btn').textContent = pending > 0 ? '다음 문제 출제 (남은 ' + pending + '개)' : (cur ? '종료하고 대기' : '다음 문제 출제');
    $('next-btn').disabled = !cur && pending === 0;
    if (s.students.length === 0) {
      box.append(el('p', { class: 'warn', text: '위의 학생 공유 주소를 학생들에게 보내 주세요. 그 주소로만 이 방에 들어올 수 있어요.' }));
    }
    if (!cur) {
      box.append(el('p', { class: 'muted', text: pending > 0 ? '대기 중입니다. "다음 문제 출제"를 누르면 학생에게 문제가 나타납니다.' : '대기 중입니다. 아래에서 문제를 등록하세요.' }));
      return;
    }
    box.append(
      el('div', { class: 'row' },
        el('span', { class: 'badge', text: cur.number + '번' }),
        el('span', { class: 'status ' + cur.status, text: cur.status === 'OPEN' ? '진행 중' : '마감 (정답 공개됨)' }),
        el('span', { class: 'muted', text: '제출 ' + s.answeredCount + ' / ' + s.students.length + '명 · 정답 ' + s.correctCount + '명' })),
      questionSummary(cur));
  }

  function questionSummary(q) {
    const wrap = el('div', { class: 'q-summary' });
    if (q.text) wrap.append(el('p', { class: 'q-text', text: q.text }));
    if (q.imageId) wrap.append(el('img', { class: 'q-thumb', src: '/images/' + q.imageId, alt: '문제 이미지' }));
    if (q.kind === 'MULTIPLE') {
      const ol = el('ol', { class: 'choice-preview' });
      q.choices.forEach((c, i) => ol.append(el('li', { class: i === q.correctChoice ? 'correct' : '', text: c })));
      wrap.append(ol);
    }
    wrap.append(el('p', { class: 'muted small', text: (q.kind === 'MULTIPLE' ? '객관식' : '주관식') + ' · 정답: ' + q.answerText + ' · ' + q.points + '점 · 오답 문구: "' + q.wrongMessage + '"' }));
    return wrap;
  }

  function renderQuestions(s) {
    const list = $('q-list');
    list.replaceChildren();
    if (s.questions.length === 0) {
      list.append(el('li', { class: 'muted', text: '등록된 문제가 없어요.' }));
      return;
    }
    s.questions.forEach(q => {
      const label = { PENDING: '출제 전', OPEN: '진행 중', CLOSED: '마감' }[q.status];
      const actions = el('div', { class: 'row' });
      if (q.status === 'PENDING') {
        actions.append(
          el('button', { type: 'button', text: '바로 출제', onclick: () => act(() => api('POST', base + '/next', { questionId: q.id })) }),
          el('button', { type: 'button', text: '수정', onclick: () => startEdit(q) }),
          el('button', { type: 'button', class: 'danger', text: '삭제', onclick: () => {
            if (confirm(q.number + '번 문제를 삭제할까요?')) act(() => api('DELETE', base + '/questions/' + q.id));
          } }));
      } else if (q.status === 'CLOSED') {
        actions.append(el('button', { type: 'button', class: 'danger', text: '삭제', onclick: () => {
          if (confirm(q.number + '번 문제를 삭제할까요?')) act(() => api('DELETE', base + '/questions/' + q.id));
        } }));
      }
      list.append(el('li', { class: 'q-item ' + q.status },
        el('div', { class: 'row between' },
          el('span', {}, el('strong', { text: q.number + '번 ' }), el('span', { class: 'status ' + q.status, text: label })),
          actions),
        questionSummary(q)));
    });
  }

  function renderStudents(s) {
    const online = s.students.filter(x => x.connected).length;
    $('online-count').textContent = online + ' / ' + s.students.length + '명';
    const cur = s.questions.find(q => q.id === s.currentId);
    $('answer-progress').textContent = cur ? cur.number + '번 문제 제출 ' + s.answeredCount + '명, 정답 ' + s.correctCount + '명' : '';
    const tbody = $('student-rows');
    tbody.replaceChildren();
    if (s.students.length === 0) {
      tbody.append(el('tr', {}, el('td', { colspan: '5', class: 'muted', text: '아직 입장한 학생이 없어요.' })));
    }
    s.students.forEach(st => {
      let now = '';
      if (cur) now = st.answered ? (st.correct ? 'O' : 'X') : '…';
      tbody.append(el('tr', { class: st.connected ? '' : 'offline' },
        el('td', {}, el('span', { class: 'dot ' + (st.connected ? 'on' : 'off'), title: st.connected ? '접속 중' : '연결 끊김' }), st.classNo),
        el('td', { text: st.name }),
        el('td', { class: 'num', text: String(st.score) }),
        el('td', { class: 'mark ' + (st.answered ? (st.correct ? 'ok' : 'bad') : ''), text: now }),
        el('td', {}, el('button', { type: 'button', class: 'small danger', text: '강퇴', onclick: () => {
          if (confirm(st.classNo + '번 ' + st.name + ' 학생을 내보낼까요?\n같은 번호와 이름으로 다시 들어올 수 없게 됩니다.')) {
            act(() => api('POST', base + '/students/' + encodeURIComponent(st.id) + '/kick'));
          }
        } }))));
    });
    const kl = $('kicked-list');
    kl.replaceChildren();
    s.kicked.forEach(key => {
      kl.append(el('li', {}, key.replace('|', '번 '), ' ',
        el('button', { type: 'button', class: 'small', text: '입장 허용', onclick: () => act(() => api('POST', base + '/unkick', { key })) })));
    });
    show($('kicked-box'), s.kicked.length > 0);
  }

  // ───────── 문제 편집 ─────────
  function radioValue(name) {
    return document.querySelector('input[name="' + name + '"]:checked').value;
  }
  function setRadio(name, value) {
    document.querySelector('input[name="' + name + '"][value="' + value + '"]').checked = true;
  }

  function syncEditor() {
    const image = radioValue('content') === 'image';
    show($('q-text-input'), !image);
    show($('image-box'), image);
    const multiple = radioValue('kind') === 'MULTIPLE';
    show($('subjective-box'), !multiple);
    show($('multiple-box'), multiple);
    if (multiple && $('choice-list').children.length === 0) {
      for (let i = 0; i < 4; i++) addChoice('');
    }
  }
  document.querySelectorAll('input[name="content"], input[name="kind"]').forEach(r => r.addEventListener('change', syncEditor));

  function addChoice(value, correct) {
    const list = $('choice-list');
    if (list.children.length >= 10) return;
    const row = el('div', { class: 'choice-edit' },
      el('input', { type: 'radio', name: 'correct', title: '정답' }),
      el('span', { class: 'choice-no' }),
      el('input', { type: 'text', class: 'choice-text', maxlength: '100' }),
      el('button', { type: 'button', class: 'small', text: '삭제', onclick: () => { row.remove(); renumber(); } }));
    row.querySelector('.choice-text').value = value;
    if (correct) row.querySelector('input[type=radio]').checked = true;
    list.append(row);
    renumber();
  }
  function renumber() {
    [...$('choice-list').children].forEach((r, i) => { r.querySelector('.choice-no').textContent = (i + 1) + '.'; });
  }
  $('add-choice').addEventListener('click', () => addChoice(''));

  $('q-image-input').addEventListener('change', async () => {
    const file = $('q-image-input').files[0];
    if (!file) return;
    $('q-error').textContent = '';
    const fd = new FormData();
    fd.append('file', file);
    try {
      const res = await api('POST', '/api/teacher/images', fd);
      uploadedImageId = res.imageId;
      $('q-image-preview').src = '/images/' + res.imageId;
      show($('q-image-preview'), true);
    } catch (e) {
      uploadedImageId = null;
      $('q-image-input').value = '';
      show($('q-image-preview'), false);
      $('q-error').textContent = e.message;
    }
  });

  function resetEditor() {
    editingId = null;
    uploadedImageId = null;
    $('q-form').reset();
    $('q-text-input').value = '';
    $('q-image-caption').value = '';
    $('q-answers').value = '';
    $('q-points').value = '3';
    $('q-wrong').value = '틀렸습니다';
    $('choice-list').replaceChildren();
    show($('q-image-preview'), false);
    $('q-error').textContent = '';
    $('editor-title').textContent = '문제 등록';
    $('q-save').textContent = '등록';
    show($('q-cancel'), false);
    syncEditor();
  }
  $('q-cancel').addEventListener('click', resetEditor);

  function startEdit(q) {
    resetEditor();
    editingId = q.id;
    setRadio('content', q.imageId ? 'image' : 'text');
    if (q.imageId) {
      uploadedImageId = q.imageId;
      $('q-image-preview').src = '/images/' + q.imageId;
      show($('q-image-preview'), true);
      $('q-image-caption').value = q.text || '';
    } else {
      $('q-text-input').value = q.text || '';
    }
    setRadio('kind', q.kind);
    $('choice-list').replaceChildren();
    if (q.kind === 'MULTIPLE') q.choices.forEach((c, i) => addChoice(c, i === q.correctChoice));
    else $('q-answers').value = q.answers.join('\n');
    $('q-points').value = String(q.points);
    $('q-wrong').value = q.wrongMessage;
    $('editor-title').textContent = q.number + '번 문제 수정';
    $('q-save').textContent = '수정 저장';
    show($('q-cancel'), true);
    syncEditor();
    $('q-form').scrollIntoView({ behavior: 'smooth' });
  }

  $('q-form').addEventListener('submit', (ev) => {
    ev.preventDefault();
    $('q-error').textContent = '';
    const image = radioValue('content') === 'image';
    const kind = radioValue('kind');
    if (image && !uploadedImageId) {
      $('q-error').textContent = '이미지를 선택해 주세요.';
      return;
    }
    const body = {
      text: image ? $('q-image-caption').value : $('q-text-input').value,
      imageId: image ? uploadedImageId : null,
      kind,
      points: Number($('q-points').value),
      wrongMessage: $('q-wrong').value,
    };
    if (kind === 'MULTIPLE') {
      const rows = [...$('choice-list').children];
      const filled = rows.filter(r => r.querySelector('.choice-text').value.trim() !== '');
      body.choices = filled.map(r => r.querySelector('.choice-text').value);
      const idx = filled.findIndex(r => r.querySelector('input[type=radio]').checked);
      body.correctChoice = idx >= 0 ? idx : null;
    } else {
      body.answers = $('q-answers').value.split('\n');
    }
    act(async () => {
      try {
        if (editingId) await api('PUT', base + '/questions/' + editingId, body);
        else await api('POST', base + '/questions', body);
        resetEditor();
      } catch (e) {
        if (e.status === 401) throw e;
        $('q-error').textContent = e.message;
      }
    });
  });

  init();
})();
