package com.dylanjohnpratt.paradise.be.config;

import com.dylanjohnpratt.paradise.be.service.DriveCacheManager;
import com.dylanjohnpratt.paradise.be.service.MyDriveService;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Warms the mediaCache drive cache on startup and refreshes it periodically
 * so that no user ever hits a cold cache.
 */
@Component
public class MediaCacheWarmupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MediaCacheWarmupScheduler.class);

    private final MyDriveService myDriveService;
    private final DriveCacheManager driveCacheManager;

    public MediaCacheWarmupScheduler(MyDriveService myDriveService,
                                     DriveCacheManager driveCacheManager) {
        this.myDriveService = myDriveService;
        this.driveCacheManager = driveCacheManager;
    }

    @PostConstruct
    public void warmOnStartup() {
        if (!driveCacheManager.isEnabled("mediaCache")) {
            logger.info("mediaCache caching is disabled, skipping warm-up");
            return;
        }
        logger.info("Warming mediaCache on startup...");
        try {
            myDriveService.warmDriveCache("mediaCache");
        } catch (Exception e) {
            logger.error("mediaCache warm-up failed, first request will populate cache", e);
        }
    }

    @Scheduled(fixedRateString = "${drive.cache.ttl-millis:7200000}")
    public void refreshCache() {
        if (!driveCacheManager.isEnabled("mediaCache")) {
            return;
        }
        logger.debug("Refreshing mediaCache...");
        try {
            myDriveService.warmDriveCache("mediaCache");
        } catch (Exception e) {
            logger.error("mediaCache refresh failed", e);
        }
    }
}
