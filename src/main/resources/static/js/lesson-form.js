// 수업 기록 폼
(function () {
	var form = document.getElementById('lesson-form');
	var instructor = document.getElementById('instructor-select');
	var hopeBox = document.getElementById('hope-box');
	var past = form.dataset.past === 'true';

	// 날짜·교시를 바꾸면 그 시점 기준의 강사·강의실 목록으로 다시 엶 (입력한 값은 유지, 강의실은 다시 자동 배정)
	form.querySelectorAll('[data-reload-on-change]').forEach(function (el) {
		el.addEventListener('change', function () {
			var params = new URLSearchParams(new FormData(form));
			params.delete('roomId');
			location.href = form.dataset.reload + '?' + params.toString();
		});
	});

	// 희망 강사 알림: 수강생 1명 + 희망 강사가 있을 때만
	function updateHope() {
		hopeBox.hidden = true;
		hopeBox.textContent = '';
		var checked = form.querySelectorAll('.picker input:checked');
		if (past || checked.length !== 1 || !checked[0].dataset.hopes) return;

		var hopeIds = checked[0].dataset.hopes.split(',');
		// 희망 강사 중 지금 근무 중이고 수업 중이 아닌 강사
		var free = Array.from(instructor.options).filter(function (o) {
			return o.dataset.free === 'true' && hopeIds.indexOf(o.value) >= 0;
		});
		if (free.some(function (o) { return o.value === instructor.value; })) return; // 이미 희망 강사가 선택됨

		hopeBox.hidden = false;
		if (free.length === 0) {
			hopeBox.className = 'hope-box muted';
			hopeBox.textContent = '희망 강사(' + checked[0].dataset.hopeNames + ') 부재 / 수업 중';
			return;
		}
		hopeBox.className = 'hope-box ask';
		var names = free.map(function (o) { return o.dataset.name; });
		var question = document.createElement('span');
		question.textContent = names.join(' / ') + ' 강사님이 수업하나요?';
		hopeBox.appendChild(question);
		free.forEach(function (o, i) {
			var yes = button(free.length === 1 ? '예' : names[i], 'btn btn-primary');
			yes.addEventListener('click', function () {
				instructor.value = o.value;
				hopeBox.hidden = true;
			});
			hopeBox.appendChild(yes);
		});
		var no = button('아니오', 'btn');
		no.addEventListener('click', function () {
			hopeBox.hidden = true;
			instructor.focus();
		});
		hopeBox.appendChild(no);
	}

	function button(text, cls) {
		var b = document.createElement('button');
		b.type = 'button';
		b.className = cls;
		b.textContent = text;
		return b;
	}

	form.querySelector('.picker').addEventListener('change', updateHope);
	updateHope();
})();
