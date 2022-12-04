package tracker;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketClient;
import org.web3j.protocol.websocket.WebSocketService;
import tracker.core.IsProcess;
import tracker.utils.AppManager;
import tracker.utils.connection.Web3jConnection;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConnectionManager extends Thread implements IsProcess {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionManager.class);
    private static final long WATCH_INTERVAL = 2000;
    private static final long REPORT_INTERVAL = 120000;
    private final List<String> networksHttp;
    private final List<String> networksWs;
    private final Map<String, Web3j> web3HttpMap;
    private final Map<String, Web3j> web3WsMap;

    private OkHttpClient okHttpClient;
    private Dispatcher dispatcher;
    private ConnectionPool connectionPool;
    private final List<WebSocketClient> webSocketClients;
    private final List<WebSocketService> webSocketServices;

    private final Web3jConnection polygon;
    private final Web3jConnection bsc;

    ConnectionManager(List<String> networksHttp, List<String> networksWs) {
        super("ConnectionManager-Monitor-Thread");
        this.web3HttpMap = new HashMap<>(networksHttp.size(),1);
        this.web3WsMap = new HashMap<>(networksWs.size(),1);
        this.networksHttp = networksHttp;
        this.networksWs = networksWs;
        webSocketClients = new ArrayList<>(networksWs.size());
        webSocketServices = new ArrayList<>(networksWs.size());
        polygon = new Web3jConnection(null, null);
        bsc = new Web3jConnection(null, null);
    }

    void setBackUpConnections(List<String> networksHttpBackups) {
        this.networksHttp.addAll(networksHttpBackups);
    }

    private OkHttpClient getOkHttpClient() {
        int max = Runtime.getRuntime().availableProcessors() * 10;
        ExecutorService executorService = Executors.newFixedThreadPool(max);
        dispatcher = new Dispatcher(executorService);
        dispatcher.setMaxRequests(max * 2);
        dispatcher.setMaxRequestsPerHost(max);
        connectionPool = new ConnectionPool(128, Integer.MAX_VALUE, TimeUnit.MINUTES);
        return HttpService.getOkHttpClientBuilder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public Web3j getBSCHttp(){
        return web3HttpMap.get(Connector.BSC_HTTP);
    }

    public Web3j getPolygonHttp(){
        return web3HttpMap.get(Connector.POLYGON_HTTP);
    }

    public Web3j getBSCWs(){
        return web3WsMap.get(Connector.BSC_WS);
    }

    public Web3j getPolygonWs(){
        return web3WsMap.get(Connector.POLYGON_WS);
    }

    public Web3jConnection getPolygon() {
        return polygon;
    }

    public Web3jConnection getBsc() {
        return bsc;
    }

    private Web3j getOrCreateHttpWeb3j(String rpcURL) throws ExecutionException, InterruptedException {
        Web3j web3j = web3HttpMap.get(rpcURL);
        if(web3j != null) return web3j;
        web3j = Web3j.build(new HttpService(rpcURL, okHttpClient));
        web3HttpMap.put(rpcURL, web3j);
        Web3ClientVersion web3ClientVersion = web3j.web3ClientVersion().sendAsync().get();
        LOG.info("HTTP RPC={}\n, Web3ClientVersion: {}\n, Connections: {}\n, Idle Connections: {}\n",
                rpcURL,
                web3ClientVersion.getWeb3ClientVersion(),
                okHttpClient.connectionPool().connectionCount(),
                okHttpClient.connectionPool().idleConnectionCount());
        //okHttpClient.connectionSpecs().forEach(System.out::println);
        return web3j;
    }

    private Web3j getOrCreateWsWeb3j(String rpcURL) throws IOException, URISyntaxException, InterruptedException {
        Web3j web3j = web3WsMap.get(rpcURL);
        if(web3j != null) return web3j;
        WebSocketClient webSocketClient = new WebSocketClient(new URI(rpcURL));
//        webSocketClient.setConnectionLostTimeout(Integer.MAX_VALUE);
        webSocketClients.add(webSocketClient);
        WebSocketService webSocketService = new WebSocketService(webSocketClient, false);
        webSocketService.connect();
        webSocketServices.add(webSocketService);
        web3j = Web3j.build(webSocketService);
        web3WsMap.put(rpcURL, web3j);
        Web3ClientVersion web3ClientVersion = web3j.web3ClientVersion().send();
        LOG.info("WS RPC={}\n, Web3ClientVersion: {}\n, IsConnected: {}\n",
                rpcURL,
                web3ClientVersion.getWeb3ClientVersion(),
                webSocketClient.isOpen());
        return web3j;
    }


    @Override
    public void open() {
        okHttpClient = getOkHttpClient();
        try {
            for (String rpcHttp : networksHttp) {
                getOrCreateHttpWeb3j(rpcHttp);
            }
            for (String rpcWs : networksWs) {
                getOrCreateWsWeb3j(rpcWs);
            }
            polygon.setMainConnection(getOrCreateHttpWeb3j(Connector.POLYGON_HTTP));
            bsc.setMainConnection(getOrCreateHttpWeb3j(Connector.BSC_HTTP));
            if(networksHttp.size() > 2) {
                polygon.setBackupConnection(getOrCreateHttpWeb3j(Connector.POLYGON_HTTP_BU));
                bsc.setBackupConnection(getOrCreateHttpWeb3j(Connector.BSC_HTTP_BU));
            }
        } catch (Exception e) {
            LOG.error("Error creating connection to network: ", e);
        }
        this.start();
        AppManager.add(this);
    }

    @Override
    public void close() {
        LOG.info("ConnectionManager -> Closing Connections");
        this.interrupt();
        web3HttpMap.values().forEach(Web3j::shutdown);
        web3WsMap.values().forEach(Web3j::shutdown);
        webSocketClients.forEach(webSocketClient -> {
            if (webSocketClient.isOpen()) webSocketClient.close();
        });
        webSocketServices.forEach(webSocketService -> {
            webSocketService.close();
        });
        dispatcher.cancelAll();
        dispatcher.executorService().shutdownNow();
        connectionPool.evictAll();
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void run() {
        int reportAfter = 0;
//        try {
//            Thread.sleep(WATCH_INTERVAL * 10);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        for (;;) {
            try {
                Thread.sleep(WATCH_INTERVAL);
            } catch (InterruptedException e) {
                LOG.warn("ConnectionManager monitor thread interrupted");
                return;
            }
            for (int i = 0; i < webSocketClients.size(); i++) {
                var wc = webSocketClients.get(i);
                if(!wc.isConnecting() && wc.isClosed()) {
                    try {
                        LOG.warn("Reconnecting : {}, connection = {}", wc, wc.getConnection().getResourceDescriptor());
                        //webSocketServices.get(i).connect();
                        wc.reconnect();
                    } catch (Exception e) {
                        LOG.error("Failed to reconnect webSocket : ", e);
                    }
                }
            }
//            webSocketClients.forEach(webSocketClient -> {
//                if(!webSocketClient.isConnecting() && webSocketClient.isClosed()) {
//                    webSocketClient.reconnect();
//                    LOG.warn("Reconnecting : {}, connection = {}", webSocketClient, webSocketClient.getConnection());
//                }
//            });
            reportAfter += WATCH_INTERVAL;
            if(reportAfter < REPORT_INTERVAL) continue;
            reportAfter = 0;
            StringBuilder report = new StringBuilder();
            if(okHttpClient != null){
                report.append("{")
                        .append(System.lineSeparator())
                        .append("    Connections ")
                        .append(okHttpClient.connectionPool().connectionCount())
                        .append(" / ")
                        .append(" Idle ")
                        .append(okHttpClient.connectionPool().idleConnectionCount())
                        .append(System.lineSeparator());
            }

            webSocketClients.forEach(webSocketClient -> {
                report.append("    Socket isOpen ")
                        .append(webSocketClient.isOpen())
                        .append(", ")
                        .append("isConnecting ")
                        .append(webSocketClient.isConnecting())
                        .append(System.lineSeparator());
            });
            report.append("}");
            LOG.info(report.toString());
        }
    }
}
