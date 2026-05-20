package com.ocee.queue;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterHandler {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterHandler.class);

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final StringRedisTemplate redis;
    private final DeadLetterApplier applier;
    private final String deadStream;
    private final String consumerGroup;
    private final String consumerName;
    private Subscription subscription;

    public DeadLetterHandler(StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
                             StringRedisTemplate redis,
                             DeadLetterApplier applier,
                             @Value("${ocee.streams.dead-letter}") String deadStream,
                             @Value("${ocee.streams.consumer-group}") String consumerGroup) {
        this.container = container;
        this.redis = redis;
        this.applier = applier;
        this.deadStream = deadStream;
        this.consumerGroup = consumerGroup + ".dead";
        this.consumerName = "api-dlq-" + ProcessHandle.current().pid();
    }

    @PostConstruct
    void subscribe() {
        try {
            redis.opsForStream().createGroup(deadStream, ReadOffset.from("0"), consumerGroup);
        } catch (Exception ignored) {}
        Consumer c = Consumer.from(consumerGroup, consumerName);
        subscription = container.receive(c,
                StreamOffset.create(deadStream, ReadOffset.lastConsumed()),
                this::onDead);
        log.info("Subscribed to {} as {} (DLQ)", deadStream, consumerName);
    }

    private void onDead(MapRecord<String, String, String> record) {
        String payload = record.getValue().get("payload");
        String reason = record.getValue().getOrDefault("reason", "dead-letter");
        try {
            applier.apply(payload, reason);
            redis.opsForStream().acknowledge(consumerGroup, record);
        } catch (Exception e) {
            log.error("DLQ processing failed for {}", record.getId(), e);
        }
    }
}
