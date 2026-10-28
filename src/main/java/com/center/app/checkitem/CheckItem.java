package com.center.app.checkitem;

import java.time.LocalDateTime;

import com.center.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 확인 필요 목록 한 줄. 사람 이름은 다른 테이블과 연결하지 않고 텍스트로만 기록
@Entity
@Table(name = "check_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckItem extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 500)
	private String content;

	@Column(name = "person_name", length = 30)
	private String personName;

	@Column(nullable = false)
	private boolean resolved;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	public CheckItem(String content, String personName) {
		this.content = content;
		this.personName = personName;
	}

	public void resolve(LocalDateTime now) {
		this.resolved = true;
		this.resolvedAt = now;
	}

	// 잘못 체크한 항목을 남은 목록으로 되돌림
	public void reopen() {
		this.resolved = false;
		this.resolvedAt = null;
	}
}
