package com.center.app.consultation;

import java.util.List;

import com.center.app.student.Student;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.center.app.common.Inputs.blankToNull;
import static com.center.app.common.Inputs.optional;

// 상담일지 등록/수정/삭제와 목록, 수강생 등록 연결
@Service
@RequiredArgsConstructor
@Transactional
public class ConsultationService {

	private final ConsultationRepository repository;

	// 이름·연락처에 keyword가 들어간 상담 (빈 값이면 전체). pendingOnly면 아직 수강생 등록 안 한 것만
	@Transactional(readOnly = true)
	public List<Consultation> list(String keyword, boolean pendingOnly) {
		String q = keyword == null ? "" : keyword.trim();
		return repository.findAllWithStudent().stream()
			.filter(c -> !pendingOnly || c.getStudent() == null)
			.filter(c -> q.isEmpty() || contains(c.getName(), q) || contains(c.getPhone(), q))
			.toList();
	}

	@Transactional(readOnly = true)
	public Consultation get(Long id) {
		return repository.findWithStudentById(id)
			.orElseThrow(() -> new IllegalArgumentException("상담 기록을 찾을 수 없습니다: " + id));
	}

	// 수강생 등록 폼에 옮겨 담을 상담. 이미 등록한 상담이면 막음 (같은 사람을 두 번 등록하지 않도록)
	@Transactional(readOnly = true)
	public Consultation getForRegistration(Long id) {
		Consultation c = get(id);
		checkNotRegistered(c);
		return c;
	}

	public Long create(Consultation.Info info) {
		return repository.save(new Consultation(valid(info))).getId();
	}

	@Transactional(readOnly = true)
	public boolean isEmpty() {
		return repository.count() == 0;
	}

	// 엑셀 첫 이전: 한 건이라도 입력 오류가 있으면 전부 취소 (반쯤 들어간 상태로 남지 않도록)
	// 그사이 누가 먼저 적었으면 넣지 않음 (상담일지가 비어 있을 때만 = 처음 한 번만)
	public int importAll(List<Consultation.Info> rows) {
		if (!isEmpty()) {
			return 0;
		}
		for (int i = 0; i < rows.size(); i++) {
			try {
				create(rows.get(i));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException((i + 1) + "번째 상담: " + e.getMessage(), e);
			}
		}
		return rows.size();
	}

	public void update(Long id, Consultation.Info info) {
		find(id).change(valid(info));
	}

	public void delete(Long id) {
		repository.delete(find(id));
	}

	// 상담에서 수강생을 등록했을 때 (StudentService가 수강생 저장과 같은 트랜잭션에서 부름)
	public void linkStudent(Long id, Student student) {
		Consultation c = find(id);
		checkNotRegistered(c);
		c.linkStudent(student);
	}

	// 수강생을 지울 때 상담 기록은 남기고 연결만 끊음
	public void unlinkStudent(Student student) {
		repository.findByStudentId(student.getId()).forEach(Consultation::unlinkStudent);
	}

	private void checkNotRegistered(Consultation c) {
		if (c.getStudent() != null) {
			throw new IllegalArgumentException("이미 수강생으로 등록한 상담입니다.");
		}
	}

	private static boolean contains(String value, String q) {
		return value != null && value.contains(q);
	}

	private Consultation find(Long id) {
		return repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("상담 기록을 찾을 수 없습니다: " + id));
	}

	// 성명·연락처는 둘 다 선택이지만 둘 중 하나는 있어야 누구인지 알 수 있음
	private Consultation.Info valid(Consultation.Info in) {
		if (blankToNull(in.name()) == null && blankToNull(in.phone()) == null) {
			throw new IllegalArgumentException("성명이나 연락처 중 하나는 입력하세요.");
		}
		return new Consultation.Info(
			optional(in.name(), 30, "성명"),
			optional(in.phone(), 20, "연락처"),
			optional(in.hopeClass(), 100, "희망 수업"),
			optional(in.hopeDay(), 30, "희망 요일"),
			optional(in.hopeTime(), 30, "희망 시간"),
			optional(in.memo(), 500, "비고"),
			in.consultDate());
	}
}
