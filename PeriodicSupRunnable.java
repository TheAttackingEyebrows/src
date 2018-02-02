import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PeriodicSupRunnable implements Runnable{
    private HashMap<ServerInfo, Date> serverStatus;
    private Blockchain blockchain;
    private int portNum;


    public PeriodicSupRunnable(HashMap<ServerInfo, Date> serverStatus, Blockchain blockchain, int portNum) {
        this.serverStatus = serverStatus;
        this.blockchain = blockchain;
        this. portNum = portNum;
    }

    @Override
    public void run() {

        while(true){
            ServerInfo[] servers = serverStatus.keySet().toArray(new ServerInfo[0]);
            if(blockchain.getHead() != null){
                if(servers.length > 5){
                    int[] indices = new int[5];
                    int index = 0;
                    while(index < 5){
                        int randomNum = ThreadLocalRandom.current().nextInt(0, servers.length + 1);
                        boolean dup = false;
                        for(int i = 0; i < index; i++){
                            if(randomNum == indices[i]){
                                dup = true;
                            }
                        }
                        if(!dup){
                            indices[index] = randomNum;
                            index ++;
                        }
                    }
                    ArrayList<Thread> threadArrayList = new ArrayList<>();
                    for (int i = 0; i < 5; i ++) {
                        String hash = Base64.getEncoder().encodeToString(blockchain.getHead().calculateHash());
                        String message = "lb|" + portNum + "|" + blockchain.getLength() + "|" + hash;
                        Thread thread = new Thread(new HeartBeatClientRunnable(servers[indices[i]], message));
                        threadArrayList.add(thread);
                        thread.start();
                    }

                    for (Thread thread : threadArrayList) {
                        try {
                            thread.join();
                        } catch (InterruptedException e) {
                        }
                    }
                }
                else{
                    ArrayList<Thread> threadArrayList = new ArrayList<>();
                    for (ServerInfo server: servers) {
                        String hash = Base64.getEncoder().encodeToString(blockchain.getHead().calculateHash());
                        String message = "lb|" + portNum + "|" + blockchain.getLength() + "|" + hash;
                        System.out.println("Sending Num:" + blockchain.getLength());
                        Thread thread = new Thread(new HeartBeatClientRunnable(server, message));
                        threadArrayList.add(thread);
                        thread.start();
                    }

                    for (Thread thread : threadArrayList) {
                        try {
                            thread.join();
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }


            try {
                Thread.sleep(2000);
            }
            catch (InterruptedException e) {
            }
        }
    }
}
