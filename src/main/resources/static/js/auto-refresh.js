// 강사 시간표를 1분마다 새로 불러와 다른 분이 적은 예약·메모·수업 기록을 보여 줌
// 입력 중(칸에 커서가 있거나 고치다 만 글이 있음)이거나 예약 창이 열려 있으면 미루고 다음 차례에 다시 확인
// 오류 안내가 떠 있으면 읽을 시간을 두려고 3분 뒤부터 새로 불러옴
(function () {
	var INTERVAL = 60 * 1000;
	var loadedAt = Date.now();
	var wait = document.querySelector('.flash.error') ? 3 * INTERVAL : INTERVAL;

	function busy() {
		var active = document.activeElement;
		if (active && active.matches('input, select, textarea')) return true;
		if (document.querySelector('dialog[open]')) return true;
		return Array.from(document.querySelectorAll('input[type=text]'))
			.some(function (input) { return input.value !== input.defaultValue; });
	}

	function tick() {
		if (document.hidden || Date.now() - loadedAt < wait || busy()) return;
		location.reload();
	}

	setInterval(tick, 10 * 1000);
	// 다른 탭에 있다가 돌아오면 바로 확인
	document.addEventListener('visibilitychange', tick);
})();
