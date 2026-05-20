package com.ocee.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocee.common.JobMessage;
import com.ocee.common.JobResult;
import com.ocee.worker.executor.Executor;
import com.ocee.worker.publisher.ResultStreamPublisher;
import com.ocee.worker.sandbox.SandboxProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class JobStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobStreamConsumer.class);

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final ResultStreamPublisher resultPublisher;
    private final String jobsStream;
    private final String consumerGroup;
    private final String consumerName;

    private Subscription subscription;
    private final Semaphore inflight;
    private final ExecutorService workerPool;

    public JobStreamConsumer(StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
                             StringRedisTemplate redis,
                             @Qualifier("streamObjectMapper") ObjectMapper mapper,
                             Executor executor,
                             ResultStreamPublisher resultPublisher,
                             @Value("${ocee.streams.jobs}") String jobsStream,
                             @Value("${ocee.streams.consumer-group}") String consumerGroup,
                             SandboxProperties sandboxProperties) {
        this.container = container;
        this.redis = redis;
        this.mapper = mapper;
        this.executor = executor;
        this.resultPublisher = resultPublisher;
        this.jobsStream = jobsStream;
        this.consumerGroup = consumerGroup;
        this.consumerName = "worker-" + safeHost() + "-" + ProcessHandle.current().pid();
        int n = sandboxProperties.effectiveConcurrency();
        this.inflight = new Semaphore(n);
        this.workerPool = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "sandbox-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    void subscribe() {
        ensureGroup();
        Consumer consumer = Consumer.from(consumerGroup, consumerName);
        subscription = container.receive(consumer,
                StreamOffset.create(jobsStream, ReadOffset.lastConsumed()),
                this::onJob);
        log.info("Subscribed to {} as consumer={} group={}", jobsStream, consumerName, consumerGroup);
    }

    private void ensureGroup() {
        try {
            redis.opsForStream().createGroup(jobsStream, ReadOffset.from("0"), consumerGroup);
        } catch (Exception ignored) { /* group already exists or stream not yet created */ }
    }

    private void onJob(MapRecord<String, String, String> record) {
        try { inflight.acquire(); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return;
        }
        workerPool.submit(() -> {
            try {
                JobMessage msg = mapper.readValue(record.getValue().get("payload"), JobMessage.class);
                JobResult result = executor.execute(msg);
                resultPublisher.publish(result);
                redis.opsForStream().acknowledge(consumerGroup, record);
            } catch (Exception e) {
                log.error("Failed to process job {}", record.getId(), e);
            } finally {
                inflight.release();
            }
        });
    }

    @PreDestroy
    void shutdown() {
        workerPool.shutdown();
        try { workerPool.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public String consumerName() { return consumerName; }

    private static String safeHost() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "unknown"; }
    }
}
