package tracker.data.pricing;

import org.web3j.protocol.Web3j;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class WBnbUSD extends BaseTokenPrice {

    public final static String address = "0x16b9a82891338f9ba80e2d6970fdda79d1eb0dae";

    public WBnbUSD(Web3j web3j) {
        super(address, web3j);
    }

    @Override
    void priceRequest() throws Exception {
        var rawResult = pair.getReserves().send();
        var reserve0 = rawResult.component1();
        var reserve1 = rawResult.component2();
        price = new BigDecimal(reserve0).divide(new BigDecimal(reserve1), 9, RoundingMode.FLOOR);
    }
}
