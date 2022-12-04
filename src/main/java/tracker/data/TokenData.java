package tracker.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.disposables.Disposable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.EventEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import tracker.Connector;
import tracker.Wallet;
import tracker.contracts.Pair;
import tracker.core.AsyncTaskManager;
import tracker.core.ConcurrentEvent;
import tracker.data.pricing.BaseTokenPrice;
import tracker.utils.AppManager;
import tracker.utils.PairResolver;
import tracker.utils.cache.BlockTimestamp;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

import static tracker.data.DataWriter.bscBlock;
import static tracker.data.DataWriter.polygonBlock;

public class TokenData {

    private static final Logger LOG = LoggerFactory.getLogger(TokenData.class);

    private final Web3j web3j;
    private final BlockTimestamp blockTimestampCache;
    private final DataWriter dataWriter;

    public final static String CREATOR_TEMP = "RESOLVING";

    @JsonProperty
    private Timestamp lastUpdated;
    @JsonProperty
    private final String dex;
    @JsonProperty
    private String contractCreator = CREATOR_TEMP;
    @JsonProperty
    private final PairResolver.TokenInfo token;

    @JsonProperty
    private int liquidityPools;

    @JsonProperty
    private BigDecimal tokenPriceUSD = BigDecimal.ZERO;

    @JsonProperty
    private BigDecimal totalLiquidityUSD = BigDecimal.ZERO;

    @JsonProperty
    private List<PairDataListener> pairSet;

    private static final Comparator<PairDataListener> comparator = Comparator.comparingLong(PairDataListener::getBlockId).reversed();

    public TokenData(Web3j web3j, PairResolver.TokenInfo token, String dex, BlockTimestamp blockTimestampCache, DataWriter dataWriter) {
        this.web3j = web3j;
        this.token = token;
        this.dex = dex;
        this.blockTimestampCache = blockTimestampCache;
        this.dataWriter = dataWriter;
        this.pairSet = new ArrayList<>(1);
    }

    public PairResolver.TokenInfo getToken() {
        return token;
    }

    public String getDex() {
        return dex;
    }

    @JsonIgnore
    public long getLastBlock() {
        return pairSet.get(0).blockId;
    }

    public void setContractCreator(String contractCreator) {
        this.contractCreator = contractCreator;
    }

    public void onAddPair(PairResolver.PairInfo pairInfo, boolean whiteListedTokenIsOne) {
        PairDataListener pairDataListener = new PairDataListener(pairInfo, whiteListedTokenIsOne);
        pairSet.add(pairDataListener);
        liquidityPools = pairSet.size();
        pairDataListener.open();
    }

    void reopen(Disposable disposable, PairDataListener pairDataListener) {
        AppManager.unTrack(disposable);
        pairDataListener.open();
    }

    public BigDecimal updateTokenLiquidityUSD() {
        BigDecimal totalLiquidity = pairSet.get(0).quotationReserveUSD;
        for (int i = 1; i < pairSet.size(); i++) {
            totalLiquidity = totalLiquidity.add(pairSet.get(i).quotationReserveUSD);
        }
        totalLiquidityUSD = totalLiquidity;
        if (getDex().equals("QuickSwap")) {
            if (getLastBlock() >= polygonBlock) dataWriter.onEvent(toJson());
        } else {
            if (getLastBlock() >= bscBlock) dataWriter.onEvent(toJson());
        }
        //toJson();
        //LOG.info(pairSet.get(0).blockId + " / " + lastUpdated + " : " + dex + ", " + token.getName() + " = " + tokenPriceUSD + ", " + token.getAddress());
        return totalLiquidityUSD;
    }

    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        mapper.setDateFormat(df);
        String jsonInString = null;
        try {
            jsonInString = mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return jsonInString;
    }

    public BigDecimal updateTokenPriceUSD() {
        Collections.sort(pairSet, comparator);
        tokenPriceUSD = pairSet.get(0).tokenPriceUSD;
        lastUpdated = pairSet.get(0).emitted;
        return tokenPriceUSD;
    }

