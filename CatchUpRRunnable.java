import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Base64;

public class CatchUpRRunnable implements Runnable {
    private ServerInfo ip;
    private Blockchain blockchain;
    int gap;

    public CatchUpRRunnable(ServerInfo ip, Blockchain blockchain, int gap) {
        this.ip = ip;
        this.blockchain = blockchain;
        this.gap = gap;
    }

    @Override
    public void run() {
        try {
            Socket toServer = new Socket();
            if(gap < 0){
                System.out.println("init!");
                toServer.connect(new InetSocketAddress(ip.getHost(), ip.getPort()));
                PrintWriter printWriter = new PrintWriter(toServer.getOutputStream(), true);
                printWriter.println("cu");
                printWriter.flush();
                ObjectOutputStream oos = new ObjectOutputStream(toServer.getOutputStream());
                ObjectInputStream ois = new ObjectInputStream(toServer.getInputStream());
                try {
                    Block last = (Block) ois.readObject();
                    if(last != null){
                        blockchain.setHead(last);
                        blockchain.setLength(blockchain.getLength() + 1);

                        ois.close();
                        printWriter.close();
                        toServer.close();

                        while(!Base64.getEncoder().encodeToString(last.getPreviousHash()).equals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")){
                            Socket newSocket = new Socket();

                            newSocket.connect(new InetSocketAddress(ip.getHost(), ip.getPort()));
                            printWriter = new PrintWriter(newSocket.getOutputStream(), true);
                            printWriter.println("cu|" + Base64.getEncoder().encodeToString(last.getPreviousHash()));
                            printWriter.flush();
                            oos = new ObjectOutputStream(newSocket.getOutputStream());
                            ois = new ObjectInputStream(newSocket.getInputStream());


                            Block temp = (Block) ois.readObject();

                            last.setPreviousBlock(temp);
                            blockchain.setLength(blockchain.getLength() + 1);
                            last = temp;


                            ois.close();
                            printWriter.close();
                            newSocket.close();
                        }
                    }
                }
                catch(ClassNotFoundException e){
                }


                printWriter.close();
                toServer.close();
                ois.close();
            }
            else{
                if(gap > 0){
                    System.out.println("need to catch up");
                    Block localTail = null;
                    Block remoteTail = null;
                    toServer.connect(new InetSocketAddress(ip.getHost(), ip.getPort()));
                    PrintWriter printWriter = new PrintWriter(toServer.getOutputStream(), true);
                    printWriter.println("cu");
                    printWriter.flush();
                    ObjectOutputStream oos = new ObjectOutputStream(toServer.getOutputStream());
                    ObjectInputStream ois = new ObjectInputStream(toServer.getInputStream());
                    try{
                        Block last = (Block) ois.readObject();
                        Block lastHead = last;

                        ois.close();
                        printWriter.close();
                        toServer.close();

                        for(int i = 0; i < gap - 1; i ++){
                            System.out.println("add more!");
                            Socket newSocket = new Socket();

                            newSocket.connect(new InetSocketAddress(ip.getHost(), ip.getPort()));
                            printWriter = new PrintWriter(newSocket.getOutputStream(), true);
                            printWriter.println("cu|" + Base64.getEncoder().encodeToString(last.getPreviousHash()));
                            printWriter.flush();
                            oos = new ObjectOutputStream(newSocket.getOutputStream());
                            ois = new ObjectInputStream(newSocket.getInputStream());


                            Block temp = (Block) ois.readObject();
                            last.setPreviousBlock(temp);
                            last.setPreviousHash(temp.calculateHash());
                            blockchain.setLength(blockchain.getLength() + 1);
                            last = temp;

                            ois.close();
                            printWriter.close();
                            newSocket.close();
                        }
                        remoteTail = last;
                        if(blockchain.getHead() == null){
                            blockchain.setHead(lastHead);
                            blockchain.setLength(blockchain.getLength() + 1);
                            localTail = last;
                        }else{
                            last.setPreviousBlock(blockchain.getHead());
                            last.setPreviousHash(blockchain.getHead().calculateHash());
                            localTail = last;
                            blockchain.setHead(lastHead);
                            blockchain.setLength(blockchain.getLength() + 1);
                        }



                        while(!hashComapre(localTail.getPreviousHash(), remoteTail.getPreviousHash())){
                            System.out.println("what!!");
                            Socket newSocket = new Socket();

                            newSocket.connect(new InetSocketAddress(ip.getHost(), ip.getPort()));
                            printWriter = new PrintWriter(newSocket.getOutputStream(), true);
                            printWriter.println("cu|" + Base64.getEncoder().encodeToString(remoteTail.getPreviousHash()));
                            printWriter.flush();
                            oos = new ObjectOutputStream(newSocket.getOutputStream());
                            ois = new ObjectInputStream(newSocket.getInputStream());

                            Block temp = (Block) ois.readObject();

                            ois.close();
                            printWriter.close();
                            newSocket.close();

                            remoteTail = temp;

                            blockchain.getPool().addAll(localTail.getPreviousBlock().getTransactions());

                            for(Transaction t: temp.getTransactions()){
                                blockchain.getPool().remove(t);
                            }

                            Block sub = new Block();
                            sub.setTransactions(temp.getTransactions());

                            sub.setPreviousBlock(localTail.getPreviousBlock().getPreviousBlock());
                            sub.setPreviousHash(localTail.getPreviousBlock().getPreviousHash());


                            localTail.setPreviousBlock(sub);
                            localTail.setPreviousHash(sub.calculateHash());

                            localTail = sub;
                        }
                    }
                    catch(ClassNotFoundException e){

                    }

                    printWriter.close();
                    toServer.close();
                    ois.close();

                }
                else{
                    try{
                        System.out.println("incoherent!");
                        toServer.connect(new InetSocketAddress(ip.getHost(), ip.getPort()));
                        PrintWriter printWriter = new PrintWriter(toServer.getOutputStream(), true);
                        printWriter.println("cu");
                        printWriter.flush();
                        ObjectOutputStream oos = new ObjectOutputStream(toServer.getOutputStream());
                        ObjectInputStream ois = new ObjectInputStream(toServer.getInputStream());

                        Block remoteTail = (Block) ois.readObject();

                        ois.close();
                        printWriter.close();
                        toServer.close();

                        Block localTail = blockchain.getHead();

                        blockchain.getPool().addAll(localTail.getTransactions());
                        for(Transaction t: remoteTail.getTransactions()){
                            blockchain.getPool().remove(t);
                        }

                        Block stt = new Block();
                        stt.getTransactions().addAll(remoteTail.getTransactions());
                        stt.setPreviousBlock(localTail.getPreviousBlock());
                        stt.setPreviousHash(localTail.getPreviousHash());
                        blockchain.setHead(stt);

                        localTail = stt;

                        while(!hashComapre(localTail.getPreviousHash(), remoteTail.getPreviousHash())){
                            System.out.println("Still wrong!");
                            Socket newSocket = new Socket();

                            newSocket.connect(new InetSocketAddress(ip.getHost(), ip.getPort()));
                            printWriter = new PrintWriter(newSocket.getOutputStream(), true);
                            printWriter.println("cu|" + Base64.getEncoder().encodeToString(remoteTail.getPreviousHash()));
                            printWriter.flush();
                            oos = new ObjectOutputStream(newSocket.getOutputStream());
                            ois = new ObjectInputStream(newSocket.getInputStream());

                            Block temp = (Block) ois.readObject();

                            ois.close();
                            printWriter.close();
                            newSocket.close();

                            remoteTail = temp;

                            if(localTail.getPreviousBlock() != null){
                                blockchain.getPool().addAll(localTail.getPreviousBlock().getTransactions());
                            }


                            for(Transaction t: temp.getTransactions()){
                                blockchain.getPool().remove(t);
                            }

                            Block sub = new Block();
                            sub.setTransactions(temp.getTransactions());
                            if(localTail.getPreviousBlock().getPreviousBlock() != null){
                                sub.setPreviousBlock(localTail.getPreviousBlock().getPreviousBlock());
                                sub.setPreviousHash(localTail.getPreviousBlock().getPreviousHash());
                            }

                            localTail.setPreviousBlock(sub);
                            localTail.setPreviousHash(sub.calculateHash());

                            localTail = sub;
                        }



                    }
                    catch(ClassNotFoundException e){

                    }
                }

            }
        }
        catch (IOException e) {

        }

    }

    private boolean hashComapre(byte[] b1, byte[] b2){
        for(int i = 0; i < b1.length; i++){
            if(b1[i] != b2[i]){
                return false;
            }
        }
        return true;
    }
}
