package tracker.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tracker.PairValidator;
import tracker.Wallet;
import tracker.contracts.Ecr20;
import org.web3j.protocol.Web3j;
import tracker.core.AsyncEvent;
import tracker.core.EventListener;
import tracker.data.SingletonPairs;
import tracker.utils.cache.BlockTimestamp;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PairResolver extends AsyncEvent<PairBase> {

    private final static Logger LOG = LoggerFactory.getLogger(PairResolver.class);

    private final AtomicInteger pairsCount;
    private final Web3j web3j;
    private final EventListener pairValidator;
    private final BlockTimestamp blockTimestampCache;

    private final PairAttempts pairAttempts = new PairAttempts();

    public PairResolver(String eventName, int poolSize, Web3j web3j, PairValidator pairValidator, BlockTimestamp blockTimestampCache) {
        super(eventName, poolSize);
        pairsCount = new AtomicInteger(0);
        this.web3j = web3j;
        this.pairValidator = pairValidator;
        this.blockTimestampCache = blockTimestampCache;
    }

    public int getPairsCount() {
        return pairsCount.get();
    }

    @Override
    public void onEvent(final PairBase rawPair) {
        Callable<PairInfo> callable = () -> {
            long start = System.currentTimeMillis();
            PairInfo pairInfo;
            try {
                Ecr20 ecr20 = Ecr20.load(rawPair.token0, web3j, Wallet.getWallet(), SingletonPairs.gasProvider);
                pairInfo = new PairInfo();
                pairInfo.token0 = SingletonPairs.tokens.get(rawPair.token0);
                if (pairInfo.token0 == null) pairInfo.token0 = createToken(ecr20, rawPair.token0);

                ecr20.setContractAddress(rawPair.token1);
                pairInfo.token1 = SingletonPairs.tokens.get(rawPair.token1);
                if (pairInfo.token1 == null) pairInfo.token1 = createToken(ecr20, rawPair.token1);

                pairInfo.blockId = rawPair.blockId;
                pairInfo.created = blockTimestampCache.getBlockTimestamp(rawPair.blockId);
                pairInfo.pairAddress = rawPair.pair;
                pairInfo.dexName = rawPair.dexName;

                long end = (System.currentTimeMillis() - start);
                LOG.info("{} listed pairs count: {}, Process request: {} ms", pairInfo.dexName, pairsCount.incrementAndGet(), end);
            } catch (Exception e) {
                LOG.error("Not ECR20 token: " + rawPair, e);
                pairAttempts.add(rawPair);
                pairInfo = null;
            }
            return pairInfo;
        };
        pairValidator.onEvent(executorService.submit(callable));
    }

    private class PairAttempts {

        Queue<PairBase> failedPairs = new ConcurrentLinkedQueue<>();
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        private Map<PairBase, Integer> attempts = new HashMap<>();
        private static final int MAX_ATTEMPTS = 10;

        public PairAttempts() {
            Runnable task = () -> {
                while (!failedPairs.isEmpty()) {
                    var pair = failedPairs.poll();
                    try {
                        attempts.computeIfAbsent(pair, key -> 0);
                        var attemptsCount = attempts.computeIfPresent(pair, (key, value) -> value + 1);
                        if (attemptsCount > MAX_ATTEMPTS) {
                            attempts.remove(pair);
                            LOG.info("Pair {}, failed to resolve {} times.", pair, MAX_ATTEMPTS);
                            continue;
                        }
                    } catch (Throwable e) {
                        LOG.error("Error trying to resolve pair : ", e);
                    }
                    onEvent(pair);
                }
            };
            scheduledExecutorService.scheduleAtFixedRate(task, 30, 30, TimeUnit.SECONDS);
        }

        void add(PairBase pairBase) {
            failedPairs.offer(pairBase);
        }

        void close() {
            scheduledExecutorService.shutdownNow();
        }
    }

    private TokenInfo createToken(Ecr20 ecr20, String tokenAddress) throws Exception {
        TokenInfo token = new TokenInfo();
        token.decimals = (int) ecr20.decimals().send().longValue();
        token.name = ecr20.name().send();
        token.symbol = ecr20.symbol().send();
        token.address = tokenAddress;
        return token;
    }

    @Override
    public void pollEvents() {

    }

    @Override
    public void open() {

    }

    @Override
    public void close() {
        super.close();
        pairAttempts.close();
    }

    public static class PairInfo {

        private long blockId;
        private final Timestamp received = new Timestamp(System.currentTimeMillis());
        private Timestamp created;
        private String dexName;
        private String pairAddress;
        private TokenInfo token0;
        private TokenInfo token1;

        private PairInfo() {
        }

        public long getBlockId() {
            return blockId;
        }

        public Timestamp getReceived() {
            return received;
        }

        public Timestamp getCreated() {
            return created;
        }

        public String getDexName() {
            return dexName;
        }

        public String getPairAddress() {
            return pairAddress;
        }

        public TokenInfo getToken0() {
            return token0;
        }

        public TokenInfo getToken1() {
            return token1;
        }

        @Override
        public String toString() {
            return "PairInfo {\n" +
                    " DEX=" + dexName +
                    ", blockId=" + blockId +
                    " \n received  =" + received +
                    " \n created   =" + created +
                    ", pairAddress='" + pairAddress + '\'' +
                    ", \n token0=" + token0 +
                    ", \n token1=" + token1 +
                    "\n}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PairInfo pairInfo = (PairInfo) o;
            return Objects.equals(pairAddress, pairInfo.pairAddress);
        }

        @Override
        public int hashCode() {
            return pairAddress.hashCode();
        }
    }

    @JsonInclude
    public static class TokenInfo {
        private int decimals;
        private String address;
        private String name;
        private String symbol;

        private TokenInfo() {
        }

        public static TokenInfo createToken(String address, int decimals, String name, String symbol) {
            TokenInfo token = new TokenInfo();
            token.decimals = decimals;
            token.address = address;
            token.name = name;
            token.symbol = symbol;
            return token;
        }

        public int getDecimals() {
            return decimals;
        }

        public String getAddress() {
            return address;
        }

        public String getName() {
            return name;
        }

        public String getSymbol() {
            return symbol;
        }

        @Override
        public String toString() {
            return "TokenInfo{" +
                    "address='" + address + '\'' +
                    ", name='" + name + '\'' +
                    ", symbol='" + symbol + '\'' +
                    ", decimals=" + decimals +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TokenInfo tokenInfo = (TokenInfo) o;
            return Objects.equals(address, tokenInfo.address);
        }

        @Override
        public int hashCode() {
            return address.hashCode();
        }
    }
}
