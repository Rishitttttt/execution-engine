package com.ocee.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocee.common.JobMessage;
import com.ocee.worker.config.WorkerProperties;
import com.ocee.worker.publisher.ResultStreamPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class PendingJobReclaimer {

    private static final Logger log = LoggerFactory.getLogger(PendingJobReclaimer.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ResultStreamPublisher resultPublisher;
    private final JobStreamConsumer consumer;
    private final WorkerProperties props;
    private final String jobsStream;
    private final String deadStream;
    private final String consumerGroup;

    public PendingJobReclaimer(StringRedisTemplate redis,
                               @Qualifier("streamObjectMapper") ObjectMapper mapper,
                               ResultStreamPublisher resultPublisher,
                               JobStreamConsumer consumer,
                               WorkerProperties props,
                               @Value("${ocee.streams.jobs}") String jobsStream,
                               @Value("${ocee.streams.dead-letter}") String deadStream,
                               @Value("${ocee.streams.consumer-group}") String consumerGroup) {
        this.redis = redis;
        this.mapper = mapper;
        this.resultPublisher = resultPublisher;
        this.consumer = consumer;
        this.props = props;
        this.jobsStream = jobsStream;
        this.deadStream = deadStream;
        this.consumerGroup = consumerGroup;
    }

    @Scheduled(fixedDelayString = "${ocee.worker.reclaim-interval-ms:10000}")
    public void reclaim() {
        Duration timeout = Duration.ofSeconds(props.visibilityTimeoutSeconds());
        try {
            PendingMessagesSummary summary = redis.opsForStream().pending(jobsStream, consumerGroup);
            if (summary == null || summary.getTotalPendingMessages() == 0) return;

            // Range over the entire group's PEL
            PendingMessages pending = redis.opsForStream()
                    .pending(jobsStream, consumerGroup, Range.unbounded(), 100L);
            for (PendingMessage pm : pending) {
                if (pm.getElapsedTimeSinceLastDelivery().compareTo(timeout) < 0) continue;
                if (pm.getTotalDeliveryCount() >= props.maxDeliveries()) {
                    moveToDeadLetter(pm);
                } else {
                    redis.opsForStream().claim(jobsStream, consumerGroup, consumer.consumerName(), timeout, pm.getId());
                    log.info("Reclaimed job {} (delivery #{})", pm.getId(), pm.getTotalDeliveryCount());
                }
            }
        } catch (Exception e) {
            log.warn("Reclaim sweep failed", e);
        }
    }

    private void moveToDeadLetter(PendingMessage pm) {
        try {
            List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                    .range(jobsStream, Range.just(pm.getId().getValue()));
            String payload = records.isEmpty() ? null
                    : (String) records.get(0).getValue().get("payload");
            String token = null;
            if (payload != null) {
                try {
                    JobMessage msg = mapper.readValue(payload, JobMessage.class);
                    token = msg.token();
                } catch (Exception ignored) {}
            }
            MapRecord<String, String, String> dead = StreamRecords
                    .mapBacked(Map.of(
                            "payload", payload == null ? "" : payload,
                            "reason", "exceeded max-deliveries=" + props.maxDeliveries(),
                            "delivery_count", String.valueOf(pm.getTotalDeliveryCount())))
                    .withStreamKey(deadStream);
            redis.opsForStream().add(dead);

            redis.opsForStream().acknowledge(jobsStream, consumerGroup, pm.getId().getValue());
            log.error("Job {} (token={}) moved to {} after {} deliveries", pm.getId(), token, deadStream, pm.getTotalDeliveryCount());
        } catch (Exception e) {
            log.error("Failed to dead-letter {}", pm.getId(), e);
        }
    }
}
