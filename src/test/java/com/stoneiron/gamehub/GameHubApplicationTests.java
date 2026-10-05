package com.stoneiron.gamehub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** 스프링 설정(빈 조립, WebSocket 설정)이 문제없이 올라오는지 확인한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:gamehub-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class GameHubApplicationTests {

    @Test
    void contextLoads() {
    }
}
