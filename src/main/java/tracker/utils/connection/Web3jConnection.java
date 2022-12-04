package tracker.utils.connection;

import org.web3j.protocol.Web3j;
import tracker.ConnectionManager;
import tracker.Connector;

public class Web3jConnection {

    private Web3j mainConnection;
    private Web3j backupConnection;

    public Web3jConnection(Web3j web3jMain, Web3j web3jBackup) {
        this.mainConnection = web3jMain;
        this.backupConnection = web3jBackup;
    }

    public Web3j getMainConnection() {
        return mainConnection;
    }

    public void setMainConnection(Web3j mainConnection) {
        this.mainConnection = mainConnection;
    }

    public Web3j getBackupConnection() {
        return backupConnection;
    }

    public void setBackupConnection(Web3j backupConnection) {
        this.backupConnection = backupConnection;
    }
}
