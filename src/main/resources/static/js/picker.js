// 수강생 선택 목록: 이름으로 걸러 보기 + 선택한 이름 요약
document.querySelectorAll('.picker').forEach(function (picker) {
	var filter = picker.querySelector('.picker-filter');
	var summary = picker.querySelector('.picker-summary');
	var boxes = picker.querySelectorAll('input[type=checkbox]');

	function update() {
		var names = Array.from(boxes).filter(function (b) { return b.checked; })
			.map(function (b) { return b.dataset.name; });
		summary.textContent = names.length ? '선택 ' + names.length + '명: ' + names.join(', ') : '선택 없음';
	}

	filter.addEventListener('input', function () {
		var q = filter.value.trim();
		boxes.forEach(function (b) {
			b.closest('label').hidden = q !== '' && b.dataset.name.indexOf(q) < 0 && !b.checked;
		});
	});
	// 이름을 입력하고 Enter를 누르면 폼이 제출되지 않고, 하나만 남았을 때 그 수강생을 체크
	filter.addEventListener('keydown', function (e) {
		if (e.key !== 'Enter') return;
		e.preventDefault();
		var visible = Array.from(boxes).filter(function (b) { return !b.closest('label').hidden && !b.checked; });
		if (visible.length === 1) {
			visible[0].checked = true;
			visible[0].dispatchEvent(new Event('change', { bubbles: true }));
			filter.value = '';
			filter.dispatchEvent(new Event('input'));
		}
	});
	picker.addEventListener('change', update);
	update();
});
