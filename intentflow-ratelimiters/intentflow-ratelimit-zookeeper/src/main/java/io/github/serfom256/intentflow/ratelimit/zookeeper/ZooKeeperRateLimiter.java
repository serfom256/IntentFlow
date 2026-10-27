package io.github.serfom256.intentflow.ratelimit.zookeeper;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.pipeline.LlmRateLimiter;
import io.github.serfom256.intentflow.core.pipeline.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Function;

@Slf4j
public final class ZooKeeperRateLimiter implements LlmRateLimiter {

    private static final int ATTEMPTS = 20;

    private final CuratorFramework client;
    private final String root;
    private final int questions;
    private final Duration period;
    private final Function<IntentContext, String> key;
    private final boolean failOpen;
    private final Clock clock;

    public ZooKeeperRateLimiter(CuratorFramework client, String root, int questions, Duration period,
            Function<IntentContext, String> key, boolean failOpen, Clock clock) {
        if (questions < 1) {
            throw new IllegalArgumentException("questions must be positive: " + questions);
        }
        if (period.toMillis() < 1) {
            throw new IllegalArgumentException("period must be at least 1ms: " + period);
        }
        this.client = client;
        this.root = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        this.questions = questions;
        this.period = period;
        this.key = key;
        this.failOpen = failOpen;
        this.clock = clock;
    }

    @Override
    public void acquire(IntentContext caller) {
        String path = root + "/" + hash(key.apply(caller));
        try {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                if (take(path)) {
                    return;
                }
            }
            throw new RateLimitExceededException("The rate limit store is too busy; try again", Duration.ofSeconds(1));
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            if (!failOpen) {
                throw new RateLimitExceededException("The rate limit store is unavailable", Duration.ofSeconds(5));
            }
            log.warn("ZooKeeper rate limit check failed, letting the question through: {}", e.toString());
        }
    }

    private boolean take(String path) throws Exception {
        long now = clock.millis();
        Stat stat = new Stat();
        byte[] data;
        try {
            data = client.getData().storingStatIn(stat).forPath(path);
        } catch (KeeperException.NoNodeException missing) {
            try {
                client.create().creatingParentsIfNeeded().forPath(path, encode(questions - 1, now));
                return true;
            } catch (KeeperException.NodeExistsException raced) {
                return false;
            }
        }
        String[] parts = new String(data, StandardCharsets.UTF_8).split(":");
        double perMilli = (double) questions / period.toMillis();
        double tokens = Math.min(questions,
                Double.parseDouble(parts[0]) + Math.max(0, now - Long.parseLong(parts[1])) * perMilli);
        if (tokens < 1) {
            long waitMillis = (long) Math.ceil((1 - tokens) / perMilli);
            throw new RateLimitExceededException("Rate limit of " + questions + " questions per " + period + " reached",
                    Duration.ofMillis(waitMillis));
        }
        try {
            client.setData().withVersion(stat.getVersion()).forPath(path, encode(tokens - 1, now));
            return true;
        } catch (KeeperException.BadVersionException raced) {
            return false;
        }
    }

    private static byte[] encode(double tokens, long refilledAt) {
        return (tokens + ":" + refilledAt).getBytes(StandardCharsets.UTF_8);
    }

    private static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
