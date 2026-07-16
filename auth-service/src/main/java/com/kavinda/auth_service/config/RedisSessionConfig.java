package com.kavinda.auth_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.SaveMode;
import org.springframework.session.FlushMode;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

@Configuration
@EnableRedisIndexedHttpSession(
        redisNamespace = "pulsetrack:auth:sessions",
        flushMode = FlushMode.ON_SAVE,
        saveMode = SaveMode.ON_SET_ATTRIBUTE
)
public class RedisSessionConfig {
}
