// 시간표의 예약 칸을 누르면 예약 수강생 화면을 큰 창(dialog)으로 띄움
// 칸은 원래 그 화면으로 가는 링크라, 스크립트가 없으면 한 화면으로 열림. 저장은 보통 폼 제출(저장 후 시간표로 돌아옴)
(function () {
	var dialog = document.getElementById('reserve-modal');
	if (!dialog) return;

	document.querySelectorAll('a.reserve-input').forEach(function (link) {
		link.addEventListener('click', function (e) {
			e.preventDefault();
			// 내용을 못 받아 왔을 때만 한 화면으로 엶. 받아 온 뒤의 오류는 콘솔에 남기고 창은 그대로 띄움
			fetch(link.href + '&modal=true')
				.then(function (res) {
					if (!res.ok) throw new Error(res.status);
					return res.text();
				})
				.then(function (html) {
					dialog.innerHTML = html;
					dialog.showModal();
					if (typeof initPicker === 'function') {
						dialog.querySelectorAll('.picker').forEach(initPicker);
					}
					var filter = dialog.querySelector('.picker-filter');
					if (filter) filter.focus();
				}, function () { location.href = link.href; });
		});
	});

	// [취소]는 창만 닫음
	dialog.addEventListener('click', function (e) {
		if (e.target.closest('[data-modal-close]') || e.target === dialog) {
			e.preventDefault();
			dialog.close();
		}
	});
})();
