package tracker.data;

import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;
import tracker.Connector;
import tracker.data.pricing.BaseTokenPrice;
import tracker.data.pricing.WBnbUSD;
import tracker.data.pricing.WMaticUSD;
import tracker.utils.PairResolver;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class SingletonPairs {

    public final static ContractGasProvider gasProvider = new StaticGasProvider(BigInteger.valueOf(100000), BigInteger.valueOf(100000));

    public enum Decimals {

        DEC1(BigInteger.valueOf(10)),
        DEC2(BigInteger.valueOf(100)),
        DEC3(BigInteger.valueOf(1000)),
        DEC4(BigInteger.valueOf(10000)),
        DEC5(BigInteger.valueOf(100000)),
        DEC6(BigInteger.valueOf(1000000)),
        DEC7(BigInteger.valueOf(10000000)),
        DEC8(BigInteger.valueOf(100000000)),
        DEC9(BigInteger.valueOf(1000000000)),
        DEC10(BigInteger.valueOf(10000000000L)),
        DEC11(BigInteger.valueOf(100000000000L)),
        DEC12(BigInteger.valueOf(1000000000000L)),
        DEC13(BigInteger.valueOf(10000000000000L)),
        DEC14(BigInteger.valueOf(100000000000000L)),
        DEC15(BigInteger.valueOf(1000000000000000L)),
        DEC16(BigInteger.valueOf(10000000000000000L)),
        DEC17(BigInteger.valueOf(100000000000000000L)),
        DEC18(BigInteger.valueOf(1000000000000000000L));

        public BigInteger value;

        Decimals(BigInteger decimal) {
            this.value = decimal;
        }
    }

    static final String WMATIC = "0x0d500b1d8e8ef31e21c99d1db9a6444d3adf1270";
    static final String WBNB   = "0xbb4cdb9cbd36b01bd1cbaebf2de08d9173bc095c";

    public static final Map<String, PairResolver.TokenInfo> tokens = new HashMap<>(2, 1);
    static {
        tokens.put(WMATIC,
                PairResolver.TokenInfo.createToken(WMATIC, 18, "Wrapped Matic", "WMATIC"));
        tokens.put(WBNB,
                PairResolver.TokenInfo.createToken(WBNB, 18, "Wrapped BNB", "WBNB"));
    }

    public static final Map<String, BaseTokenPrice> price = new HashMap<>(2, 1);
    static {
        //WMATIC address
        price.put(WMATIC, new WMaticUSD(Connector.connectionManager.getPolygonHttp()));
        //WBNB address
        price.put(WBNB, new WBnbUSD(Connector.connectionManager.getBSCHttp()));
    }
//    //WMATIC-USDT
//    public final static Pair WMATIC = Pair.load("0x604229c960e5CACF2aaEAc8Be68Ac07BA9dF81c3", Connector.connectionManager.getPolygon(), Wallet.getWallet(), gasProvider);
//    //WMATIC-USDC
//    //public final static Pair WMATIC = Pair.load("0x604229c960e5CACF2aaEAc8Be68Ac07BA9dF81c3", ConnectionManager.getPolygon(), Wallet.getWallet(), gasProvider);
//    public final static BigInteger decimalsPoly = Decimals.DEC6.value;
//
//
//    //WBNB-USDT
//    public final static Pair WBNB = Pair.load("0x16b9a82891338f9ba80e2d6970fdda79d1eb0dae", Connector.connectionManager.getBSC(), Wallet.getWallet(), gasProvider);
//    public final static BigInteger decimalsBsc = Decimals.DEC18.value;
//
//    public static BigDecimal getWBNBPrice () {
//        try {
//            var rawResult = WBNB.getReserves().send();
//            var reserve0 = rawResult.component1();
//            var reserve1 = rawResult.component2();
//            var res = new BigDecimal(reserve0).divide(new BigDecimal(reserve1), 9, RoundingMode.FLOOR);
//            return res;
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    public static BigDecimal getWMATICPrice () {
//        try {
//            var rawResult = WMATIC.getReserves().send();
//            var reserve0 = rawResult.component1().divide(Decimals.DEC12.value);
//            var reserve1 = rawResult.component2();
//            var res = new BigDecimal(reserve1).divide(new BigDecimal(reserve0), 9, RoundingMode.FLOOR);
//            return res;
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return null;
//    }

//    public static BigInteger getReadablePrice(BigInteger token, BigInteger decimals) {
//            BigInteger res = token.divide(decimals);
//            res = res.add(token.mod(decimals));
//            return res;
//    }


}
