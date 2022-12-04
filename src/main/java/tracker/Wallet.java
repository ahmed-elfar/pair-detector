package tracker;

import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public class Wallet {

    private static final Credentials credentials = createWallet();

    private static Credentials createWallet() {
        URL walletPath = Connector.class.getClassLoader().
                getResource("wallet/wallet.json");
        Credentials credentials = null;
        try {
            credentials = WalletUtils.loadCredentials(
                    "",
                    new File(walletPath.toURI()));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CipherException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return credentials;
    }


    public static Credentials getWallet() {
        return credentials;
    }

}
