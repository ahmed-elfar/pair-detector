package tracker.utils.cache;

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.annotation.Nullable;
import org.cache2k.io.AdvancedCacheLoader;
import org.slf4j.Logger;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.response.EthBlock;
import tracker.utils.connection.Web3jConnection;

import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

public abstract class BlockTimestamp {

    private Web3jConnection web3jConnection;
    private final Cache<Long, Timestamp> cache;
    private final Logger logger;
    private static final long TIME_LAG = 5000;

    public BlockTimestamp(String name, Web3jConnection web3jConnection, Logger logger) {
        this.web3jConnection = web3jConnection;
        this.logger = logger;
        this.cache = init(name);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(cache.toString());
            cache.close();
        }));
    }

    public Timestamp getBlockTimestamp(long blockId) {
        return cache.get(blockId);
    }

    public void putBlockTimestamp(long blockId, Timestamp timestamp) {
        cache.put(blockId, timestamp);
    }

    private Cache<Long, Timestamp> init(String cacheName) {
        return Cache2kBuilder.of(Long.class, Timestamp.class)
                .name(cacheName)
                .eternal(true)
                .entryCapacity(300)
                .storeByReference(true)
                .loader(new AdvancedCacheLoader<Long, Timestamp>() {
                    @Override
                    public Timestamp load(Long aLong, long l, @Nullable CacheEntry<Long, Timestamp> cacheEntry) throws Exception {
                        if (cacheEntry != null) System.out.println("not null cache entry");
                        logger.info("Requesting Block Timestamp.");
                        long blockTimestamp = getBlockTimestamp(aLong, web3jConnection.getMainConnection(), true);
                        return new Timestamp(TimeUnit.SECONDS.toMillis(blockTimestamp));
                    }

                    long getBlockTimestamp(long blockId, Web3j web3j, boolean withFallback) {
                        long blockTimestamp;
                        try {
                            int attempts = 5;
                            EthBlock.Block block;
                            do {
                                block = web3j
                                        .ethGetBlockByNumber(new DefaultBlockParameterNumber(blockId), false)
                                        .send()
                                        .getBlock();
                                attempts++;
                            } while (block == null && attempts < 5);

                            if (block == null) {
                                if (withFallback) {
                                    logger.warn("Failed to get block timestamp for block: {}, using fallback connection", blockId);
                                    if (web3jConnection.getBackupConnection() != null)
                                        return getBlockTimestamp(blockId, web3jConnection.getBackupConnection(), false);
                                    logger.warn("No backup connection found for block: {}, using default current timestamp - {} ms", blockId, TIME_LAG);
                                } else {
                                    logger.warn("Failed to get block timestamp for block: {}, using default current timestamp - {} ms", blockId, TIME_LAG);
                                }
                                blockTimestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - TIME_LAG);
                            } else {
                                blockTimestamp = block.getTimestamp().longValue();
                            }
                        } catch (Exception e) {
                            if (withFallback) {
                                logger.warn("Failed to get block timestamp for block: {}, using fallback connection", blockId, e);
                                if (web3jConnection.getBackupConnection() != null)
                                    return getBlockTimestamp(blockId, web3jConnection.getBackupConnection(), false);
                                logger.warn("No backup connection found for block: {}, using default current timestamp - {} ms", blockId, TIME_LAG);
                            } else {
                                logger.warn("Failed to get block timestamp for block: {}, using default current timestamp - {} ms", blockId, TIME_LAG, e);
                            }
                            blockTimestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - TIME_LAG);
                        }
                        return blockTimestamp;
                    }
                })
                .build();
    }
}
