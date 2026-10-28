package com.center.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.center.app.room.Room;
import com.center.app.room.RoomInitializer;
import com.center.app.room.RoomRepository;
import com.center.app.room.RoomType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AppApplicationTests {

	@Autowired
	RoomRepository roomRepository;

	@Autowired
	RoomInitializer roomInitializer;

	@Test
	void 교실_초기_데이터는_한_번만_들어간다() {
		// 앱 시작 시 이미 한 번 실행됨 → 다시 실행해도 8개 유지
		roomInitializer.run();

		var rooms = roomRepository.findAllByOrderBySortOrderAsc();
		assertThat(rooms).hasSize(8);
		assertThat(rooms.getFirst().getName()).isEqualTo("203호");
		assertThat(rooms.getFirst().getCreatedAt()).isNotNull(); // Auditing 동작 확인
		assertThat(rooms).extracting(Room::getType).endsWith(RoomType.COUNSEL, RoomType.STORAGE);
	}
}