    @Override
    public String toString() {
        return "TokenData {\n" +
                "liquidityPools=" + liquidityPools +
                "\n  token=" + token +
                "\n, pairSet=" + pairSet +
                "\n, tokenPriceUSD=" + tokenPriceUSD +
                "\n, tokenLiquidityUSD=" + totalLiquidityUSD +
                "\n}";
    }

    @JsonPropertyOrder(value = {"blockId", "created_", "emitted_", "received", "pairName", "pairAddress", "transactions", "tokenPriceUSD", "quotationPriceUSD", "quotationReserveUSD",
            "eventLag", "processTime"})
    private class PairDataListener extends ConcurrentEvent<Pair.SyncEventResponse> {

        private final PairResolver.PairInfo pairInfo;
        //private final Function<Pair, RemoteFunctionCall<BigInteger>> tokenPriceFunction;
        private final Consumer<Pair.SyncEventResponse> tokenLiquidityFunction;
        @JsonProperty
        private long blockId;
        @JsonProperty(value = "created_")
        private Timestamp created;
        @JsonProperty(value = "emitted_")
        private Timestamp emitted;
        @JsonProperty
        private Timestamp received = new Timestamp(System.currentTimeMillis());
        @JsonProperty
        private String pairName;
        @JsonProperty
        private String pairAddress;
        @JsonProperty
        private int transactions;
        @JsonProperty
        private int eventLag;
        @JsonProperty
        private int processTime;
        @JsonProperty
        private BigDecimal quotationPriceUSD = BigDecimal.ZERO;
        @JsonProperty
        private BigDecimal tokenPriceUSD = BigDecimal.ZERO;
        @JsonProperty
        private BigDecimal quotationReserveUSD = BigDecimal.ZERO;

        private final BaseTokenPrice priceFunction;
        private Disposable disposable;
        @JsonIgnore
        private int skipped = 0;

