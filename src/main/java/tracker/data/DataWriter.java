package tracker.data;

import tracker.core.BlockingEvent;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;

public class DataWriter extends BlockingEvent<String> {

    public static long polygonBlock = Long.MAX_VALUE;
    public static long bscBlock = Long.MAX_VALUE;;

    public final static DataWriter polygonDW = new DataWriter("Polygon");
    public final static DataWriter bscDW = new DataWriter("BSC");

    static {
        polygonDW.open();
        bscDW.open();
    }

    private FileWriter fileWriter;

    public DataWriter(String network) {
        super("DataWriter-" + network);
        try {
            long timestamp = System.currentTimeMillis();
            String fileName = String.format("workspace/jsonData/%s_%d.json", network, timestamp);
            fileWriter = new FileWriter(fileName, Charset.forName("UTF-8"), true);
            fileWriter.write("[");
            fileWriter.write(new TokenData(null, null, "", null, null).toJson());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void process(String String) {
        try {
            fileWriter.write("," + String);
            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        while(!blockingQueue.isEmpty()){}

        super.close();
        try {
            fileWriter.write("]");
            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
