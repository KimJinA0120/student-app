package com.center.app.web;

import static com.center.app.lesson.LessonTestData.MON;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.Set;

import com.center.app.common.FixedClockConfig;
import com.center.app.instructor.InstructorOverride;
import com.center.app.instructor.InstructorOverrideRepository;
import com.center.app.instructor.InstructorRole;
import com.center.app.instructor.OverrideType;
import com.center.app.lesson.Lesson;
import com.center.app.lesson.LessonRepository;
import com.center.app.lesson.LessonTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 강사 시간표 칸: 예약, 수업 기록 표시·버튼, 조퇴와 조퇴 취소
// 지금 = 11/2(월) 14:00, 2교시. 김강사(수업 중)·이강사 2교시 근무, 최강사 배치 없음
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@Transactional
class InstructorTimetableTest {

	static final String DAY = "/instructors?date=2026-11-02";

	@Autowired
	MockMvc mvc;

	@Autowired
	LessonTestData.Factory factory;

	@Autowired
	LessonRepository lessonRepository;

	@Autowired
	InstructorOverrideRepository overrideRepository;

	LessonTestData d;
	Lesson ongoing;

	@BeforeEach
	void setUp() {
		d = factory.create();
		ongoing = lessonRepository.save(new Lesson(MON, 2, d.rooms.get(0), d.kim, null, null, Set.of(d.park)));
	}

