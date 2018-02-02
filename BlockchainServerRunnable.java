import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;

public class BlockchainServerRunnable implements Runnable{

    private Socket clientSocket;
    private Blockchain blockchain;
    private HashMap<ServerInfo, Date> serverStatus;

    public BlockchainServerRunnable(Socket clientSocket, Blockchain blockchain, HashMap<ServerInfo, Date> serverStatus) {
        this.clientSocket = clientSocket;
        this.blockchain = blockchain;
        this.serverStatus = serverStatus;
    }

    public void run() {
        try {
            serverHandler(clientSocket.getInputStream(), clientSocket.getOutputStream());
            clientSocket.close();
        } catch (IOException e) {
        }
    }

    public void serverHandler(InputStream clientInputStream, OutputStream clientOutputStream) {

        String localIP = (((InetSocketAddress) clientSocket.getLocalSocketAddress()).getAddress()).toString().replace("/", "");
        String remoteIP = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");
        int localPort = clientSocket.getLocalPort();

        BufferedReader inputReader = new BufferedReader(
                new InputStreamReader(clientInputStream));
        PrintWriter outWriter = new PrintWriter(clientOutputStream, true);

        try {
            while (true) {
                String inputLine = inputReader.readLine();
                if (inputLine == null) {
                    break;
                }

                String[] tokens = inputLine.split("\\|");
                switch (tokens[0]) {
                    case "tx":
                        if (blockchain.addTransaction(inputLine))
                            outWriter.print("Accepted\n\n");
                        else
                            outWriter.print("Rejected\n\n");
                        outWriter.flush();
                        System.out.println("get!");
                        break;
                    case "pb":
                        outWriter.print(blockchain.toString() + "\n");
                        outWriter.flush();
                        break;
                    case "cc":
                        return;
                    case "hb":
                        serverStatus.put(new ServerInfo(remoteIP, Integer.parseInt(tokens[1])), new Date());
                        if (tokens[2].equals("0")) {
                            ArrayList<Thread> threadArrayList = new ArrayList<>();
                            for (ServerInfo ip : serverStatus.keySet()) {
                                if ((ip.getHost().equals(remoteIP)&&ip.getPort() == Integer.parseInt(tokens[1])) || (ip.getHost().equals(localIP)&&ip.getPort() == localPort)) {
                                    continue;
                                }
                                Thread thread = new Thread(new HeartBeatClientRunnable(ip,"si|"+localPort+"|"+remoteIP+"|"+Integer.parseInt(tokens[1])));
                                threadArrayList.add(thread);
                                thread.start();
                            }
                            for (Thread thread : threadArrayList) {
                                thread.join();
                            }
                        }
                        break;
                    case "si":
                        boolean known = false;
                        for(ServerInfo ip : serverStatus.keySet()){
                            if(ip.getHost().equals(tokens[2])&&ip.getPort() == (Integer.parseInt(tokens[3]))){
                                known = true;
                            }
                        }
                        if(!known){
                            ArrayList<Thread> threadArrayList = new ArrayList<>();
                            for (ServerInfo ip : serverStatus.keySet()) {
                                if ((ip.getHost().equals(remoteIP)&&ip.getPort() == Integer.parseInt(tokens[1])) || (ip.getHost().equals(localIP)&&ip.getPort() == localPort)) {
                                    continue;
                                }
                                Thread thread = new Thread(new HeartBeatClientRunnable(ip,"si|"+localPort+"|"+tokens[2]+"|"+tokens[3]));
                                threadArrayList.add(thread);
                                thread.start();
                            }
                            for (Thread thread : threadArrayList) {
                                thread.join();
                            }
                            serverStatus.put(new ServerInfo(tokens[2], Integer.parseInt(tokens[3])), new Date());
                        }
                        break;
                    case "lb":
                        int length = Integer.parseInt(tokens[2]);
                        byte [] barr = Base64.getDecoder().decode(tokens[3]);
                        if(blockchain.getLength() < length){
                            System.out.println("Local:" + blockchain.getLength());
                            System.out.println("Remote:" + length);
                            Thread cu = new Thread(new CatchUpRRunnable(new ServerInfo(remoteIP, Integer.parseInt(tokens[1])) ,blockchain, length - blockchain.getLength()));
                            cu.start();
                            cu.join();
                        }
                        else if(blockchain.getLength() == length){
                            boolean small = false;
                            if(blockchain.getHead() != null){
                                for(int i = 0; i < barr.length; i ++){
                                    if(barr[i] < blockchain.getHead().calculateHash()[i]){
                                        small = true;
                                        break;
                                    }
                                }
                            }
                            if(small){
                                Thread cu = new Thread(new CatchUpRRunnable(new ServerInfo(remoteIP, Integer.parseInt(tokens[1])) ,blockchain, 0));
                                cu.start();
                                cu.join();
                            }
                        }

                        return;
                    case "cu":

                        ObjectOutputStream oos = new ObjectOutputStream(clientOutputStream);
                        if(tokens.length == 1){
                            oos.writeObject(blockchain.getHead());
                            oos.flush();
                        }
                        else{
                            String hash = tokens[1];
                            Block toThrow = blockchain.getHead().getPreviousBlock();

                            while(toThrow != null){
                                String comp = Base64.getEncoder().encodeToString(toThrow.calculateHash());
                                if(comp.equals(hash)){

                                    oos.writeObject(toThrow);

                                    oos.flush();

                                    return;
                                }
                                toThrow = toThrow.getPreviousBlock();
                            }
                        }
                        return;
                    default:
                        outWriter.print("Error\n\n");
                        outWriter.flush();
                }
            }
        } catch (IOException e) {
        } catch (InterruptedException e){

        }
    }
}