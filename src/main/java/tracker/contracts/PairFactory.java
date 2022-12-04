package tracker.contracts;

import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.ens.EnsResolver;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.utils.Numeric;
import tracker.contracts.bsc.PancakeSwapV2Factory;
import tracker.utils.PairBase;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PairFactory extends Contract {

    public static final Event PAIRCREATED_EVENT = new Event("PairCreated",
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Address>() {}, new TypeReference<Uint256>() {}));

    protected PairFactory(String contractBinary, String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider gasProvider) {
        super(contractBinary, contractAddress, web3j, credentials, gasProvider);
    }

    public PairCreatedEventResponse getPairCreatedEventResponse(Log log) {
        EventValuesWithLog eventValues = extractEventParametersWithLog(PAIRCREATED_EVENT, log);
        PairCreatedEventResponse typedResponse = new PancakeSwapV2Factory.PairCreatedEventResponse();
        typedResponse.log = log;
        typedResponse.token0 = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.token1 = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.pair = (String) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.pairLength = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.blockId = log.getBlockNumber().longValue();
        return typedResponse;
    }

    public PairCreatedEventResponse readLog(org.web3j.protocol.websocket.events.Log log) {
        EventValues eventValues = extractEventParametersWSWithLog(PAIRCREATED_EVENT, log);
        PairCreatedEventResponse typedResponse = new PairCreatedEventResponse();
        typedResponse.token0 = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.token1 = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.pair = (String) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.pairLength = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.blockId = Numeric.decodeQuantity(log.getBlockNumber()).longValue();
        return typedResponse;
    }

    protected EventValues extractEventParametersWSWithLog(Event event, org.web3j.protocol.websocket.events.Log log) {
        EventValues eventValues = null;
        List<String> topics = log.getTopics();
        String encodedEventSignature = EventEncoder.encode(event);
        if (topics != null && topics.size() != 0 && ((String)topics.get(0)).equals(encodedEventSignature)) {
            List<Type> indexedValues = new ArrayList();
            List<Type> nonIndexedValues = FunctionReturnDecoder.decode(log.getData(), event.getNonIndexedParameters());
            List<TypeReference<Type>> indexedParameters = event.getIndexedParameters();

            for(int i = 0; i < indexedParameters.size(); ++i) {
                Type value = FunctionReturnDecoder.decodeIndexedValue((String)topics.get(i + 1), (TypeReference)indexedParameters.get(i));
                indexedValues.add(value);
            }

            return new EventValues(indexedValues, nonIndexedValues);
        } else {
            return null;
        }
    }

    public static class PairCreatedEventResponse extends PairBase {
    }
}
