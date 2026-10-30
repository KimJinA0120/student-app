package com.center.app.consultation;

import java.time.LocalDate;

import com.center.app.common.BaseEntity;
import com.center.app.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 상담일지 한 줄 (전화·방문 문의). 이름 없이 연락처만, 또는 연락처 없이 이름만 적는 경우도 있음
// 희망 요일·시간은 계산에 쓰지 않아 자유 글 ("매일", "월수금" / "09:30")
// [수강생 등록]으로 수강생이 되면 student가 채워져 "등록됨"으로 표시
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consultation extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 30)
	private String name;

	@Column(length = 20)
	private String phone;

	@Column(name = "hope_class", length = 100)
	private String hopeClass;

	@Column(name = "hope_day", length = 30)
	private String hopeDay;

	@Column(name = "hope_time", length = 30)
	private String hopeTime;

	@Column(length = 500)
	private String memo;

	@Column(name = "consult_date")
	private LocalDate consultDate;

	// 등록한 수강생. 수강생을 지우면 StudentService가 연결만 끊음 (상담 기록은 남김)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "student_id")
	private Student student;

	public Consultation(Info info) {
		change(info);
	}

	public void change(Info info) {
		this.name = info.name();
		this.phone = info.phone();
		this.hopeClass = info.hopeClass();
		this.hopeDay = info.hopeDay();
		this.hopeTime = info.hopeTime();
		this.memo = info.memo();
		this.consultDate = info.consultDate();
	}

	public void linkStudent(Student student) {
		this.student = student;
	}

	public void unlinkStudent() {
		this.student = null;
	}

	// 등록·수정 폼에서 그대로 받는 값
	public record Info(String name, String phone, String hopeClass, String hopeDay, String hopeTime, String memo,
			LocalDate consultDate) {
	}
}
