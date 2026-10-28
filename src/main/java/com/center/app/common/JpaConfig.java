package com.center.app.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// Auditing 설정을 메인 클래스가 아닌 별도 설정 클래스에 둠
// → 컨트롤러만 띄우는 슬라이스 테스트(@WebMvcTest)에서 JPA 설정 오류가 나지 않음
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
