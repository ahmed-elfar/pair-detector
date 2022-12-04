package tracker.utils;

import org.web3j.protocol.core.methods.response.BaseEventResponse;

import java.math.BigInteger;

public class PairBase extends BaseEventResponse {
    public String dexName = "";

    public String token0;

    public String token1;

    public String pair;

    public BigInteger pairLength;

    public long blockId;

    @Override
    public String toString() {
        return "PairCreatedEventResponse {" +
                "token0='" + token0 + '\'' +
                ", token1='" + token1 + '\'' +
                ", pair='" + pair + '\'' +
                ", blockId=" + blockId +
                '}';
    }
}
