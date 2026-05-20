package com.ocee.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocee.common.JobResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

@Component
public class ResultStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResultStreamConsumer.class);

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ResultApplier applier;
    private final String resultsStream;
    private final String consumerGroup;
    private final String consumerName;

    private Subscription subscription;

    public ResultStreamConsumer(StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
                                StringRedisTemplate redis,
                                @Qualifier("streamObjectMapper") ObjectMapper mapper,
                                ResultApplier applier,
                                @Value("${ocee.streams.results}") String resultsStream,
                                @Value("${ocee.streams.consumer-group}") String consumerGroup) {
        this.container = container;
        this.redis = redis;
        this.mapper = mapper;
        this.applier = applier;
        this.resultsStream = resultsStream;
        this.consumerGroup = consumerGroup;
        this.consumerName = "api-" + safeHostname() + "-" + ProcessHandle.current().pid();
    }

    @PostConstruct
    void subscribe() {
        ensureGroup();
        Consumer consumer = Consumer.from(consumerGroup, consumerName);
        subscription = container.receive(consumer,
                StreamOffset.create(resultsStream, ReadOffset.lastConsumed()),
                this::onMessage);
        log.info("Subscribed to {} as consumer={} in group={}", resultsStream, consumerName, consumerGroup);
    }

    private void ensureGroup() {
        try {
            redis.opsForStream().createGroup(resultsStream, ReadOffset.from("0"), consumerGroup);
        } catch (Exception ignored) {
            // BUSYGROUP if group already exists; or stream doesn't exist yet (XADD MKSTREAM happens first)
        }
    }

    private void onMessage(MapRecord<String, String, String> record) {
        try {
            String payload = record.getValue().get("payload");
            JobResult result = mapper.readValue(payload, JobResult.class);
            applier.apply(result);
            redis.opsForStream().acknowledge(consumerGroup, record);
        } catch (Exception e) {
            log.error("Failed to process result record {}", record.getId(), e);
        }
    }

    private static String safeHostname() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "unknown"; }
    }
}
