package com.ocee.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocee.common.JobMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RedisStreamJobPublisher implements JobPublisher {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String stream;

    public RedisStreamJobPublisher(StringRedisTemplate redis,
                                   @Qualifier("streamObjectMapper") ObjectMapper mapper,
                                   @Value("${ocee.streams.jobs}") String stream) {
        this.redis = redis;
        this.mapper = mapper;
        this.stream = stream;
    }

    @Override
    public String publish(JobMessage message) {
        try {
            String payload = mapper.writeValueAsString(message);
            MapRecord<String, String, String> record = StreamRecords
                    .mapBacked(Map.of("payload", payload))
                    .withStreamKey(stream);
            RecordId id = redis.opsForStream().add(record);
            return id == null ? null : id.getValue();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JobMessage", e);
        }
    }
}
