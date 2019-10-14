package com.flightstats.hub.dao.aws;

import com.amazonaws.services.s3.model.BucketLifecycleConfiguration;
import com.amazonaws.services.s3.model.lifecycle.LifecycleFilter;
import com.amazonaws.services.s3.model.lifecycle.LifecyclePrefixPredicate;
import com.flightstats.hub.model.ChannelConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
class S3ConfigStrategy {

    final static String BATCH_POSTFIX = "Batch";
    final static String SINGLE_POSTFIX = "";
    final static String BUCKET_LIFECYCLE_RULE_PREFIX = "HUB_";

    static List<BucketLifecycleConfiguration.Rule> apportion(Iterable<ChannelConfig> channelConfigs, DateTime timeForSharding, int max) {
        List<BucketLifecycleConfiguration.Rule> rules = new ArrayList<>();
        for (ChannelConfig config : channelConfigs) {
            if (!config.getKeepForever()) {  // no rule = keep forever
                addRule(rules, config);
            }
        }
        if (rules.size() <= max) {
            return rules;
        }
        return handleMax(channelConfigs, timeForSharding, max, rules.size());
    }

    private static List<BucketLifecycleConfiguration.Rule> handleMax(Iterable<ChannelConfig> channelConfigs, DateTime timeForSharding, int max, double rulesCount) {
        int buckets = (int) Math.ceil(rulesCount / (0.8 * max));
        Map<Integer, List<BucketLifecycleConfiguration.Rule>> shardedRules = new HashMap<>();
        for (ChannelConfig config : channelConfigs) {
            byte[] md5 = DigestUtils.md5(config.getDisplayName());
            int mod = Math.abs(md5[0]) % buckets;
            List<BucketLifecycleConfiguration.Rule> ruleList = shardedRules.getOrDefault(mod, new ArrayList<>());
            shardedRules.put(mod, ruleList);
            addRule(ruleList, config);

        }
        int days = 2;
        int activeShard = timeForSharding.getDayOfYear() / days % buckets;
        log.debug("getDayOfYear {} buckets {} activeShard {}", timeForSharding.getDayOfYear(), buckets, activeShard);
        List<BucketLifecycleConfiguration.Rule> rules = shardedRules.get(activeShard);
        log.debug("base rules  {}", rules.size());
        if (rules.size() < max) {
            activeShard++;
            if (activeShard == buckets) {
                activeShard = 0;
            }
            List<BucketLifecycleConfiguration.Rule> nextRules = shardedRules.get(activeShard);
            int additionalRules = max - rules.size();
            for (int i = 0; i < additionalRules; i++) {
                rules.add(nextRules.get(i));
            }
        }
        log.info("total rules {}", rules.size());
        log.info("shardedRules {} keys {}", shardedRules.size(), shardedRules.keySet());
        return rules;
    }

    private static void addRule(List<BucketLifecycleConfiguration.Rule> rules, ChannelConfig config) {
        if (config.getTtlDays() > 0) {
            if (config.isSingle() || config.isBoth()) {
                rules.add(createRule(config, getChannelTypedName(config, SINGLE_POSTFIX)));
            }
            if (config.isBatch() || config.isBoth()) {
                rules.add(createRule(config, getChannelTypedName(config, BATCH_POSTFIX)));
            }
        }
    }

    static String getChannelTypedName(ChannelConfig config, String postfix) {
        return config.getDisplayName() + postfix;
    }

    private static BucketLifecycleConfiguration.Rule createRule(ChannelConfig config, String id) {
        return new BucketLifecycleConfiguration.Rule()
                .withFilter(new LifecycleFilter(new LifecyclePrefixPredicate(id + "/")))
                .withId(BUCKET_LIFECYCLE_RULE_PREFIX.concat(id))
                .withExpirationInDays((int) config.getTtlDays())
                .withStatus(BucketLifecycleConfiguration.ENABLED);
    }

    /**
     * S3 Bucket lifecycle rules limit                         = 1000 per bucket
     * Max S3 Bucket lifecycle rules Hub can create            = 990
     * Max S3 Bucket lifecycle rules terraform code can create = 10
     * The lifecycle rules created in hub only expires the objects based on TTL days configuration in hub channel.
     * The expiration action in S3 adds delete marker to current object and does not delete it.
     * The lifecycle rules set in terraform code cleans up all the delete markers and previous versions of the object in the S3 bucket.
     **/
    static List<BucketLifecycleConfiguration.Rule> getNonHubBucketLifecycleRules(BucketLifecycleConfiguration config) {
        List<BucketLifecycleConfiguration.Rule> currentBucketLifecycleRules = config.getRules();
        return currentBucketLifecycleRules
                        .parallelStream()
                        .filter(rule -> !rule.getId().startsWith(BUCKET_LIFECYCLE_RULE_PREFIX))
                        .limit(10)
                        .collect(Collectors.toList());
    }
}
