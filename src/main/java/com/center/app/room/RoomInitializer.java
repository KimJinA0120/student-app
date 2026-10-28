package com.center.app.room;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 교실 초기 데이터: 테이블이 비어 있을 때만 삽입 (재시작해도 중복 안 됨)
@Component
@RequiredArgsConstructor
public class RoomInitializer implements CommandLineRunner {

	private final RoomRepository roomRepository;

	@Override
	@Transactional
	public void run(String... args) {
		if (roomRepository.count() > 0) {
			return;
		}
		List<Room> rooms = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			rooms.add(new Room((203 + i) + "호", RoomType.NORMAL, i + 1)); // 203~208호: 배정 1~6순위
		}
		rooms.add(new Room("202호", RoomType.COUNSEL, 7));
		rooms.add(new Room("201호", RoomType.STORAGE, 8));
		roomRepository.saveAll(rooms);
	}
}
