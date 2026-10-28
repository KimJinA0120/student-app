package com.center.app.instructor;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import com.center.app.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(indexes = @Index(name = "idx_instructor_name", columnList = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Instructor extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 30)
	private String name;

	@Column(length = 20)
	private String phone;

	// 기본 배치는 강사 정보의 일부라 강사와 함께 저장·삭제 (orphanRemoval: 목록에서 빼면 행도 삭제)
	// 날짜별 예외·당일 상태는 날짜 범위로 따로 조회하므로 컬렉션으로 두지 않음
	@OneToMany(mappedBy = "instructor", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<InstructorSlot> slots = new ArrayList<>();

	public Instructor(String name, String phone) {
		this.name = name;
		this.phone = phone;
	}

	public void changeInfo(String name, String phone) {
		this.name = name;
		this.phone = phone;
	}

	public void addSlot(DayOfWeek dayOfWeek, int period, InstructorRole role, String note) {
		slots.add(new InstructorSlot(this, dayOfWeek, period, role, note));
	}

	public void clearSlots() {
		slots.clear();
	}
}
