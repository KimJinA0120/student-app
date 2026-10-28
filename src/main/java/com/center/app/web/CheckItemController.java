package com.center.app.web;

import com.center.app.checkitem.CheckItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 오른쪽 사이드 패널의 확인 필요 목록. 처리 후 보던 화면(back)으로 돌아감
@Controller
@RequestMapping("/check-items")
@RequiredArgsConstructor
public class CheckItemController {

	private final CheckItemService service;

	@PostMapping
	public String add(@RequestParam(required = false) String content, @RequestParam(required = false) String personName,
			@RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/");
		return Flash.handle(ra, to, () -> {
			service.add(content, personName);
			return to;
		});
	}

	@PostMapping("/{id:\\d+}/resolve")
	public String resolve(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/");
		return Flash.handle(ra, to, () -> {
			service.resolve(id);
			return to;
		});
	}

	@PostMapping("/{id:\\d+}/reopen")
	public String reopen(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/");
		return Flash.handle(ra, to, () -> {
			service.reopen(id);
			return to;
		});
	}

	@PostMapping("/{id:\\d+}/delete")
	public String delete(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/");
		return Flash.handle(ra, to, () -> {
			service.delete(id);
			return to;
		});
	}
}
