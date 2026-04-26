package com.hd.authz.cache;

import com.hd.authz.config.RedisConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvalidationListener implements MessageListener {

    private final RedisMessageListenerContainer container;
    private final PermCacheService cache;

    @PostConstruct
    public void register() {
        container.addMessageListener(this, new PatternTopic(RedisConfig.INVALIDATION_CHANNEL));
        container.start();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        if ("*".equals(body)) {
            cache.invalidateAllLocal();
            log.info("L1 cache fully invalidated by pub/sub");
        } else {
            for (String key : body.split("\\|")) {
                if (!key.isEmpty()) cache.invalidateLocal(key);
            }
            log.debug("L1 invalidated {} keys", body.split("\\|").length);
        }
    }
}