        public PairDataListener(PairResolver.PairInfo pairInfo, boolean whiteListedTokenIsOne) {
            super("SyncEvent");
            this.pairInfo = pairInfo;
            this.pairAddress = pairInfo.getPairAddress();
            //this.transactions = new AtomicInteger(0);
            if (whiteListedTokenIsOne) {
                this.priceFunction = SingletonPairs.price.get(pairInfo.getToken1().getAddress());
                this.pairName = pairInfo.getToken0().getName() + "/" + pairInfo.getToken1().getName();
                //tokenPriceFunction = (pair) -> pair.price0CumulativeLast();
                this.tokenLiquidityFunction = (syncEventResponse) -> {
                    BigDecimal p0 = new BigDecimal(syncEventResponse.reserve1);
                    BigDecimal p1 = new BigDecimal(syncEventResponse.reserve0);
                    var decs = SingletonPairs.Decimals.values();
                    var liquidityDecimal = new BigDecimal(decs[pairInfo.getToken1().getDecimals() - 1].value);
                    if (pairInfo.getToken0().getDecimals() != pairInfo.getToken1().getDecimals()) {
                        int max = Math.abs(pairInfo.getToken0().getDecimals() - pairInfo.getToken1().getDecimals());
                        var dec = new BigDecimal(decs[max - 1].value);
                        if (pairInfo.getToken0().getDecimals() > pairInfo.getToken1().getDecimals()) {
                            p1 = p1.divide(dec);
                            //p0 = p0.multiply(dec);
                        } else {
                            p0 = p0.divide(dec);
                            //p1 = p1.multiply(dec);
                            liquidityDecimal = new BigDecimal(decs[pairInfo.getToken0().getDecimals() - 1].value);
                        }
                    }
                    var p0d = p0;
                    var p1d = p1;
                    tokenPriceUSD = p0d.divide(p1d, 9, RoundingMode.FLOOR);
                    //System.out.println("WTF: Case1: " + tokenPriceUSD);
                    tokenPriceUSD = tokenPriceUSD.multiply(quotationPriceUSD).setScale(9, RoundingMode.FLOOR);
                    quotationReserveUSD = p0d.divide(liquidityDecimal, 6, RoundingMode.FLOOR).multiply(quotationPriceUSD).setScale(6, RoundingMode.FLOOR);
                };
            } else {
                this.priceFunction = SingletonPairs.price.get(pairInfo.getToken0().getAddress());
                this.pairName = pairInfo.getToken1().getName() + "/" + pairInfo.getToken0().getName();
                //tokenPriceFunction = (pair) -> pair.price1CumulativeLast();
                tokenLiquidityFunction = (syncEventResponse) -> {
                    BigDecimal p0 = new BigDecimal(syncEventResponse.reserve0);
                    BigDecimal p1 = new BigDecimal(syncEventResponse.reserve1);
                    var decs = SingletonPairs.Decimals.values();
                    var liquidityDecimal = new BigDecimal(decs[pairInfo.getToken0().getDecimals() - 1].value);
                    if (pairInfo.getToken0().getDecimals() != pairInfo.getToken1().getDecimals()) {
                        int max = Math.abs(pairInfo.getToken0().getDecimals() - pairInfo.getToken1().getDecimals());
                        var dec = new BigDecimal(decs[max - 1].value);
                        if (pairInfo.getToken0().getDecimals() > pairInfo.getToken1().getDecimals()) {
                            p0 = p0.divide(dec);
                            liquidityDecimal = new BigDecimal(decs[pairInfo.getToken1().getDecimals() - 1].value);
                            //p0 = p0.multiply(dec);
                        } else {
                            p1 = p1.divide(dec);
                            //p1 = p1.multiply(dec);
                        }
                    }
                    var p0d = p0;//new BigDecimal(p0);
                    var p1d = p1;//new BigDecimal(p1);
                    tokenPriceUSD = p0d.divide(p1d, 9, RoundingMode.FLOOR);
                    //System.out.println("WTF: Case2: " + tokenPriceUSD);
                    tokenPriceUSD = tokenPriceUSD.multiply(quotationPriceUSD).setScale(9, RoundingMode.FLOOR);
                    quotationReserveUSD = p0d.divide(liquidityDecimal, 6, RoundingMode.FLOOR).multiply(quotationPriceUSD).setScale(6, RoundingMode.FLOOR);
                };
            }
        }

        public long getBlockId() {
            return blockId;
        }

        @Override
        protected void process(Pair.SyncEventResponse syncEventResponse) {
            if (syncEventResponse.blockId.longValue() >= blockId) {
                if (!concurrentQueue.isEmpty()) {
                    transactions++;
                    skipped++;
                    return;
                }
            } else {
                System.out.println("Wrong block id, currentId " + blockId + ", eventBlockId: " + syncEventResponse.log.getBlockNumber());
                return;
            }
            //System.out.println(Thread.currentThread().getId() + " / " + Thread.currentThread().getName());
            received = new Timestamp(System.currentTimeMillis());
            quotationPriceUSD = priceFunction.getPrice();
            tokenLiquidityFunction.accept(syncEventResponse);
            blockId = syncEventResponse.blockId.longValue();
            emitted = blockTimestampCache.getBlockTimestamp(blockId);
            eventLag = (int) (received.getTime() - emitted.getTime());
            transactions++;
            updateTokenPriceUSD();
            updateTokenLiquidityUSD();
            processTime = (int) (System.currentTimeMillis() - start);
            LOG.info("Process Block {}, {} -> {}, time {} ms, trx {}, skipped {}",
                    blockId, token.getSymbol(), dex, processTime, transactions, skipped);
            skipped = 0;
        }

