// [카톡 복사]: data-copy에 적힌 id의 숨은 textarea 글을 클립보드에 복사
// 내부망 http://IP:3000은 브라우저가 안전한 주소로 보지 않아 navigator.clipboard를 쓸 수 없음
// → 화면 밖에 잠깐 글상자를 만들어 선택한 뒤 execCommand('copy')로 복사 (http에서도 동작)
document.addEventListener('click', e => {
	const btn = e.target.closest('[data-copy]');
	if (!btn) return;
	const text = document.getElementById(btn.dataset.copy).value;
	const box = document.createElement('textarea');
	box.value = text;
	box.setAttribute('readonly', '');
	box.style.position = 'fixed';
	box.style.left = '-9999px';
	document.body.appendChild(box);
	box.select();
	let ok = false;
	try {
		ok = document.execCommand('copy');
	} catch (err) {
		ok = false;
	}
	box.remove();
	if (!ok) {
		alert('복사하지 못했습니다. 다른 브라우저(크롬·엣지)에서 다시 해 주세요.');
		return;
	}
	const label = btn.textContent;
	btn.textContent = '복사됨 ✓';
	setTimeout(() => { btn.textContent = label; }, 1500);
});
