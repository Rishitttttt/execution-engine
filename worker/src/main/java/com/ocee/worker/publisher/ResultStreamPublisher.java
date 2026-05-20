package com.ocee.worker.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocee.common.JobResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ResultStreamPublisher {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String stream;

    public ResultStreamPublisher(StringRedisTemplate redis,
                                 @Qualifier("streamObjectMapper") ObjectMapper mapper,
                                 @Value("${ocee.streams.results}") String stream) {
        this.redis = redis;
        this.mapper = mapper;
        this.stream = stream;
    }

    public void publish(JobResult result) {
        try {
            String payload = mapper.writeValueAsString(result);
            MapRecord<String, String, String> record = StreamRecords
                    .mapBacked(Map.of("payload", payload))
                    .withStreamKey(stream);
            redis.opsForStream().add(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JobResult", e);
        }
    }
}