	@Test
	void 상단에_근무_중인_강사_패널이_없다() throws Exception {
		mvc.perform(get("/students"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("근무 중인 강사"))));
	}

	@Test
	void 칸에_수업_기록과_버튼이_보인다() throws Exception {
		mvc.perform(get(DAY))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("고정 2 · 조건부 0 · 결근 0")))
			// 수업 중인 칸: 강의실·수강생 + [추가] [종료] [취소]
			.andExpect(content().string(containsString("<b>203호</b>")))
			.andExpect(content().string(containsString("박영희")))
			.andExpect(content().string(containsString("/lessons/" + ongoing.getId() + "/students?back=")))
			.andExpect(content().string(containsString("/lessons/" + ongoing.getId() + "/end")))
			// 빈 칸: [수업시작] → 강사·날짜·교시가 채워진 폼
			.andExpect(content().string(containsString(
				"/lessons/new?instructorId=" + d.lee.getId() + "&amp;date=2026-11-02&amp;period=2")))
			// 수업 중인 김강사 칸에는 [수업시작]이 없음
			.andExpect(content().string(not(containsString("/lessons/new?instructorId=" + d.kim.getId()))));
	}

	@Test
	void 중간에_종료하면_다시_수업시작이_보이고_근무_없는_칸은_막힌다() throws Exception {
		ongoing.end(LocalTime.of(13, 50));
		mvc.perform(get(DAY))
			.andExpect(content().string(containsString("13:00~13:50")))
			.andExpect(content().string(not(containsString("/lessons/" + ongoing.getId() + "/end"))))
			.andExpect(content().string(containsString("/lessons/new?instructorId=" + d.kim.getId()
				+ "&amp;date=2026-11-02&amp;period=2")))
			// 김강사는 1·3·4교시 근무가 없어 그 칸엔 [수업시작]이 없음
			.andExpect(content().string(not(containsString("instructorId=" + d.kim.getId() + "&amp;date=2026-11-02&amp;period=1"))));
	}

	@Test
	void 조퇴하면_취소_버튼으로_바뀌고_취소하면_되돌아간다() throws Exception {
		String base = "/instructors/" + d.lee.getId() + "/leave-early";
		mvc.perform(get(DAY))
			.andExpect(content().string(containsString("action=\"" + base + "\"")));

		mvc.perform(post(base).param("date", "2026-11-02").param("period", "2").param("back", DAY))
			.andExpect(redirectedUrl(DAY))
			.andExpect(flash().attribute("message", containsString("조퇴 처리했습니다")));
		mvc.perform(get(DAY))
			.andExpect(content().string(not(containsString("action=\"" + base + "\""))))
			.andExpect(content().string(containsString("action=\"" + base + "/cancel\"")))
			.andExpect(content().string(containsString("class=\"leave-state\">조퇴<")));
		mvc.perform(post(base).param("date", "2026-11-02").param("period", "2"))
			.andExpect(flash().attribute("error", containsString("이미 조퇴 처리되었습니다")));

		mvc.perform(post(base + "/cancel").param("date", "2026-11-02").param("back", DAY))
			.andExpect(flash().attribute("message", "조퇴를 취소했습니다."));
		mvc.perform(get(DAY))
			.andExpect(content().string(containsString("action=\"" + base + "\"")));
		mvc.perform(post(base + "/cancel").param("date", "2026-11-02"))
			.andExpect(flash().attribute("error", "조퇴 기록이 없습니다."));
	}

	@Test
	void 지난_날짜에는_조퇴_버튼이_없다() throws Exception {
		mvc.perform(get("/instructors?date=2026-10-26"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("/leave-early\""))));
	}

	@Test
	void 메모_예약은_근무_칸에_적고_비우면_지운다() throws Exception {
		mvc.perform(post("/reservations").param("instructorId", d.lee.getId().toString())
				.param("date", "2026-11-09").param("period", "2").param("content", " 김철수, 2명 ").param("back", "/instructors?date=2026-11-09"))
			.andExpect(redirectedUrl("/instructors?date=2026-11-09"))
			.andExpect(flash().attribute("message", "메모를 저장했습니다."));
		mvc.perform(get("/instructors?date=2026-11-09"))
			.andExpect(content().string(containsString("value=\"김철수, 2명\"")));

		// 같은 칸에 다시 저장하면 덮어씀, 비우면 삭제
		mvc.perform(post("/reservations").param("instructorId", d.lee.getId().toString())
			.param("date", "2026-11-09").param("period", "2").param("content", "3명"));
		mvc.perform(get("/instructors?date=2026-11-09"))
			.andExpect(content().string(containsString("value=\"3명\"")))
			.andExpect(content().string(not(containsString("김철수"))));
		mvc.perform(post("/reservations").param("instructorId", d.lee.getId().toString())
				.param("date", "2026-11-09").param("period", "2").param("content", ""))
			.andExpect(flash().attribute("message", "메모를 지웠습니다."));
		mvc.perform(get("/instructors?date=2026-11-09"))
			.andExpect(content().string(not(containsString("value=\"3명\""))));
	}

	@Test
	void 강사는_주강사_먼저_그_안에서_근무_교시가_많은_순으로_보인다() throws Exception {
		// 11/9(월): 김강사(주) 2교시, 이강사(보조) 2교시 + 추가 3·4교시, 최강사(보조) 추가 2·3교시
		// → 주강사인 김강사가 맨 위(교시가 적어도), 보조끼리는 교시 많은 이강사 → 최강사
		overrideRepository.save(new InstructorOverride(d.lee, OverrideType.EXTRA, MON.plusDays(7), MON.plusDays(7),
			Set.of(3, 4), InstructorRole.ASSIST, null));
		overrideRepository.save(new InstructorOverride(d.choi, OverrideType.EXTRA, MON.plusDays(7), MON.plusDays(7),
			Set.of(2, 3), InstructorRole.ASSIST, null));
		mvc.perform(get("/instructors?date=2026-11-09"))
			.andExpect(content().string(stringContainsInOrder("<b>김강사</b>", "<b>이강사</b>", "<b>최강사</b>")));

		// 최강사가 4교시까지 하면 교시 수로 이강사를 앞지름 (이름순이면 이강사가 먼저)
		overrideRepository.save(new InstructorOverride(d.choi, OverrideType.EXTRA, MON.plusDays(7), MON.plusDays(7),
			Set.of(1, 4), InstructorRole.ASSIST, null));
		mvc.perform(get("/instructors?date=2026-11-09"))
			.andExpect(content().string(stringContainsInOrder("<b>김강사</b>", "<b>최강사</b>", "<b>이강사</b>")));
	}

	@Test
	void 화면을_연_뒤_다른_사람이_고친_메모는_덮어쓰지_않는다() throws Exception {
		String lee = d.lee.getId().toString();
		String next = "/instructors?date=2026-11-09";
		// 화면에 보였던 글이 hidden으로 함께 나감
		mvc.perform(post("/reservations").param("instructorId", lee)
			.param("date", "2026-11-09").param("period", "2").param("content", "김철수").param("original", ""));
		mvc.perform(get(next))
			.andExpect(content().string(containsString("name=\"original\" value=\"김철수\"")));

		// 빈칸을 보고 있던 사람이 저장 → 막고 안내, 글은 그대로
		mvc.perform(post("/reservations").param("instructorId", lee).param("date", "2026-11-09")
				.param("period", "2").param("content", "이영희").param("original", "").param("back", next))
			.andExpect(redirectedUrl(next))
			.andExpect(flash().attribute("error",
				"다른 분이 먼저 메모를 '김철수'(으)로 바꿨습니다. 입력하신 '이영희'는 저장하지 않았으니 확인 후 다시 해 주세요."));
		mvc.perform(get(next))
			.andExpect(content().string(containsString("value=\"김철수\"")))
			.andExpect(content().string(not(containsString("이영희"))));

		// 지우려 했는데 그 사이 바뀜 → 막음
		mvc.perform(post("/reservations").param("instructorId", lee).param("date", "2026-11-09")
				.param("period", "2").param("content", "").param("original", "박민수"))
			.andExpect(flash().attribute("error",
				"다른 분이 먼저 메모를 '김철수'(으)로 바꿨습니다. 지우지 않았으니 확인 후 다시 해 주세요."));

		// 최신 글을 보고 고치면 저장
		mvc.perform(post("/reservations").param("instructorId", lee).param("date", "2026-11-09")
				.param("period", "2").param("content", "김철수, 이영희").param("original", "김철수"))
			.andExpect(flash().attribute("message", "메모를 저장했습니다."));
		mvc.perform(get(next))
			.andExpect(content().string(containsString("value=\"김철수, 이영희\"")));
	}

	@Test
	void 예약에_등록_수강생을_고르면_수업시작_폼에_미리_체크된다() throws Exception {
		String next = "/instructors?date=2026-11-09";
		String lee = d.lee.getId().toString();
		mvc.perform(post("/reservations").param("instructorId", lee)
			.param("date", "2026-11-09").param("period", "2").param("content", "2명(첫 방문)"));

		// [수강생] 화면: 그 칸의 예약 글이 보이고 수강생 체크 목록
		mvc.perform(get("/reservations/students").param("instructorId", lee)
				.param("date", "2026-11-09").param("period", "2").param("back", next))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("메모: 2명(첫 방문)")))
			.andExpect(content().string(containsString("name=\"studentIds\"")));

		mvc.perform(post("/reservations/students").param("instructorId", lee).param("date", "2026-11-09")
				.param("period", "2").param("studentIds", d.park.getId().toString(), d.hong.getId().toString())
				.param("back", next))
			.andExpect(redirectedUrl(next))
			.andExpect(flash().attribute("message", "예약 수강생을 저장했습니다."));

		// 칸: 글은 그대로, 수강생 이름 + [수업시작] 주소에 수강생 id
		mvc.perform(get(next))
			.andExpect(content().string(containsString("value=\"2명(첫 방문)\"")))
			.andExpect(content().string(containsString("예약: 박영희, 홍길동")))
			.andExpect(content().string(containsString("studentIds=" + d.park.getId())))
			.andExpect(content().string(containsString("studentIds=" + d.hong.getId())));

		// 글을 비워도 수강생은 남음
		mvc.perform(post("/reservations").param("instructorId", lee)
			.param("date", "2026-11-09").param("period", "2").param("content", ""));
		mvc.perform(get(next))
			.andExpect(content().string(not(containsString("2명(첫 방문)"))))
			.andExpect(content().string(containsString("예약: 박영희, 홍길동")));

		// 수강생도 비우면 예약이 사라짐
		mvc.perform(post("/reservations/students").param("instructorId", lee).param("date", "2026-11-09")
			.param("period", "2"));
		mvc.perform(get(next))
			.andExpect(content().string(not(containsString("예약: 박영희"))))
			.andExpect(content().string(not(containsString("studentIds="))));
	}

	@Test
	void 근무가_빠진_칸의_삭제는_글과_수강생을_모두_지운다() throws Exception {
		String next = "/instructors?date=2026-11-09";
		mvc.perform(post("/reservations/students").param("instructorId", d.lee.getId().toString())
			.param("date", "2026-11-09").param("period", "2").param("studentIds", d.park.getId().toString()));
		overrideRepository.save(new InstructorOverride(d.lee, OverrideType.ABSENT, MON.plusDays(7), MON.plusDays(7),
			Set.of(), null, null));
		mvc.perform(get(next))
			.andExpect(content().string(containsString("예약: 박영희")))
			.andExpect(content().string(containsString("/reservations/delete")));

		mvc.perform(post("/reservations/delete").param("instructorId", d.lee.getId().toString())
				.param("date", "2026-11-09").param("period", "2"))
			.andExpect(flash().attribute("message", "예약을 지웠습니다."));
		mvc.perform(get(next))
			.andExpect(content().string(not(containsString("예약: 박영희"))));
	}

	@Test
	void 예약에_고른_수강생도_삭제할_수_있다() throws Exception {
		mvc.perform(post("/reservations/students").param("instructorId", d.lee.getId().toString())
			.param("date", "2026-11-09").param("period", "2").param("studentIds", d.hong.getId().toString()));
		mvc.perform(post("/students/" + d.hong.getId() + "/delete"))
			.andExpect(flash().attribute("message", containsString("삭제")));
		mvc.perform(get("/instructors?date=2026-11-09"))
			.andExpect(content().string(not(containsString("홍길동"))));
	}

	@Test
	void 예약_칸은_큰_창으로_띄울_조각을_따로_준다() throws Exception {
		// 시간표: 예약 칸(링크) + 창 자리, 메모 입력칸은 placeholder "메모"
		mvc.perform(get("/instructors?date=2026-11-09"))
			.andExpect(content().string(containsString("class=\"reserve-input\"")))
			.andExpect(content().string(containsString("id=\"reserve-modal\"")))
			.andExpect(content().string(containsString("placeholder=\"메모\"")));
		// modal=true면 레이아웃 없이 창 안쪽만
		mvc.perform(get("/reservations/students").param("instructorId", d.lee.getId().toString())
				.param("date", "2026-11-09").param("period", "2").param("modal", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("class=\"reserve-panel\"")))
			.andExpect(content().string(not(containsString("<html"))));
	}

	@Test
	void 미배정_예약도_수강생을_고르고_배정하면_강사_칸으로_합쳐진다() throws Exception {
		String next = "/instructors?date=2026-11-09";
		mvc.perform(get("/reservations/students").param("date", "2026-11-09").param("period", "2"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<b>미배정</b>")));
		mvc.perform(post("/reservations/students").param("date", "2026-11-09").param("period", "2")
				.param("studentIds", d.hong.getId().toString()))
			.andExpect(flash().attribute("message", "예약 수강생을 저장했습니다."));
		mvc.perform(post("/reservations").param("date", "2026-11-09").param("period", "2").param("content", "1명(첫 방문)"));
		mvc.perform(get(next))
			.andExpect(content().string(containsString("예약: 홍길동")))
			.andExpect(content().string(containsString("/reservations/assign")));

		// 이강사 칸에 예약 수강생이 이미 있으면 수강생은 합치고 메모는 그대로 옮김
		mvc.perform(post("/reservations/students").param("instructorId", d.lee.getId().toString())
			.param("date", "2026-11-09").param("period", "2").param("studentIds", d.park.getId().toString()));
		mvc.perform(post("/reservations/assign").param("date", "2026-11-09").param("period", "2")
			.param("instructorId", d.lee.getId().toString()));
		mvc.perform(get(next))
			.andExpect(content().string(containsString("예약: 박영희, 홍길동")))
			.andExpect(content().string(containsString("value=\"1명(첫 방문)\"")))
			.andExpect(content().string(containsString("예약 (강사 미정)")));
	}

	@Test
	void 근무_없는_칸에는_예약할_수_없다() throws Exception {
		mvc.perform(post("/reservations").param("instructorId", d.choi.getId().toString())
				.param("date", "2026-11-09").param("period", "2").param("content", "김철수"))
			.andExpect(flash().attribute("error", containsString("근무하지 않아 예약할 수 없습니다")));
	}

	@Test
	void 수업_기록이_생기면_예약을_숨기고_근무가_빠지면_확인_필요로_남긴다() throws Exception {
		// 김강사 칸은 이미 수업 중 → 예약을 넣어도 보이지 않음
		mvc.perform(post("/reservations").param("instructorId", d.kim.getId().toString())
			.param("date", "2026-11-02").param("period", "2").param("content", "숨는예약"));
		mvc.perform(get(DAY))
			.andExpect(content().string(not(containsString("숨는예약"))));

		// 다음 주 이강사 예약 후 결근 입력 → 회색 칸에 남고 [삭제]만
		mvc.perform(post("/reservations").param("instructorId", d.lee.getId().toString())
			.param("date", "2026-11-09").param("period", "2").param("content", "박영희"));
		overrideRepository.save(new InstructorOverride(d.lee, OverrideType.ABSENT, MON.plusDays(7), MON.plusDays(7),
			Set.of(), null, null));
		mvc.perform(get("/instructors?date=2026-11-09"))
			.andExpect(content().string(containsString("메모: 박영희")))
			.andExpect(content().string(containsString("근무 없음 · 확인 필요")))
			.andExpect(content().string(not(containsString("value=\"박영희\""))));
	}

	@Test
	void 미배정_예약은_근무와_상관없이_적고_배정하면_강사_칸으로_옮긴다() throws Exception {
		String next = "/instructors?date=2026-11-09";
		// 1교시는 근무 강사가 없어도 미배정 칸에는 적을 수 있음
		mvc.perform(post("/reservations").param("date", "2026-11-09").param("period", "1")
				.param("content", "박민수(첫 방문)").param("back", next))
			.andExpect(flash().attribute("message", "메모를 저장했습니다."));
		mvc.perform(post("/reservations").param("date", "2026-11-09").param("period", "2").param("content", "2명"));
		mvc.perform(get(next))
			.andExpect(content().string(containsString("value=\"박민수(첫 방문)\"")))
			.andExpect(content().string(containsString("value=\"2명\"")))
			.andExpect(content().string(containsString("/reservations/assign")));

		// 근무하지 않는 강사에게는 배정 불가
		mvc.perform(post("/reservations/assign").param("date", "2026-11-09").param("period", "2")
				.param("instructorId", d.choi.getId().toString()))
			.andExpect(flash().attribute("error", containsString("근무하지 않아")));

		// 이강사 칸에 이미 예약이 있으면 이어 붙이고 미배정 칸은 비움
		mvc.perform(post("/reservations").param("instructorId", d.lee.getId().toString())
			.param("date", "2026-11-09").param("period", "2").param("content", "김철수"));
		mvc.perform(post("/reservations/assign").param("date", "2026-11-09").param("period", "2")
				.param("instructorId", d.lee.getId().toString()).param("back", next))
			.andExpect(redirectedUrl(next))
			.andExpect(flash().attribute("message", "예약을 배정했습니다."));
		mvc.perform(get(next))
			.andExpect(content().string(containsString("value=\"김철수, 2명\"")))
			.andExpect(content().string(not(containsString("value=\"2명\""))));

		// 빈 강사 칸이면 그대로 옮김
		mvc.perform(post("/reservations").param("date", "2026-11-09").param("period", "2").param("content", "최지영"));
		mvc.perform(post("/reservations/assign").param("date", "2026-11-09").param("period", "2")
			.param("instructorId", d.kim.getId().toString()));
		mvc.perform(get(next))
			.andExpect(content().string(containsString("value=\"최지영\"")))
			.andExpect(content().string(containsString("value=\"박민수(첫 방문)\"")));

		// 미배정 칸도 비우면 삭제
		mvc.perform(post("/reservations").param("date", "2026-11-09").param("period", "1").param("content", ""))
			.andExpect(flash().attribute("message", "메모를 지웠습니다."));
		mvc.perform(get(next))
			.andExpect(content().string(not(containsString("박민수"))));
	}

	@Test
	void 지난_교시의_미배정_예약은_글과_삭제만_보인다() throws Exception {
		// 지금 = 11/2 14:00 → 1교시는 지남
		mvc.perform(post("/reservations").param("date", "2026-11-02").param("period", "1").param("content", "안온사람"));
		mvc.perform(get(DAY))
			.andExpect(content().string(containsString("메모: 안온사람")))
			.andExpect(content().string(not(containsString("value=\"안온사람\""))));
	}
}
