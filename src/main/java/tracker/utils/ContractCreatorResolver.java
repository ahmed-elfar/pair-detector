package tracker.utils;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import tracker.core.BlockingEvent;
import tracker.data.TokenData;

public class ContractCreatorResolver extends BlockingEvent<TokenData> {

    private final String networkScanUrl;
    private final static String XPATH_EXPR1 = "//a[@title='Creator Address']";
    private final static String XPATH_EXPR2 = "//a[@data-original-title='Creator Address']";
    private long sleepTime = MIN_SLEEP_TIME;
    private static final long MAX_SLEEP_TIME = 3000;
    private static final long MIN_SLEEP_TIME = 500;

    public ContractCreatorResolver(String eventName, String networkScanUrl) {
        super(eventName);
        this.networkScanUrl = networkScanUrl;
    }

    @Override
    protected void process(TokenData tokenData) {
        //valid result
        if (tokenData == null) {
            System.err.println("Error resolving Contract Creator: Null Result");
            return;
        }
        String address = getContractCreator(networkScanUrl, tokenData.getToken().getAddress(), tokenData);
        tokenData.setContractCreator(address);
    }

    private String getContractCreator(String url, String address, TokenData tokenData) {
        String addressUrl = url + address;
        try (WebClient webClient = new WebClient(BrowserVersion.CHROME)) {
            webClient.getOptions().setCssEnabled(false);
            webClient.getOptions().setJavaScriptEnabled(false);
            webClient.getOptions().setThrowExceptionOnScriptError(false);
            webClient.getOptions().setDownloadImages(false);
            HtmlPage htmlPage = webClient.getPage(addressUrl);
            Thread.sleep(sleepTime);
            //System.out.println(htmlPage.isBeingParsed() + " / " + htmlPage.getReadyState());
            HtmlAnchor anchor = htmlPage.getFirstByXPath(XPATH_EXPR1);
            if (anchor == null) anchor = htmlPage.getFirstByXPath(XPATH_EXPR2);
            if (anchor == null) {
                LOG.warn("Unable to find href for Contract creator for {} - Queue size: {}, page loading time {}", addressUrl, blockingQueue.size(), sleepTime);
                if(sleepTime < MAX_SLEEP_TIME) sleepTime = sleepTime * 2;
                else sleepTime = MAX_SLEEP_TIME;
            } else {
                sleepTime = MIN_SLEEP_TIME;
                return anchor.getFirstChild().getTextContent();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if(sleepTime < MAX_SLEEP_TIME) sleepTime = sleepTime * 2;
            else sleepTime = MAX_SLEEP_TIME;
            LOG.error("Unable to find href for Contract creator for {} - Queue size: {}, page loading time {}", addressUrl, blockingQueue.size(), sleepTime);
        }
        onEvent(tokenData);
        return TokenData.CREATOR_TEMP;
    }
}
