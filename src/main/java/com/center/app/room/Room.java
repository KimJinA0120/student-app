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

	// enum 컬럼은 columnDefinition으로 varchar 지정: 안 하면 H2에서 ENUM 타입이나 IN(...) CHECK가 생겨
	// 나중에 enum 값을 추가해도 ddl-auto=update가 바꿔 주지 않아 저장이 실패함
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "varchar(20)")
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
