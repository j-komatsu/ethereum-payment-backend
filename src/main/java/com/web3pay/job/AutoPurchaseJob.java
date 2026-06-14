package com.web3pay.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 毎月1日 JST 0:00 に有効なサブスクリプション全件を処理する自動購入ジョブ。
 *
 * ShedLock により複数インスタンスが同時実行しないよう排他制御される。
 * 各サブスクリプションは AutoPurchaseProcessor で REQUIRES_NEW トランザクションとして処理され、
 * 1件の失敗が他件の実行記録をロールバックしない。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoPurchaseJob {

    private final AutoPurchaseSubscriptionRepository subscriptionRepository;
    private final AutoPurchaseProcessor processor;

    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Tokyo")
    @SchedulerLock(name = "autoPurchaseJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void executeMonthlyPurchases() {
        List<AutoPurchaseSubscription> subscriptions = subscriptionRepository.findByActiveTrue();
        log.info("AutoPurchaseJob started: {} active subscriptions", subscriptions.size());

        for (AutoPurchaseSubscription sub : subscriptions) {
            processor.process(sub);
        }

        log.info("AutoPurchaseJob completed");
    }
}
