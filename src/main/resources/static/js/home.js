'use strict';

(function () {
  async function init() {
    try {
      await api('GET', '/api/teacher/me');
      showRooms();
    } catch (e) {
      show($('login-view'), true);
    }
  }

  $('login-form').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    $('login-error').textContent = '';
    try {
      await api('POST', '/api/teacher/login', { password: $('login-password').value });
      $('login-password').value = '';
      showRooms();
    } catch (e) {
      $('login-error').textContent = e.message;
    }
  });

  $('logout-btn').addEventListener('click', async () => {
    try { await api('POST', '/api/teacher/logout'); } catch (e) { /* 무시 */ }
    location.reload();
  });

  async function showRooms() {
    show($('login-view'), false);
    show($('rooms-view'), true);
    show($('logout-btn'), true);
    await loadRooms();
  }

  async function loadRooms() {
    let rooms;
    try {
      rooms = await api('GET', '/api/teacher/rooms');
    } catch (e) {
      if (e.status === 401) location.reload();
      return;
    }
    const list = $('room-list');
    list.replaceChildren();
    if (rooms.length === 0) {
      list.append(el('li', { class: 'muted', text: '아직 만든 방이 없어요.' }));
      return;
    }
    rooms.forEach(r => {
      const url = shareUrl(r.id);
      const copyBtn = el('button', { type: 'button', text: '주소 복사', onclick: async () => {
        if (await copyText(url)) { copyBtn.textContent = '복사됨'; setTimeout(() => { copyBtn.textContent = '주소 복사'; }, 1500); }
      } });
      list.append(el('li', { class: 'room-item' },
        el('div', { class: 'room-info' },
          el('strong', { text: r.name }),
          el('span', { class: 'muted small', text: ' 학생 ' + r.studentCount + '명 · ' + new Date(r.createdAt).toLocaleString('ko-KR') }),
          el('div', { class: 'share-url', text: url })),
        el('div', { class: 'row' },
          el('a', { class: 'button primary', href: '/t/' + r.id, text: '수업 열기' }),
          copyBtn,
          el('button', { type: 'button', class: 'danger', text: '삭제', onclick: async () => {
            if (!confirm('"' + r.name + '" 방을 삭제할까요?\n접속한 학생은 모두 나가고 점수도 지워집니다.')) return;
            try { await api('DELETE', '/api/teacher/rooms/' + r.id); } catch (e) { alert(e.message); }
            loadRooms();
          } }))));
    });
  }

  $('create-form').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    $('create-error').textContent = '';
    try {
      const res = await api('POST', '/api/teacher/rooms', { name: $('room-name-input').value });
      location.href = '/t/' + res.id;
    } catch (e) {
      if (e.status === 401) { location.reload(); return; }
      $('create-error').textContent = e.message;
    }
  });

  init();
})();
