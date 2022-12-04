package tracker;

import io.reactivex.disposables.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.EventEncoder;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Log;
import tracker.contracts.PairFactory;
import tracker.contracts.bsc.PancakeSwapV2Factory;
import tracker.contracts.polygon.QuickSwapV2Factory;
import org.web3j.crypto.CipherException;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.tx.gas.ContractGasProvider;
import tracker.core.AsyncTaskManager;
import tracker.data.SingletonPairs;
import tracker.utils.ContractCreatorResolver;
import tracker.utils.AppManager;
import tracker.data.DataWriter;
import tracker.utils.PairResolver;
import tracker.utils.cache.BlockTimestamp;
import tracker.utils.cache.BscBlock;
import tracker.utils.cache.PolygonBlock;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidParameterException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class Connector {

    private static final Logger LOG = LoggerFactory.getLogger(Connector.class);

    //public final static String POLYGON_HTTP = "https://rpc-mainnet.maticvigil.com";
    public final static String POLYGON_HTTP = "https://matic-mainnet.chainstacklabs.com";
    //    private final static String POLYGON_WS = "wss://ws-matic-mainnet.chainstacklabs.com";

    public final static String BSC_HTTP = "https://bsc-dataseed.binance.org/";
//    public final static String BSC_HTTP = "https://bsc-dataseed1.defibit.io/";
//    private final static String BSC_WS = "wss://bsc-ws-node.nariox.org:443";

//    private final static String POLYGON = "https://mainnet.infura.io/v3/9aa3d95b3bc440fa88ea12eaa4456161";

    public final static String POLYGON_HTTP_BU = "https://speedy-nodes-nyc.moralis.io/d888fe7a8ddad57aaa5f9c8e/polygon/mainnet";
    public final static String POLYGON_WS = "wss://speedy-nodes-nyc.moralis.io/d888fe7a8ddad57aaa5f9c8e/polygon/mainnet/ws";

    //public final static String BSC_HTTP = "https://speedy-nodes-nyc.moralis.io/d888fe7a8ddad57aaa5f9c8e/bsc/mainnet";
    //https://speedy-nodes-nyc.moralis.io/52a1e4923f30e70e89767a65/bsc/mainnet
    public final static String BSC_HTTP_BU = "https://speedy-nodes-nyc.moralis.io/d888fe7a8ddad57aaa5f9c8e/bsc/mainnet";
    public final static String BSC_WS = "wss://speedy-nodes-nyc.moralis.io/d888fe7a8ddad57aaa5f9c8e/bsc/mainnet/ws";

    public final static String POLYGON_SCAN = "https://polygonscan.com/address/";
    public final static String BSC_SCAN = "https://bscscan.com/address/";

//    static {
//
//        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
//    }

    private static final int POOL_SIZE = 2;
    public static ConnectionManager connectionManager;
    public static BlockTimestamp polygon;
    public static BlockTimestamp bsc;

    public static void main(String[] args) {
        AppManager.start();

        List<String> networksHttp = new ArrayList<>(2);
        networksHttp.add(POLYGON_HTTP);
        networksHttp.add(BSC_HTTP);
        List<String> networksWs = Arrays.asList(new String[]{POLYGON_WS, BSC_WS});
        connectionManager = new ConnectionManager(networksHttp, networksWs);
        connectionManager.setBackUpConnections(Arrays.asList(new String[]{POLYGON_HTTP_BU, BSC_HTTP_BU}));
        connectionManager.open();

        polygon = new PolygonBlock("Polygon", connectionManager.getPolygon());
        bsc = new BscBlock("BSC", connectionManager.getBsc());

        Thread polygon = new Thread(() -> {
            try {
                connectPolygon();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (CipherException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        });

        Thread bsc = new Thread(() -> {
            try {
                connectBSC();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (CipherException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        });
        polygon.start();
        bsc.start();
        LOG.info("END MAIN");
    }

    private static void connectPolygon() throws InterruptedException, ExecutionException, IOException, CipherException, URISyntaxException {
        //Web3j web3j = connectionManager.getPolygonHttp();
        Web3j web3j = connectionManager.getPolygonWs();
        if (web3j == null) throw new InvalidParameterException("NO CONNECTION");
        long blockNumber = logNetworkInfo(web3j);
        DataWriter.polygonBlock = blockNumber;
        listenToFactoryEventsP(web3j, blockNumber - 50);
    }

    private static void connectBSC() throws ExecutionException, InterruptedException, IOException, CipherException, URISyntaxException {
        //Web3j web3j = connectionManager.getOrCreateHttpWeb3j(BSC_HTTP);
        Web3j web3j = connectionManager.getBSCWs();
        if (web3j == null) throw new InvalidParameterException("NO CONNECTION");
        long blockNumber = logNetworkInfo(web3j);
        DataWriter.bscBlock = blockNumber;
        listenToFactoryEventsB(web3j, blockNumber - 50);
    }

    private static long logNetworkInfo(Web3j web3j) throws InterruptedException, ExecutionException, IOException {
        var chainId = web3j.ethChainId().sendAsync();
        StringBuilder sb = new StringBuilder();
        sb.append("NetworkInfo: {\n")
                .append(" Id: ")
                .append(chainId.get().getId())
                .append(",\n ChainId: ")
                .append(chainId.get().getChainId())
                .append(",\n JsonRpc: ")
                .append(chainId.get().getJsonrpc())
                .append(",\n Result: ")
                .append(chainId.get().getResult())
                .append(",\n RawResponse: ")
                .append(chainId.get().getRawResponse());
        long blockNumber = web3j.ethBlockNumber().send().getBlockNumber().longValue();
        sb.append(",\n CurrentBlock: ")
                .append(blockNumber)
                .append("\n}\n");
        LOG.info(sb.toString());
        return blockNumber;
    }

    private static void listenToFactoryEventsP(Web3j web3j, long fromBlock) throws CipherException, IOException, URISyntaxException {
        ContractGasProvider gasProvider = SingletonPairs.gasProvider;
        QuickSwapV2Factory contract = QuickSwapV2Factory.load(QuickSwapV2Factory.ADDRESS, web3j, Wallet.getWallet(), gasProvider);
        DefaultBlockParameter startBlock = new DefaultBlockParameterNumber(fromBlock);
        DefaultBlockParameter endBlock = DefaultBlockParameterName.LATEST;

        var contractCreatorResolver = new ContractCreatorResolver("PolygonCreatorResolver", POLYGON_SCAN);
        contractCreatorResolver.open();
        var pairValidator = new PairValidator("PolygonValidator", POOL_SIZE, web3j, contractCreatorResolver);
        pairValidator.open();
        var pairResolver = new PairResolver("QuickSwapPairs", POOL_SIZE, connectionManager.getPolygonHttp(), pairValidator, polygon);
        pairResolver.open();

        useWebSocket(web3j, fromBlock, contract, startBlock, endBlock, polygon, pairResolver);

//        var flowableEvent = contract.pairCreatedEventFlowable(blockStart, blockEnd);
//        var disposable = flowableEvent.subscribe(onNext -> {
//            pairResolver.onEvent(onNext);
//        }, onError -> {
//            onError.printStackTrace();
//        }, () -> {
//            System.out.println("Polygon Pair created onComplete");
//        }, onSubscribe -> {
//            System.out.println("onSubscribe: " + onSubscribe);
//            onSubscribe.request(fromBlock);
//        });
//        AppManager.add(disposable);
    }

    private static void listenToFactoryEventsB(Web3j web3j, long fromBlock) throws URISyntaxException, CipherException, IOException {
        ContractGasProvider gasProvider = SingletonPairs.gasProvider;
        PancakeSwapV2Factory contract = PancakeSwapV2Factory.load(PancakeSwapV2Factory.ADDRESS, web3j, Wallet.getWallet(), gasProvider);
        DefaultBlockParameter startBlock = new DefaultBlockParameterNumber(fromBlock);
        DefaultBlockParameter endBlock = DefaultBlockParameterName.LATEST;//new DefaultBlockParameterNumber(17968240);

        var contractCreatorResolver = new ContractCreatorResolver("BSCCreatorResolver", BSC_SCAN);
        contractCreatorResolver.open();
        var pairValidator = new PairValidator("BSCValidator", POOL_SIZE, web3j, contractCreatorResolver);
        pairValidator.open();
        var pairResolver = new PairResolver("PaneCakeSwapPairs", POOL_SIZE, connectionManager.getBSCHttp(), pairValidator, bsc);
        pairResolver.open();

        useWebSocket(web3j, fromBlock, contract, startBlock, endBlock, bsc, pairResolver);

//        var flowableEvent = contract.pairCreatedEventFlowable(startBlock, endBlock);
//        var disposable = flowableEvent.subscribe(onNext -> {
//            pairResolver.onEvent(onNext);
//        }, onError -> {
//            onError.printStackTrace();
//        }, () -> {
//            System.out.println("BSC Pair created onComplete");
//        }, onSubscribe -> {
//            System.out.println("onSubscribe: " + onSubscribe);
//            onSubscribe.request(fromBlock);
//        });
//        AppManager.add(disposable);
    }

    private static void useWebSocket(Web3j web3j, long fromBlock, PairFactory contract,
                                     DefaultBlockParameter startBlock, DefaultBlockParameter endBlock, BlockTimestamp blockCache, PairResolver pairResolver) throws IOException {
        var flowable = web3j.replayPastBlocksFlowable(startBlock, endBlock, false, true);
        final Disposable[] blockDisposable = new Disposable[1];
        boolean completed[] = {false};
        blockDisposable[0] = flowable.subscribe(onBlock -> {
            var block = onBlock.getBlock();
            blockCache.putBlockTimestamp(block.getNumber().longValue(), new Timestamp(TimeUnit.SECONDS.toMillis(block.getTimestamp().longValue())));
//            LOG.info("BlockNumber {}, BlockTimestamp {}, logBlooms {}", block.getNumber().longValue(),
//                    new Timestamp(TimeUnit.SECONDS.toMillis(block.getTimestamp().longValue())), block.getLogsBloom());
        }, onError -> {
            onError.printStackTrace();
        }, () -> {
            LOG.info("Finished past block processing.");
            completed[0] = true;
            blockDisposable[0] = subscribeBlock(web3j, blockCache, null);
        }, onSubscribe -> {
            LOG.info("Successfully subscribed to Block Notifications.");
            onSubscribe.request(fromBlock);
        });

        while (!completed[0]) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        AppManager.add(blockDisposable[0]);
        LOG.info("Starting Pair Notifications");
        startBlock = new DefaultBlockParameterNumber(fromBlock - 950);
        var logResults = web3j.ethGetLogs(new EthFilter(startBlock, endBlock, contract.getContractAddress())).send().getResult();
        logResults.forEach(logResult -> {
            Log log = (Log) logResult.get();
            if (log != null || !log.isRemoved()) {
                pairResolver.onEvent(contract.getPairCreatedEventResponse(log));
            }
        });
        var factoryDisposable = subscribePair(web3j, contract, pairResolver, null);
        AppManager.add(factoryDisposable);
    }

    private static Disposable subscribeBlock(Web3j web3j, BlockTimestamp blockCache, Disposable disposable) {
        final Disposable newDisposable[] = {null};
        if (disposable != null) AppManager.unTrack(disposable);
        newDisposable[0] = web3j.newHeadsNotifications().subscribe(newHead -> {
            //LOG.info("onBlock");
            AsyncTaskManager.asyncTaskManager.execute(() -> {
                var head = newHead.getParams().getResult();
                EthBlock.Block newBlock = new EthBlock.Block(head.getNumber(), null, null, null,
                        null, head.getLogsBloom(), null, null, null, null,
                        null, null, null, null, null, null, null, null,
                        head.getTimestamp(), null, null, null, null);
                var blockTs = new Timestamp(TimeUnit.SECONDS.toMillis(newBlock.getTimestamp().longValue()));
//                LOG.info("BlockNumber {}, BlockTimestamp {}, logBlooms {}", newBlock.getNumber().longValue(),
//                        blockTs, newBlock.getLogsBloom());
                blockCache.putBlockTimestamp(newBlock.getNumber().longValue(), blockTs);
                //LOG.info("caching BlockNumber {}, BlockTimestamp {}", newBlock.getNumber().longValue(), blockTs);
            });
        }, onError -> {
            LOG.error("CHECK WHICH THREAD");
            onError.printStackTrace();
            AppManager.unTrack(newDisposable[0]);
            newDisposable [0] = subscribeBlock(web3j, blockCache, newDisposable[0]);
            AppManager.add(newDisposable[0]);
        });
        if(disposable != null) AppManager.add(newDisposable[0]);
        return newDisposable[0];
    }

    private static Disposable subscribePair(Web3j web3j, PairFactory contract, PairResolver pairResolver, Disposable disposable) {
        final Disposable newDisposable[] = {null};
        if (disposable != null) AppManager.unTrack(disposable);
        newDisposable[0] = web3j.logsNotifications(Arrays.asList(new String[]{contract.getContractAddress()}),
                        Arrays.asList(EventEncoder.encode(PairFactory.PAIRCREATED_EVENT)))
                .subscribe(onNext -> {
                    LOG.info("onPair");
                    AsyncTaskManager.asyncTaskManager.execute(() -> {
                        var pairEvent = contract.readLog(onNext.getParams().getResult());
                        pairResolver.onEvent(pairEvent);
                    });
                }, onError -> {
                    onError.printStackTrace();
                    AppManager.unTrack(newDisposable[0]);
                    newDisposable [0] = subscribePair(web3j, contract, pairResolver, newDisposable[0]);
                    AppManager.add(newDisposable[0]);
                }/*, () -> {
                    System.out.println("ACTION logsNotifications");
                }, onSubscribe -> {
                    LOG.info("Successfully subscribed to Pair Notifications.");
                }*/);
        if(disposable != null) AppManager.add(newDisposable[0]);
        return newDisposable[0];
    }

}
