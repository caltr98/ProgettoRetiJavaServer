import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TaskWorker implements Runnable {
    private boolean clientReady;//Se true si può dare risposta al client
    private String request;//Riga richiesta da esaudire passata dal MainServer-Thread
    private ReentrantLock lockState;//lock per acceso alle variabili condivise con MainServer-Thread
    private Condition waitForTask;//variabile condizione su cui il Thread si disattiva in caso di nessuna richiesta da esaudire
    private SocketChannel client;
    private String userRequester;
    WORTH worther;

    public TaskWorker(String request, String userRequester, WORTH worther){
        lockState=new ReentrantLock();
        waitForTask=lockState.newCondition();
        clientReady=false;
        this.request=request;
        this.userRequester=userRequester;
        this.worther=worther;
    }
    public void canGiveAnswer(SocketChannel client){
        lockState.lock();
        this.client=client;
        clientReady=true;//passata la socket channel del client a cui dare risposta
        waitForTask.signal();
        lockState.unlock();
    }

    @Override
    public void run() {
        String request;
        String response;
        SocketChannel clientChannel;
        ByteBuffer responseBuf;
        request = this.request;
        response = worther.OPTODO(request,userRequester)+"\n";
        lockState.lock();
        while (!clientReady) {
            try {
                waitForTask.await();//attendo di ricevere la socketChannel del client
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        clientChannel = this.client;
        lockState.unlock();
        try {
            clientChannel.configureBlocking(false);
            responseBuf = ByteBuffer.wrap(response.getBytes());
            while (responseBuf.hasRemaining()) {
                try {
                    client.write(responseBuf);
                } catch (IOException e) {
                    e.printStackTrace();
                    clientChannel.close();//chiusura della SocketChannel se PIPE viene interrotta
                }
            }
            if (!responseBuf.hasRemaining()) {
                responseBuf.clear();
            }
            System.out.println("Task terminata:"+Thread.currentThread());
            //clientChannel.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
