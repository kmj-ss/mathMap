'use strict';

(function () {
  const views = ['join-view', 'wait-view', 'question-view', 'ended-view'];
  const roomId = roomIdFromPath();
  let socket = null;
  let currentQuestionId = null;
  let selectedChoice = null;
  let submitting = false;

  function showView(id) {
    views.forEach(v => show($(v), v === id));
  }

  function showEnded(title, text, canRejoin) {
    if (socket) { socket.stop(); socket = null; }
    $('ended-title').textContent = title;
    $('ended-text').textContent = text;
    show($('rejoin'), canRejoin);
    show($('me'), false);
    show($('history-box'), false);
    showView('ended-view');
  }

  async function init() {
    if (!roomId) {
      showEnded('주소가 올바르지 않아요', '선생님께 받은 방 주소로 들어와 주세요.', false);
      return;
    }
    try {
      const info = await api('GET', '/api/rooms/' + roomId);
      setRoomName(info.roomName);
      if (info.me) startSession(info.me);
      else showView('join-view');
    } catch (e) {
      showEnded(e.status === 404 ? '없는 방이에요' : '접속하지 못했어요', e.message, false);
    }
  }

  function setRoomName(name) {
    $('room-name').textContent = name || '';
    document.title = (name ? name + ' · ' : '') + 'mathMap';
  }

  $('join-form').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    $('join-error').textContent = '';
    try {
      const res = await api('POST', '/api/rooms/' + roomId + '/join', {
        classNo: $('join-no').value,
        name: $('join-name').value,
      });
      setRoomName(res.roomName);
      startSession(res.me);
    } catch (e) {
      $('join-error').textContent = e.message;
    }
  });

  $('rejoin').addEventListener('click', () => {
    showView('join-view');
  });

  function startSession(me) {
    $('me').textContent = me.classNo + '번 ' + me.name;
    show($('me'), true);
    showView('wait-view');
    socket = connectSocket('/ws/student?room=' + roomId, onMessage, async () => {
      // 계속 연결이 안 되면 방이 아직 있는지, 입장 정보가 남아 있는지 확인
      try {
        const info = await api('GET', '/api/rooms/' + roomId);
        if (!info.me) showEnded('입장 정보가 없어요', '다시 입장해 주세요.', true);
      } catch (e) {
        if (e.status === 404) showEnded('수업이 끝났어요', '선생님이 방을 닫았어요.', false);
      }
    });
  }

  function onMessage(msg) {
    switch (msg.type) {
      case 'state': renderState(msg); break;
      case 'kicked': showEnded('퇴장되었어요', '선생님이 이 수업에서 내보냈어요.', false); break;
      case 'reset': showEnded('수업이 새로 시작됐어요', '다시 입장해 주세요.', true); break;
      case 'closed': showEnded('수업이 끝났어요', '선생님이 방을 닫았어요.', false); break;
      case 'replaced': showEnded('다른 창에서 접속했어요', '이 창은 연결이 끊겼어요.', false); break;
      case 'error': showResultError(msg.message); break;
    }
  }

  function renderState(s) {
    if (s.status === 'GONE') {
      showEnded('입장 정보가 없어요', '다시 입장해 주세요.', true);
      return;
    }
    $('me').textContent = s.me.classNo + '번 ' + s.me.name + ' · ' + s.me.score + '점';
    renderHistory(s.history || []);
    if (s.status === 'WAITING' || !s.question) {
      currentQuestionId = null;
      showView('wait-view');
      return;
    }
    showView('question-view');
    const q = s.question;
    const isNew = q.id !== currentQuestionId;
    currentQuestionId = q.id;
    submitting = false;

    $('q-number').textContent = q.number + '번 문제';
    $('q-points').textContent = q.points + '점';
    renderMath($('q-text'), q.text || '');
    show($('q-text'), !!q.text);
    if (q.imageId) {
      $('q-image').src = '/images/' + encodeURIComponent(q.imageId);
      show($('q-image'), true);
    } else {
      $('q-image').removeAttribute('src');
      show($('q-image'), false);
    }

    const answered = !!s.myResult;
    const closed = s.status === 'CLOSED';
    const locked = answered || closed;

    if (isNew) {
      selectedChoice = null;
      $('answer-input').value = '';
      buildChoices(q);
    }
    const multiple = q.kind === 'MULTIPLE';
    show($('choices'), multiple);
    show($('subjective'), !multiple);
    $('answer-input').disabled = locked;
    $('choices').querySelectorAll('button').forEach(b => { b.disabled = locked; });
    show($('answer-submit'), !locked);

    if (answered) {
      const r = s.myResult;
      $('result').textContent = r.message;
      $('result').className = 'result ' + (r.correct ? 'ok' : 'bad');
      show($('result'), true);
      if (multiple) markChoice(Number(r.answer));
      else $('answer-input').value = r.answer;
    } else if (closed) {
      $('result').textContent = '문제가 마감됐어요.';
      $('result').className = 'result';
      show($('result'), true);
    } else {
      show($('result'), false);
    }

    if (closed && s.correctAnswer) {
      renderMath($('reveal'), '정답: ' + s.correctAnswer);
      show($('reveal'), true);
    } else {
      show($('reveal'), false);
    }
    show($('next-hint'), locked);
  }

  function buildChoices(q) {
    const box = $('choices');
    box.replaceChildren();
    (q.choices || []).forEach((c, i) => {
      box.append(el('button', {
        type: 'button', class: 'choice', 'data-index': String(i),
        onclick: () => markChoice(i),
      }, el('span', { class: 'choice-no', text: String(i + 1) }), mathEl('span', 'choice-label', c)));
    });
  }

  // ───────── 지난 문제 ─────────
  let historyOpen = false;
  let historyCount = 0;

  $('history-btn').addEventListener('click', () => {
    historyOpen = !historyOpen;
    show($('history-list'), historyOpen);
    updateHistoryButton();
    if (historyOpen) $('history-list').scrollIntoView({ behavior: 'smooth', block: 'start' });
  });

  function updateHistoryButton() {
    $('history-btn').textContent = historyOpen ? '지난 문제 닫기' : '지난 문제 보기 (' + historyCount + ')';
  }

  function renderHistory(items) {
    historyCount = items.length;
    show($('history-box'), items.length > 0);
    updateHistoryButton();
    const list = $('history-list');
    list.replaceChildren();
    items.forEach(h => {
      const mark = !h.answered ? el('span', { class: 'h-mark none', text: '미제출' })
        : el('span', { class: 'h-mark ' + (h.correct ? 'ok' : 'bad'), text: h.correct ? 'O 정답 +' + h.points + '점' : 'X 오답' });
      const card = el('article', { class: 'history-item' },
        el('div', { class: 'q-head' }, el('span', { class: 'badge', text: h.number + '번 문제' }), mark));
      if (h.text) card.append(mathEl('p', 'q-text small-q', h.text));
      if (h.imageId) card.append(el('img', { class: 'q-image', src: '/images/' + encodeURIComponent(h.imageId), alt: '문제 이미지', loading: 'lazy' }));
      if (h.kind === 'MULTIPLE') {
        const ol = el('ol', { class: 'choice-preview' });
        h.choices.forEach(c => ol.append(mathEl('li', '', c)));
        card.append(ol);
      }
      card.append(
        mathEl('p', 'h-line', '내 답: ' + (h.answered ? h.myAnswer : '(제출 안 함)')),
        mathEl('p', 'h-line strong', '정답: ' + h.correctAnswer));
      list.append(card);
    });
  }

  function markChoice(i) {
    selectedChoice = i;
    $('choices').querySelectorAll('.choice').forEach(b => {
      b.classList.toggle('selected', Number(b.dataset.index) === i);
    });
  }

  function showResultError(message) {
    submitting = false;
    $('result').textContent = message;
    $('result').className = 'result bad';
    show($('result'), true);
  }

  $('answer-form').addEventListener('submit', (ev) => {
    ev.preventDefault();
    if (submitting || currentQuestionId == null) return;
    const multiple = !$('choices').classList.contains('hidden');
    let answer;
    if (multiple) {
      if (selectedChoice == null) { showResultError('보기를 선택해 주세요.'); return; }
      answer = String(selectedChoice);
    } else {
      answer = $('answer-input').value.trim();
      if (!answer) { showResultError('답을 입력해 주세요.'); return; }
    }
    submitting = true;
    if (!socket || !socket.send({ type: 'answer', questionId: currentQuestionId, answer })) {
      showResultError('연결이 잠시 끊겼어요. 다시 제출해 주세요.');
    }
  });

  init();
})();
