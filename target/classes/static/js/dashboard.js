/* ── Dashboard ─────────────────────────────────────────────── */

// 일별 Bar Chart (Canvas 기반 순수 JS)
(function buildChart() {
    const canvas = document.getElementById('dailyChart');
    if (!canvas || typeof dailyStats === 'undefined') return;

    const ctx = canvas.getContext('2d');
    const W = canvas.offsetWidth || 400;
    const H = 80;
    canvas.width  = W;
    canvas.height = H;

    const data    = dailyStats;
    const maxVal  = Math.max(...data.map(d => d.successCount + d.failedCount), 1);
    const barW    = Math.floor((W - 40) / data.length);
    const gap     = 4;
    const singleW = Math.floor((barW - gap) / 2);
    const baseY   = H - 20;

    const style   = getComputedStyle(document.body);
    const cSuccess= '#16a34a';
    const cFail   = '#dc2626';
    const cText   = '#6b6960';

    data.forEach((d, i) => {
        const x = 20 + i * barW;
        const sh = Math.round((d.successCount / maxVal) * (H - 28));
        const fh = Math.round((d.failedCount  / maxVal) * (H - 28));

        // 성공 바
        ctx.fillStyle = cSuccess + 'aa';
        ctx.beginPath();
        ctx.roundRect(x, baseY - sh, singleW, sh, [3, 3, 0, 0]);
        ctx.fill();

        // 실패 바
        ctx.fillStyle = cFail + 'aa';
        ctx.beginPath();
        ctx.roundRect(x + singleW + gap, baseY - Math.max(fh, d.failedCount > 0 ? 3 : 0),
                      singleW, Math.max(fh, d.failedCount > 0 ? 3 : 0), [3, 3, 0, 0]);
        ctx.fill();

        // 날짜 레이블
        ctx.fillStyle = cText;
        ctx.font = '10px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText(d.dayLabel || '', x + singleW, H - 4);
    });
})();

// 재전송
function retryJob(btn) {
    const id    = btn.getAttribute('data-id');
    const label = btn.getAttribute('data-label');
    if (!confirm('"' + label + '" 건을 재전송하시겠습니까?')) return;

    btn.disabled = true;
    btn.innerHTML = '<i class="ti ti-loader"></i>전송 중...';

    apiFetch('/dashboard/retry/' + id, 'POST').then(r => {
        if (r.success) {
            showToast(label + ' 재전송 완료');
            btn.closest('tr').querySelector('td:nth-child(5)').innerHTML =
                '<span class="badge badge-success"><i class="ti ti-check"></i>성공</span>';
            btn.closest('td').innerHTML = '<span class="text-tertiary text-sm">-</span>';
            // 실패 카운트 감소
            const fc = document.getElementById('fail-count');
            if (fc) fc.textContent = Math.max(0, parseInt(fc.textContent) - 1);
        } else {
            showToast(r.message, true);
            btn.disabled = false;
            btn.innerHTML = '<i class="ti ti-send"></i>재전송';
        }
    });
}
