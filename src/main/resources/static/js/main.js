/* ── 공통 유틸 ─────────────────────────────────────────────── */

// API fetch wrapper
function apiFetch(url, method, data) {
    return fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: data ? JSON.stringify(data) : undefined
    }).then(r => r.json());
}

// Toast 메시지
function showToast(msg, isError) {
    const t = document.getElementById('toast');
    const m = document.getElementById('toast-msg');
    if (!t || !m) return;
    m.textContent = msg;
    t.style.borderColor = isError ? 'var(--c-danger)' : 'var(--c-success)';
    t.style.color       = isError ? 'var(--c-danger)' : 'var(--c-success)';
    t.querySelector('i').className = isError ? 'ti ti-alert-circle' : 'ti ti-check';
    t.classList.add('show');
    setTimeout(() => t.classList.remove('show'), 3000);
}

// 모달 열기/닫기
function closeModal(e) {
    if (e.target.id === 'modal-overlay') closeModalDirect();
}
function closeModalDirect() {
    const el = document.getElementById('modal-overlay');
    if (el) el.style.display = 'none';
}

// 실시간 시계
function startClock() {
    const el = document.getElementById('live-clock');
    if (!el) return;
    function tick() {
        el.textContent = new Date().toLocaleTimeString('ko-KR', {
            hour: '2-digit', minute: '2-digit', second: '2-digit'
        });
    }
    tick();
    setInterval(tick, 1000);
}
startClock();
