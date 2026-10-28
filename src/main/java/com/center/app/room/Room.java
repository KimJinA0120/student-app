package com.center.app.room;

import com.center.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자, 외부에서 빈 객체 생성 방지
public class Room extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 30)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RoomType type;

	// 자동 배정 순서 (작을수록 먼저)
	@Column(name = "sort_order", nullable = false, unique = true)
	private int sortOrder;

	public Room(String name, RoomType type, int sortOrder) {
		this.name = name;
		this.type = type;
		this.sortOrder = sortOrder;
	}
}
