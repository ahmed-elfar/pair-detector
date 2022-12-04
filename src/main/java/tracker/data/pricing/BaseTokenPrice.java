package tracker.data.pricing;

import org.web3j.protocol.Web3j;
import tracker.Wallet;
import tracker.contracts.Pair;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

import static tracker.data.SingletonPairs.gasProvider;

public abstract class BaseTokenPrice {

    protected final Pair pair;
    protected volatile long lastUpdated;
    protected volatile BigDecimal price;
    protected final AtomicBoolean updating = new AtomicBoolean();

    private static final long EXPIRATION_TIME = 3000;

    protected BaseTokenPrice(String address, Web3j web3j) {
        this.pair = Pair.load(address, web3j, Wallet.getWallet(), gasProvider);
        price = BigDecimal.ONE;
        lastUpdated = System.currentTimeMillis();
    }

    public BigDecimal getPrice() {
        if(System.currentTimeMillis() - lastUpdated <= EXPIRATION_TIME)  {
            return price;
        }
        if(updating.compareAndSet(false, true)) {
            try {
                priceRequest();
                lastUpdated = System.currentTimeMillis();
                return price;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                updating.set(false);
            }
        } else {
            boolean locked = true;
            while (locked){
                locked = updating.get();
            }
        }
        return price;
    }

    abstract void priceRequest() throws Exception;
}