        @Override
        public void open() {
            long lastBlock = -50;
//            BigInteger dec = dex.equals("QuickSwap") ? SingletonPairs.decimalsPoly : SingletonPairs.decimalsBsc;
//            Web3j web3jIn = null;
//            if(dex.equals("QuickSwap")) {
//                web3jIn = Connector.connectionManager.getPolygonWs();
//            } else {
//                web3jIn = Connector.connectionManager.getBSCWs();
//            }
            final long fromBlock = pairInfo.getBlockId();
            created = blockTimestampCache.getBlockTimestamp(fromBlock);
            emitted = created;
            Pair watchedPair = Pair.load(pairInfo.getPairAddress(), web3j, Wallet.getWallet(), SingletonPairs.gasProvider);

            DefaultBlockParameter startBlock = new DefaultBlockParameterNumber(fromBlock);
            DefaultBlockParameter endBlock = DefaultBlockParameterName.LATEST;

            disposable = useWebSocket(watchedPair, startBlock, endBlock);

//            var flowableEvent = watchedPair.syncEventFlowable(startBlock, endBlock);
//            disposable = flowableEvent.subscribe(onNext -> {
//                onEvent(onNext);
//            }, onError -> {
//                onError.printStackTrace();
//                System.err.println(this.toString() + " / " + pairInfo);
//                //reopen(disposable, this);
//            }, () -> {
//            }, onSubscribe -> {
//                onSubscribe.request(fromBlock);
//            });
            super.open();
            AppManager.add(disposable);
        }

        @NotNull
        private Disposable useWebSocket(Pair watchedPair, DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
            Web3j web3jIn = null;
            if (dex.equals("QuickSwap")) {
                web3jIn = Connector.connectionManager.getPolygon().getBackupConnection();
            } else {
                web3jIn = Connector.connectionManager.getBsc().getBackupConnection();
            }
            List<EthLog.LogResult> logResults = null;
            try {
                logResults = web3jIn.ethGetLogs(new EthFilter(startBlock, endBlock, watchedPair.getContractAddress())).send().getResult();
            } catch (IOException e) {
                LOG.error("", e);
            }
            int failedNotifications = 0;
            if (logResults != null) {
                for (var logResult : logResults) {
                    Log log = (Log) logResult.get();
                    if (log != null && !log.isRemoved()) {
                        try {
                            var event = watchedPair.getSyncEventResponse(log);
                            this.onEvent(event);
                        } catch (Exception e) {
                            failedNotifications++;
                        }
                    }
                }
                LOG.info("Successfully processed {} Sync notifications, failed to process {}", logResults.size() - failedNotifications, failedNotifications);
            } else {
                LOG.warn("Requesting previous Sync notification failed, null log results");
            }

            var disposable = subscribeSync(watchedPair, null);
            return disposable;
        }

        @NotNull
        private Disposable subscribeSync(Pair watchedPair, Disposable disposable) {
            final Disposable newDisposable[] = {null};
            if (disposable != null) AppManager.unTrack(disposable);
            newDisposable[0] = web3j.logsNotifications(Arrays.asList(new String[]{watchedPair.getContractAddress()}),
                            Arrays.asList(EventEncoder.encode(Pair.SYNC_EVENT)))
                    .subscribe(onNext -> {
                        //LOG.info("onSync");
                        AsyncTaskManager.asyncTaskManager.execute(() -> {
                            var pairEvent = watchedPair.readLog(onNext.getParams().getResult());
                            this.onEvent(pairEvent);
                        });
                    }, onError -> {
                        onError.printStackTrace();
                        AppManager.unTrack(newDisposable[0]);
                        newDisposable [0] = subscribeSync(watchedPair, newDisposable[0]);
                        AppManager.add(newDisposable[0]);
                    }/*, () -> {
                    }, onSubscribe -> {
                        LOG.info("Successfully subscribed to Sync Notifications.");
                    }*/);

            if (disposable != null) AppManager.add(newDisposable[0]);
            return newDisposable[0];
        }


        @Override
        public String toString() {
            return "PairDataListener{\n" +
                    "  blockNumber=" + blockId +
                    ", basePriceUSD=" + quotationPriceUSD +
                    ", tokenPriceUSD=" + tokenPriceUSD +
                    ", tokenReserveUSD=" + quotationReserveUSD +
                    "\n}";
        }
    }

}
