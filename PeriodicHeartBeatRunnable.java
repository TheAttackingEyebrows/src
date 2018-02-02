import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class PeriodicHeartBeatRunnable implements Runnable {

    private HashMap<ServerInfo, Date> serverStatus;
    private int sequenceNumber;
    private int portNum;

    public PeriodicHeartBeatRunnable(HashMap<ServerInfo, Date> serverStatus, int portNum) {
        this.serverStatus = serverStatus;
        this.sequenceNumber = 0;
        this. portNum = portNum;
    }

    @Override
    public void run() {
        while(true) {
            // broadcast HeartBeat message to all peers
            ArrayList<Thread> threadArrayList = new ArrayList<>();
            for (ServerInfo ip : serverStatus.keySet()) {
                String message = "hb|" + portNum + "|" + Integer.toString(sequenceNumber);
                Thread thread = new Thread(new HeartBeatClientRunnable(ip, message));
                threadArrayList.add(thread);
                thread.start();
            }

            for (Thread thread : threadArrayList) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                }
            }

            // increment the sequenceNumber
            sequenceNumber += 1;

            // sleep for two seconds
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
        }
    }
}
