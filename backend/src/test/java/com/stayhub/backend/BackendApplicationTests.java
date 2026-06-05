package com.stayhub.backend;

import com.stayhub.backend.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestSecurityConfig.class)
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
