package com.center.app.manager;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.center.app.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Manager extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 30)
	private String name;

	@Column(length = 20)
	private String phone;

	// 기본 근무표는 매니저 정보의 일부라 함께 저장·삭제 (강사의 기본 배치와 같은 방식)
	@OneToMany(mappedBy = "manager", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ManagerShift> shifts = new ArrayList<>();

	public Manager(String name, String phone) {
		this.name = name;
		this.phone = phone;
	}

	public void changeInfo(String name, String phone) {
		this.name = name;
		this.phone = phone;
	}

	public void addShift(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
		shifts.add(new ManagerShift(this, dayOfWeek, startTime, endTime));
	}

	public void clearShifts() {
		shifts.clear();
	}

	public ManagerShift shiftOn(DayOfWeek dayOfWeek) {
		return shifts.stream().filter(s -> s.getDayOfWeek() == dayOfWeek).findFirst().orElse(null);
	}
}
