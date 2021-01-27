//Calogero Turco Mat.558998

import com.fasterxml.jackson.core.JsonProcessingException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServerMain {
    public static void main(String args[]) {
        //INIZIALIZZAZIONE//

        int serverPort,registryPort,backup;//possibilità di inserire come parametri la porta della serverSocket e del Registry RMI
        serverPort = 6364;//numero di porta di Default del Server in questo Progetto
        registryPort=7897;//numero di porta di Default del Registry da cui ottenere metodi RMI Registrazione e Login
        int backupThreshold=16;//ogni 16 operazioni viene eseguito un Backup dello stato del Server,di Default
        int coreThreads=3;//numero di CoreThread del thread pool di default
        int maximumThreads=10;//numero massimo di Thread del ThreadPool attivi di default
        //argomenti serverport registryport backupthreashold corethreads maximumthreads, posso settarne uno in default con '-'
        if (!(args.length <1)) {//se c'è almeno un argomento
            //Se si inserisce da linea di comando anche n.porta del Server
            //controllo che porta sia un numero
            if(!args[0].equals("-")) {//in questo caso sarà impostato il valore di default
                try {
                    serverPort = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    System.out.println("ERR -arg 1");
                    return;
                }
            }
            if (args.length >= 2) {
                if(!args[1].equals("-")) {//in questo caso sarà impostato il valore di default

                    try {
                        registryPort = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        System.out.println("ERR -arg 2");
                        return;
                    }
                }

                if(args.length>=3) {//inserita una backupThreshold manualmente
                    if(!args[2].equals("-")) {//in questo caso sarà impostato il valore di default

                        try {
                            backupThreshold = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            System.out.println("ERR -arg 3");
                            return;
                        }
                    }
                    if (args.length >= 4) {//inserito numero corethreads manualmente
                        if(!args[3].equals("-")) {//in questo caso sarà impostato il valore di default
                            try {
                                coreThreads = Integer.parseInt(args[3]);
                                maximumThreads = coreThreads;//se non viene inserito un nuovo argomento a maximum thread
                            } catch (NumberFormatException e) {
                                System.out.println("ERR -arg 4");
                                return;
                            }
                        }
                        if (args.length == 5) {
                            if(!args[4].equals("-")) {//in questo caso sarà impostato il valore di default
                                try {
                                    maximumThreads = Integer.parseInt(args[4]);
                                } catch (NumberFormatException e) {
                                    System.out.println("ERR -arg 5");
                                    return;
                                }
                            }
                            if (maximumThreads < coreThreads) {
                                System.out.println("numero di corethreads maggiore di maximumthreads");
                                throw new IllegalArgumentException();//errore

                            }
                        }
                        else {
                            System.out.println("Too many arguments");
                            return;
                        }
                    }
                }
            }
        }
        System.out.println("parametri impostati ServerPort: "+serverPort + " registryPort: "+registryPort+" backupThreshold: "+backupThreshold+
                " nCorethreas: "+coreThreads+" nMaxThreads: "+maximumThreads);
        //FINE INIZIALIZZAZIONE//
        ServerSocketChannel serverSocketChannel;//serverSocketChannel per il multiplexing dei canali
        Selector selector = null;
        Set<SelectionKey> readKeys;
        Iterator<SelectionKey> keyIterator;
        ByteBuffer byteBuffer;//ByteBuffer non direct per la lettura dei comandi
        ArrayList<Byte>growingBuffer = new ArrayList<>();//supporto alla lettura dal ByteBuffer
        int i;
        int OpCounter=0;//Conteggio del numero di operazioni fatte, se arrivano a numberofOp si esegue un Backup del Server
        String username;
        String []tmpUsrPass=null;
        String regexNospace="([a-zA-Z0-9.~!$%^&*_=+_%\\[\\]()\"{}\\\\:;,^?-]+)"; // usata per matching username,stringa di simboli
        //+caratteri alfanumerici senza spazi
        WORTHService worthService=new WORTHService();//WORTHService rappresenta l'insieme dei progetti e dei metodi su essi
        //chiamabili dai Worker-Thread
        try {
            worthService.WorthRestoration();//Prima dell'avvio effettivo del server si ripristina lo stato peprsistenze
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();//Non ci sono più indirizzi per il Multicast da usare per la chat
            //presubile che questa casistica non accada mai
        }
        ServerUserData serverUserData = null;//Oggetto che rappresenta le informazioni sugli utenti
        
        try {
            serverUserData=ServerUserData.usersRestoration();//Ripristino dello stato degli Utenti(utenti registrati e password)

        } catch (IOException e) {
            e.printStackTrace();
        }catch (NullPointerException e){
            if(serverUserData==null){
                try {
                    serverUserData=new ServerUserData();
                } catch (NoSuchPaddingException e1) {
                    e1.printStackTrace();
                } catch (NoSuchAlgorithmException e1) {
                    e1.printStackTrace();
                }
            }

        }
        if(serverUserData==null){
            try {
                serverUserData=new ServerUserData();
            } catch (NoSuchPaddingException e1) {
                e1.printStackTrace();
            } catch (NoSuchAlgorithmException e1) {
                e1.printStackTrace();
            }
        }


        //Inizializzazione RMI//
        RegistrationInterface stubRegistration;//Metodo da esportare per Registrarsi al Server
        ServerCallbackRegistration stubCallbackRegistration;//Metodo da esportare per registrarsi al servio di CallBack
        try{
            stubRegistration = new ServerRegistrationProxy(serverUserData);
            stubCallbackRegistration=new ServerCallBackRegistationProxy(serverUserData);
            LocateRegistry.createRegistry(registryPort);
            Registry r=LocateRegistry.getRegistry(registryPort);
            r.bind("Server-Utenti",stubRegistration);
            r.bind("Server-Callback",stubCallbackRegistration);
            System.out.println("Servizi RMI pronti");

        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (AlreadyBoundException e) {
            e.printStackTrace();
        }
        WORTH wother=new WORTH(worthService,serverUserData);//Utilizzato per accedere a worthService, contiene
        //i metodi comprendere che tipo di richiesta è arrivata e che comando eseguire per esaudirla
        String s22;//La stringa che userò per le richieste del client
        byte[] tmpTOString ;
        HashMap<Integer,TaskWorker> clientsWorker=new HashMap<>();//Conservo in una HashMap clientId->WorkerAdibito
        HashMap<Integer,String> clientUsername=new HashMap<>();//Conservo in una HashMap clientId->UserNameClientLoggato

        ThreadPoolExecutor users=new ThreadPoolExecutor(coreThreads,maximumThreads,3, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());//pool di thread con corethread=3,espandibile a 10,
        //alla ricezione di un comando viene creato un nuovo Task da eseguire, la coda delle task è espandibile al massimo

        int clientId=0;//Associo ad ogni Client un Id univoco(intero ogni volta maggiore)

        try {//Avvio del serverSocketChannel
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(serverPort));
            serverSocketChannel.configureBlocking(false);//selettore supporta solo metodi in non blocking mode
            selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);//Attento connessioni TCP
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Server READY!!");
        while (true) {
            try {
                selector.select();//ricerca di operazioni ready dall'interestset
            } catch (IOException e) {
                e.printStackTrace();
            }
            readKeys = selector.selectedKeys();
            keyIterator = readKeys.iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();//prendo chiave
                keyIterator.remove();//rimuovo chiave dal keyIterator

                try {
                    if(key.isAcceptable()) {//Stabilimento Connessione TCP
                        SocketChannel client = ((ServerSocketChannel) key.channel()).accept();
                        client.configureBlocking(false);
                        SelectionKey k2=client.register(selector,SelectionKey.OP_READ);//registro operazione READ nell'interestSet
                        k2.attach(clientId++);//Associato clientId al SocketChannel del client,manca ancora nome utente però
                        //non potrà ancora inviare comandi previo Login
                        System.out.println("Stabilita una connessione id: "+(clientId-1));
                    }
                    else if(key.isReadable()){
                        TaskWorker worker;
                        int id= (int) key.attachment();

                        byteBuffer=ByteBuffer.allocate(1024);//
                        SocketChannel client = (SocketChannel) key.channel();
                        client.configureBlocking(false);
                        byteBuffer.clear();
                        while (client.read(byteBuffer)>0){//Lettura della richiesta dal Client
                            byteBuffer.flip();
                            while (byteBuffer.hasRemaining()) {
                                growingBuffer.add(byteBuffer.get());//lettura se byteBuffer.dimension()< (dati in arrivo)
                            }
                            byteBuffer.clear();
                        }
                        byteBuffer.flip();
                        while (byteBuffer.hasRemaining()) {
                            growingBuffer.add(byteBuffer.get());//ultima lettura
                        }
                        byteBuffer.clear();//il byteBuffer viene ripulito per operazioni successive
                        if(growingBuffer.size()==0){//bytebuffer con 0 byte da letti se cade connessione TCP
                            //caso in cui la connessione TCP lato client CADE, necessario eliminare
                            //il client dalla lista dei client online e il suo socketchannel dalla lista
                            //dei client correntemente connessi
                            System.out.print("TCP connection with Client Fail: "+clientId);

                            client.close();//chiusura del canale e automatica eliminazione della key dal selector
                            if(clientUsername.containsKey(id)) {//Se utente è effettivamente loggato
                                //necessario forzare il logout e avvisare altri client del logout
                                serverUserData.logOutUser(clientUsername.get(id));
                                clientUsername.remove(id);//rimozione dell'utente dalla lista del server
                            }
                        }
                        else {
                            key.interestOps(SelectionKey.OP_WRITE);//È arrivando un comando,vuol dire che client aspetterà una risposta,
                            //mettiamo nell'interestSet la scrittura nel canale del client

                            tmpTOString = new byte[growingBuffer.size()];//i Byte letti vengono convertiti in byte[] e poi stringa
                            for(i=0;i<growingBuffer.size();i++){
                                tmpTOString[i]=growingBuffer.get(i);
                            }
                            growingBuffer.clear();//pulizia dell'arraylist per operazioni future
                            s22 = new String(tmpTOString, Charset.defaultCharset());//trasmormo i byte della richiesta in una stringa con la richiesta stessa
                            if (clientUsername.get(id) == null) {

                                if (s22.matches("^0 login -\r"+regexNospace+" -\r"+regexNospace)) {//Login avviene su MainServer-Thread
                                    tmpUsrPass = s22.split(" -\r");
                                    username = serverUserData.loggingIn(tmpUsrPass[1], tmpUsrPass[2]);//stringa di avviso o stringa username

                                    if (username.equals(tmpUsrPass[1])) {//username e password inserite corretamente
                                        clientUsername.put(id, username);
                                        worker = new TaskWorker("LOGINSUCCESS", username, wother);
                                        clientsWorker.put(id, worker);
                                        users.submit(worker);//Un WorkerThread si impegnerà ad informare il client dell'esito del login

                                    } else {//avviso client di login errato,WRONGPASSWORD oppure NOUSER + ALREADYLOGGEDSOMEWHERE
                                        worker = new TaskWorker(username, tmpUsrPass[1], wother);
                                        clientsWorker.put(id, worker);
                                        users.submit(worker);

                                    }
                                } else {
                                    worker = new TaskWorker("DOLOGIN","not logged user", wother);
                                    clientsWorker.put(id, worker);
                                    users.submit(worker);

                                }

                                } else if (s22.equals("-1 logout") && clientUsername.containsKey(id)) {//Logout totalmente effettuato nel MainThread-Server
                                    serverUserData.logOutUser(clientUsername.get(id));
                                    clientUsername.remove(id);//rimozione dell'utente dalla lista del server
                                    System.out.println("Logout : numero keys pre cancel: " + selector.keys().size());
                                    key.cancel();//key cancellata quindi non si controllerà più nessuna operazione Read/Write
                                    //per il canale di questo client
                                    key.channel().close();//chiuso il channel del client


                            }
                                else {
                                    System.out.println("creato un worker per task di user" + clientUsername.get(id));
                                    worker = new TaskWorker(s22, clientUsername.get(id), wother);
                                    clientsWorker.put(id, worker);
                                    users.submit(worker);

                                }
                            }OpCounter++;//aggiornamento di OpCounter alla richiesta di Operazione del Client
                        }
                        else if(key.isWritable()) {//Quando il Client è pronto a ricevere risposta
                            int id = (int) key.attachment();
                            TaskWorker worker;
                            if (clientsWorker.containsKey(id)) {//SE non è presenta allora vuol dire che non
                                //si è letta richiesta in precedenza
                                SocketChannel client = (SocketChannel) key.channel();
                                worker=clientsWorker.remove(id);//non abbiamo più bisogno del riferimento al worker
                                worker.canGiveAnswer(client);//contattiamo worker per passargli il canale in cui scrivere la risposta
                                key.interestOps(SelectionKey.OP_READ);//imposto operazione di OP_READ in interestSet
                                //attendo nuove richieste
                            }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Operazioni eseguite pre Backup: "+OpCounter);
            if(OpCounter==backupThreshold){//Backup eseguito da MainThread-Server quindi non saranno lette richieste
                //durante il backupt,MA il Backup viene eseguito in concorrenza con i Worker-Thread (può sfuggire qualche op)
                System.out.println("BACKUP IN CORSO");
                OpCounter=0;//OpCounter viene conteggiato ad ogni ricezione comando dal client

                worthService.SaveState();//Salvataggio dello stato dei Progetti
                try {
                    serverUserData.SaveRegistredUsers();//Salvataggio degli utenti registrati
                    //necessario eseguire il Backup degli utenti e i progetti insieme per evitare progetti con
                    //utenti non registrati in un eventuale ripristino
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }

        }
    }
}
