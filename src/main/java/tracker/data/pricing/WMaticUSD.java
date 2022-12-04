package tracker.data.pricing;

import org.web3j.protocol.Web3j;
import tracker.data.SingletonPairs;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class WMaticUSD extends BaseTokenPrice {

    public final static String address = "0x604229c960e5CACF2aaEAc8Be68Ac07BA9dF81c3";

    public WMaticUSD(Web3j web3j) {
        super(address, web3j);
    }

    @Override
    void priceRequest() throws Exception {
        var rawResult = pair.getReserves().send();
        var reserve0 = rawResult.component1().divide(SingletonPairs.Decimals.DEC12.value);
        var reserve1 = rawResult.component2();
        price = new BigDecimal(reserve1).divide(new BigDecimal(reserve0), 9, RoundingMode.FLOOR);
    }
}
