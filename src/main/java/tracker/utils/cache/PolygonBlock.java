package tracker.utils.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tracker.utils.connection.Web3jConnection;

public class PolygonBlock extends BlockTimestamp {

    private static final Logger LOG = LoggerFactory.getLogger(PolygonBlock.class);

    public PolygonBlock(String name, Web3jConnection web3jConnection) {
        super(name, web3jConnection, LOG);
    }
}
