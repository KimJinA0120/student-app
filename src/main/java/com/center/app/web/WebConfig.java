package com.center.app.web;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@Configuration
public class WebConfig {

	// 화면의 요일 표시(#temporals의 'E')를 한국어로 고정
	// 기본값은 브라우저 언어를 따라가서, 영어 설정 PC에서는 "Mon"으로 나옴
	// (설정 파일 대신 빈으로 둔 이유: 테스트·서버 PC의 외부 설정 파일과 상관없이 항상 적용)
	@Bean
	public LocaleResolver localeResolver() {
		return new FixedLocaleResolver(Locale.KOREAN);
	}
}
