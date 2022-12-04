package tracker.contracts.bsc;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.utils.Numeric;
import tracker.contracts.PairFactory;
import tracker.utils.PairBase;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 1.4.1.
 */
@SuppressWarnings("rawtypes")
public class PancakeSwapV2Factory extends PairFactory {
    public static final String ADDRESS = "0xcA143Ce32Fe78f1f7019d7d551a6402fC5350c73";

    public static final String BINARY = "Bin file was not provided";

    public static final String FUNC_INIT_CODE_PAIR_HASH = "INIT_CODE_PAIR_HASH";

    public static final String FUNC_ALLPAIRS = "allPairs";

    public static final String FUNC_ALLPAIRSLENGTH = "allPairsLength";

    public static final String FUNC_CREATEPAIR = "createPair";

    public static final String FUNC_FEETO = "feeTo";

    public static final String FUNC_FEETOSETTER = "feeToSetter";

    public static final String FUNC_GETPAIR = "getPair";

    public static final String FUNC_SETFEETO = "setFeeTo";

    public static final String FUNC_SETFEETOSETTER = "setFeeToSetter";


//    public static final Event PAIRCREATED_EVENT = new Event("PairCreated",
//            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Address>() {}, new TypeReference<Uint256>() {}));
//    ;

//    protected PancakeSwapV2Factory(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
//        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
//    }

    protected PancakeSwapV2Factory(String contractBinary, String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider gasProvider) {
        super(contractBinary, contractAddress, web3j, credentials, gasProvider);
    }

    public static PancakeSwapV2Factory load(String address, Web3j web3j, Credentials wallet, ContractGasProvider gasProvider) {
        return new PancakeSwapV2Factory(BINARY, address, web3j, wallet, gasProvider);
    }

    public List<PairCreatedEventResponse> getPairCreatedEvents(TransactionReceipt transactionReceipt) {
        List<EventValuesWithLog> valueList = extractEventParametersWithLog(PAIRCREATED_EVENT, transactionReceipt);
        ArrayList<PairCreatedEventResponse> responses = new ArrayList<PairCreatedEventResponse>(valueList.size());
        for (EventValuesWithLog eventValues : valueList) {
            PairCreatedEventResponse typedResponse = new PairCreatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.token0 = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.token1 = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.pair = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.pairLength = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<PairCreatedEventResponse> pairCreatedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, PairCreatedEventResponse>() {
            @Override
            public PairCreatedEventResponse apply(Log log) {
                return getPairCreatedEventResponse(log);
            }
        });
    }

    @NotNull
    public PairCreatedEventResponse getPairCreatedEventResponse(Log log) {
        EventValuesWithLog eventValues = extractEventParametersWithLog(PAIRCREATED_EVENT, log);
        PairCreatedEventResponse typedResponse = new PairCreatedEventResponse();
        typedResponse.log = log;
        typedResponse.token0 = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.token1 = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.pair = (String) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.pairLength = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.blockId = log.getBlockNumber().longValue();
        typedResponse.dexName = PairCreatedEventResponse.NAME;
        return typedResponse;
    }

    public PairFactory.PairCreatedEventResponse readLog(org.web3j.protocol.websocket.events.Log log) {
        PairFactory.PairCreatedEventResponse typedResponse = super.readLog(log);
        typedResponse.dexName = PairCreatedEventResponse.NAME;
        return typedResponse;
    }

    public Flowable<PairCreatedEventResponse> pairCreatedEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(PAIRCREATED_EVENT));
        return pairCreatedEventFlowable(filter);
    }

    public RemoteFunctionCall<byte[]> INIT_CODE_PAIR_HASH() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_INIT_CODE_PAIR_HASH,
                Arrays.<Type>asList(),
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {
                }));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<String> allPairs(BigInteger param0) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_ALLPAIRS,
                Arrays.<Type>asList(new Uint256(param0)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {
                }));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<BigInteger> allPairsLength() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_ALLPAIRSLENGTH,
                Arrays.<Type>asList(),
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {
                }));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> createPair(String tokenA, String tokenB) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_CREATEPAIR,
                Arrays.<Type>asList(new Address(160, tokenA),
                        new Address(160, tokenB)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<String> feeTo() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_FEETO,
                Arrays.<Type>asList(),
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {
                }));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<String> feeToSetter() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_FEETOSETTER,
                Arrays.<Type>asList(),
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {
                }));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<String> getPair(String param0, String param1) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_GETPAIR,
                Arrays.<Type>asList(new Address(160, param0),
                        new Address(160, param1)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {
                }));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<TransactionReceipt> setFeeTo(String _feeTo) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_SETFEETO,
                Arrays.<Type>asList(new Address(160, _feeTo)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> setFeeToSetter(String _feeToSetter) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_SETFEETOSETTER,
                Arrays.<Type>asList(new Address(160, _feeToSetter)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

//    public static PancakeSwapV2Factory load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
//        return new PancakeSwapV2Factory(contractAddress, web3j, transactionManager, contractGasProvider);
//    }

    public static class PairCreatedEventResponse extends PairFactory.PairCreatedEventResponse {
        private static final String NAME = "PaneCakeSwap";
    }
}
