package tracker;

import org.web3j.protocol.Web3j;
import tracker.core.BlockingEventWithExecutorService;
import tracker.core.EventListener;
import tracker.data.TokenData;
import tracker.utils.ContractCreatorResolver;
import tracker.data.DataWriter;
import tracker.utils.PairResolver;
import tracker.utils.cache.BlockTimestamp;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static tracker.utils.PairResolver.*;

public class PairValidator extends BlockingEventWithExecutorService<Future<PairInfo>> {

    private final Map<PairResolver.TokenInfo, Set<PairInfo>> tokens;
    private final Map<String, TokenData> tokenListener;
    private final Web3j web3j;
    private final EventListener contractCreatorResolver;
    public static final Set<String> whiteListed = new HashSet<>();

    static {
        //bsc
        whiteListed.add("0xbb4cdb9cbd36b01bd1cbaebf2de08d9173bc095c");
        //whiteListed.add("0xe9e7cea3dedca5984780bafc599bd69add087d56");

        //polygon
        whiteListed.add("0x0d500b1d8e8ef31e21c99d1db9a6444d3adf1270");
    }

    public PairValidator(String eventName, int poolSize, Web3j web3j, ContractCreatorResolver contractCreatorResolver) {
        super(eventName, poolSize);
        this.tokens = new ConcurrentHashMap<>(200, 1, 1);
        this.tokenListener = new ConcurrentHashMap<>(200, 1, 1);
        this.web3j = web3j;
        this.contractCreatorResolver = contractCreatorResolver;
    }

    @Override
    protected void process(Future<PairInfo> pairInfoFuture) {
        PairInfo newPair = null;
        try {
            newPair = pairInfoFuture.get();
        } catch (InterruptedException e) {
            LOG.error("InterruptedException: ", e);
        } catch (ExecutionException e) {
            LOG.error("ExecutionException: ", e);
        }

        //valid result
        if(newPair == null) {
            System.err.println("Error resolving pair name/symbol/decimals: Null Result");
            return;
        }

        //both tokens with different address
        if(newPair.getToken0().equals(newPair.getToken1())){
            System.err.println("Invalid Pair: " + newPair);
            return;
        }

        //find the token address
        TokenInfo token0 = newPair.getToken0();
        TokenInfo token1 = newPair.getToken1();

        if(token0.getDecimals() == 0 || token1.getDecimals() == 0) {
            System.err.println("Unsupported decimal 0: " + newPair);
            return;
        }

        final TokenInfo[] newToken = {token0};
        Set pairSetInner = null;
        //If new token is token0
        boolean whiteListedTokenIsOne = false;
        //one token must be within the whitelisted tokens
        if(whiteListed.contains(token0.getAddress())) {
            pairSetInner = tokens.computeIfAbsent(newPair.getToken1(), key -> new HashSet<>(1, 1));
            newToken[0] = token1;
        } else if (whiteListed.contains(token1.getAddress())) {
            pairSetInner = tokens.computeIfAbsent(newPair.getToken0(), key -> new HashSet<>(1, 1));
            whiteListedTokenIsOne = true;
            newToken[0] = token0;
        } else {
            System.err.println("Unknown Base Token, both tokens are not whitelisted: " + newPair);
            return;
        }

        //the pair already exists in the list of know pairs
        if(pairSetInner.contains(newPair)) {
            System.err.println("Pair already exists: " + newPair);
            return;
        }

        pairSetInner.add(newPair);
        final String dexName = newPair.getDexName();
        BlockTimestamp blockTimestampCache = dexName.equals("QuickSwap") ? Connector.polygon: Connector.bsc;
        DataWriter dataWriter = dexName.equals("QuickSwap") ? DataWriter.polygonDW : DataWriter.bscDW;
        TokenData tokenData = tokenListener.computeIfAbsent(newToken[0].getAddress(), key-> new TokenData(web3j, newToken[0], dexName, blockTimestampCache, dataWriter));
        contractCreatorResolver.onEvent(tokenData);
        LOG.info("Validated pair, submitting new Sync event listener");
        submitTask(tokenData, newPair, whiteListedTokenIsOne);
    }

    public boolean isWhiteListed(String address) {
        return whiteListed.contains(address);
    }

    public int getTokensSize(){
        return tokenListener.size();
    }

    private void submitTask(TokenData tokenData, PairInfo newPair, boolean whiteListedTokenIsOne) {
        executorService.submit(() -> {
            try {
                tokenData.onAddPair(newPair, whiteListedTokenIsOne);
            } catch (Throwable throwable) {
                LOG.error("Error trying to submit sync event", throwable);
            }
        });
    }
}
