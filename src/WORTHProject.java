import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;

public class WORTHProject {
    //WORTHProject, rappresenta un progetto, con le sue card e con dei meccanismi di sincronizzazione per gli accessi
    //concorrenti


    public static class WORTHCard {//Inner Class che rappresenta una card
        private String cardName;
        private String cardDescrption;
        private String currentCardStatus;
        private String History;//History è una stringa che concateniamo con gli stati




        //esempio di History: TODO|INPROGRESS|TOBEREVISED|INPROGRESS|TOBEREVISED|DONE con '|' come separatore
        public WORTHCard() {
        }

        public WORTHCard(String cardName, String cardDescrption) {
            this.cardName = cardName;
            this.cardDescrption = cardDescrption;
            this.currentCardStatus="TODO";//Card automaticamente nello stato TODO
            this.History=this.currentCardStatus;
        }
        public String getCardDescrption() {
            return cardDescrption;
        }

        public  String getCardName() {
            return cardName;
        }

        public String getCurrentCardStatus() {
            //synchronized (currentCardStatus) {
                return currentCardStatus;
            //}
        }
        public void setCurrentCardStatus(String currentCardStatus) {
            this.currentCardStatus = currentCardStatus;
        }

        public void setNEWCurrentCardStatus(String newCardStatus) {
            //synchronized(currentCardStatus) {
                this.currentCardStatus = newCardStatus;
                this.History=this.History.concat("|"+newCardStatus);
            //}
        }

        public String getHistory(){
            return this.History;
        }

        public void setCardName(String cardName) {
            this.cardName = cardName;
        }

        public void setCardDescrption(String cardDescrption) {
            this.cardDescrption = cardDescrption;
        }

        public void setHistory(String history) {
            this.History = history;
        }
        //metodo per scrivere sul file system una card per backup
        public void SaveCard(String projectPath, ObjectMapper objMapper) throws FileNotFoundException, JsonProcessingException {//crea un File con le informazioni della card nella directory del Project corrente
            File card=new File(projectPath+"/"+this.getCardName());//Creazione del File della Card
            FileOutputStream fos=new FileOutputStream(card);//scrittura sul file della card
            byte[] toSave=objMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(this);
            try {
                card.createNewFile();//crea file della card se non esiste
                fos.write(toSave);
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private String ProjectTitle;
    //NomeCard,Card elementi delle hashmap che rappresentano la lista delle cards del progetto nei vari stati
    private HashMap<String,WORTHCard> ToDo;
    private HashMap<String,WORTHCard> InProgess;
    private HashMap<String ,WORTHCard> ToBeRevised;
    private HashMap<String,WORTHCard> Done;
    private ReentrantLock todoLock,inprogressLock,toberevisedLock,doneLock,usersListLock;
    private ArrayList<String> usersOfProject;
    private String ChatMulticastAdress;//MulticastAdress della Multicast Chat del Progetto
    //viene inviato al client alla sua prima richiesta readChat,in seguito il client non passerà dal server
    //per leggere la chat, IndrizzoMulticast+numPortaUDP
    private MulticastSocket ChatSocket;//socket su cui creo la chat e da cui ottengo il numero di porta da passare al server
    int port;
    String onlyAdress;




    public WORTHProject(String ProjectTitle, String projectMaker){
        this.ProjectTitle=ProjectTitle;
        this.usersOfProject=new ArrayList<String>();
        this.usersOfProject.add(projectMaker);
        this.ToDo=new HashMap<>();
        this.InProgess=new HashMap<>();
        this.Done=new HashMap<>();
        this.ToBeRevised=new HashMap<>();

        this.todoLock=new ReentrantLock();
        this.inprogressLock=new ReentrantLock();
        this.toberevisedLock=new ReentrantLock();
        this.doneLock=new ReentrantLock();
        this.usersListLock=new ReentrantLock();
        //in WORTHService verrà invocato createChat per inizializzare la chat
    }
    public boolean createChat(String chatMulticastAdress){//ritorna false c'è un errore nella creazione
        //della MulticastSocket nell'address specificato
        int port=-1;
        try {
            this.ChatMulticastAdress=chatMulticastAdress;//inizializzazione

            if(!InetAddress.getByName(this.ChatMulticastAdress).isMulticastAddress()){
                return false;//controllo che IP sia nel range di multicast
            }
            //se tutto va bene così si apre la MultiCast Socket
            this.ChatSocket=new MulticastSocket(new InetSocketAddress(this.ChatMulticastAdress,0));

            //this.ChatSocket.setNetworkInterface(NetworkInterface.getByInetAddress(InetAddress.getByName("localhost")));// se necessario settare interfaccia1
            //this.ChatSocket.setNetworkInterface(NetworkInterface.getByInetAddress(InetAddress.getLocalHost())); se necessario settare interfaccia 2
            //System.out.println("networkInterface della chat"+this.ChatSocket.getNetworkInterface().toString());

            //restituisco adress + numero di porta UDP per il multicast adressing al client
            this.port=ChatSocket.getLocalPort();
            this.onlyAdress=this.ChatMulticastAdress;
            this.ChatMulticastAdress=this.ChatMulticastAdress.concat("\u2407"+(ChatSocket.getLocalPort()));


        } catch (SocketException e) {
            System.out.println(e.getMessage()+" Problem with MulticastSocket,RETRY");

            return false;
        } catch (IOException e) {
            System.out.println(e.getMessage()+" Problem with MulticastSocket,RETRY");
            return false;
        }
        return true;
    }
    public void closeSocket(){//chiusura della MulticastSocket in caso eliminazione progetto + Invio del messaggio
        //\nPROJECT DELETED\n per avvisare i client di chiudere la Chat
        byte[] toSend=("\nPROJECT DELETED\n").getBytes();
        try (MulticastSocket sendSocket=new MulticastSocket()){//creo una DatagramSocket per questo metodo,per inviare mex di chiusura

            sendSocket.setTimeToLive(1);
            //sendSocket.setNetworkInterface(NetworkInterface.getByInetAddress(InetAddress.getByName("localhost")));
            DatagramPacket datagramPacket = new DatagramPacket(toSend, toSend.length, InetAddress.getByName(onlyAdress), this.port);
            sendSocket.send(datagramPacket);        }
         catch (UnknownHostException e) {
            e.printStackTrace();
        }catch (IOException e) {
            e.printStackTrace();
        }

        this.ChatSocket.close();
    }
    private void NotifyChatOfMovement(String Card,String src,String dst){//avviso gli utenti della Chat di un cambio di lista
        byte[] toSend=("SERVER:"+this.ProjectTitle+"\nEND\n"+Card+" spostata da "+ src+" a "+dst).getBytes();
        try (MulticastSocket sendSocket=new MulticastSocket()){//creo una DatagramSocket per questo metodo,per inviare mex notifica spostamento card
            sendSocket.setTimeToLive(1);
            //sendSocket.setNetworkInterface(NetworkInterface.getByInetAddress(InetAddress.getByName("localhost")));
            DatagramPacket datagramPacket = new DatagramPacket(toSend, toSend.length, InetAddress.getByName(onlyAdress), this.port);
            sendSocket.send(datagramPacket);
        }
        catch (UnknownHostException e) {
            e.printStackTrace();
        }catch (IOException e) {
            e.printStackTrace();
        }

    }
    public WORTHProject(){}
    public String[]getMembers(String username)throws IllegalArgumentException{
        String[] toReturn;
        authentication(username);
        usersListLock.lock();
        toReturn=usersOfProject.toArray(new String[0]);
        usersListLock.unlock();
        return toReturn;
    }
    public String getProjectTitle() {
        return ProjectTitle;
    }
    public String readChat(String nickUtente)throws IllegalArgumentException {
        authentication(nickUtente);
        return this.ChatMulticastAdress;
    }

    public void setProjectTitle(String projectTitle) {
        ProjectTitle = projectTitle;
    }

    public ArrayList<String> getUsersOfProject() {
        return usersOfProject;
    }

    public void setUsersOfProject(ArrayList<String> usersOfProject) {
        this.usersOfProject = usersOfProject;
    }
    public int addCard(String username, String cardName, String cardDescription) throws IllegalArgumentException{
        //necessario controllare che la Card non sia già presente con lo stesso titolo nel resto del Progetto
        authentication(username);//autenticazione utente nella lista degli utenti del progetto
        int toReturn=1;
        //check ad una ad una nelle liste delle Card per trovarla
        inprogressLock.lock();
        if(InProgess.containsKey(cardName) || ToBeRevised.containsKey(cardName)) {
            toReturn= 0;
        }
        inprogressLock.unlock();

        toberevisedLock.lock();
        if(ToBeRevised.containsKey(cardName)) {
            toReturn= 0;
        }
        toberevisedLock.unlock();

        doneLock.lock();
        if(Done.containsKey(cardName)) {
            toReturn= 0;
        }
        doneLock.unlock();

        todoLock.lock();
        if(ToDo.containsKey(cardName)) {
            toReturn= 0;
        }
        else if(toReturn==1){//inserisco solo se card non è presente ovunque
            ToDo.put(cardName,new WORTHCard(cardName,cardDescription));
            toReturn=1;
        }
        todoLock.unlock();
        return toReturn;

    }




    public boolean addUser(String newUserName,String username)throws IllegalArgumentException{
        authentication(username);
        boolean altradyIN=true;
        usersListLock.lock();
        if(!usersOfProject.contains(newUserName)){
            usersOfProject.add(newUserName);
            altradyIN=false;
        }
        usersListLock.unlock();
        return altradyIN;//se l'utente è già in lista lo segnalo al client
    }
    public boolean isUser(String username){//check che username corrisponda a quello di un utente del progetto
        boolean toReturn=false;
        usersListLock.lock();
        toReturn=usersOfProject.contains(username);
        usersListLock.unlock();
        return toReturn;
    }
    private void authentication(String username){
        if(!isUser(username)){
            //Se utente non ha permessi sul progetto viene lanciata una IllegalArgumentException
            throw new IllegalArgumentException("Operazione non autorizzata per utente:"+username);
        }
    }
    public String getHistory(String username,String cardName) throws IllegalArgumentException{
        authentication(username);
        String toReturn;
        //check ad una ad una nelle liste delle Card per trovarla
        todoLock.lock();
        if(ToDo.containsKey(cardName)) {
            toReturn = ToDo.get(cardName).getHistory();
            todoLock.unlock();
            return toReturn;
        }
        todoLock.unlock();

        inprogressLock.lock();
        if(InProgess.containsKey(cardName)) {
            toReturn = InProgess.get(cardName).getHistory();
            inprogressLock.unlock();
            return toReturn;
        }
        inprogressLock.unlock();


        toberevisedLock.lock();
        if(ToBeRevised.containsKey(cardName)) {
            toReturn = ToBeRevised.get(cardName).getHistory();
            toberevisedLock.unlock();
            return toReturn;
        }
        toberevisedLock.unlock();

        doneLock.lock();
        if(Done.containsKey(cardName)) {
            toReturn = Done.get(cardName).getHistory();
            doneLock.unlock();
            return toReturn;
        }
        doneLock.unlock();

        return "Operazione Fallita:Card non presente in nessuna lista";

    }


















    public String moveCard(String username,String cardTitle,String oldPosition,String newPosition) throws IllegalArgumentException{
        //Muove la Card in un nuovo stato SE è una stato accettabile dallo stato corrente
        authentication(username);
        WORTHCard cardToMove = null;
        if(newPosition=="TODO"){
            return "Operazione Fallita: Impossibile spostarsi in TODO";
        }
        else if(newPosition.equals("INPROGRESS")){
            if(oldPosition.equals("TOBEREVISED")){

                toberevisedLock.lock();//accesso in mutua esclusione alla lista delle card da revisionare

                if(ToBeRevised.containsKey(cardTitle)){
                    cardToMove=ToBeRevised.get(cardTitle);
                    ToBeRevised.remove(cardTitle,cardToMove);//rimozione della card dalla old list
                    //spostamento della card in INPROGRESS,necessario esegure questo codice con ENTRAMBE le lock
                    //per non "perdere" la card nelle altre operazioni
                    cardToMove.setNEWCurrentCardStatus("INPROGRESS");

                    inprogressLock.lock();//lock nuova lista acquisita
                    InProgess.put(cardTitle,cardToMove);
                    inprogressLock.unlock();

                    toberevisedLock.unlock();//rilascio della lock della vecchia lista

                    NotifyChatOfMovement(cardTitle,oldPosition,newPosition);
                    return "Operazione Successo: Card spostata in posizione desiderata";

                }
                else{
                    toberevisedLock.unlock();
                    return "Operazione Fallita: Card non è presente in posizione indicata";
                }
            }
            else if(oldPosition.equals("TODO")){
                todoLock.lock();

                if(ToDo.containsKey(cardTitle)){
                    cardToMove=ToDo.get(cardTitle);
                    ToDo.remove(cardTitle,cardToMove);
                    //spostamento della card in INPROGRESS
                    cardToMove.setNEWCurrentCardStatus("INPROGRESS");

                    inprogressLock.lock();
                    InProgess.put(cardTitle,cardToMove);
                    inprogressLock.unlock();

                    todoLock.unlock();

                    NotifyChatOfMovement(cardTitle,oldPosition,newPosition);
                    return "Operazione Successo: Card spostata in posizione desiderata";

                }
                else{
                    todoLock.unlock();
                    return "Operazione Fallita: Card non è presente in posizione indicata";
                }
            }
            else{
                return "Operazione Fallita: Impossibile spostarsi";
            }
        }else if(newPosition.equals("TOBEREVISED")) {
            if (oldPosition.equals("INPROGRESS")) {
                inprogressLock.lock();

                if (InProgess.containsKey(cardTitle)) {
                    cardToMove = InProgess.get(cardTitle);
                    InProgess.remove(cardTitle, cardToMove);

                    cardToMove.setNEWCurrentCardStatus("TOBEREVISED");
                    toberevisedLock.lock();//ottengo lock per inserimento in mutua esclusione
                    ToBeRevised.put(cardTitle, cardToMove);
                    toberevisedLock.unlock();



                    inprogressLock.unlock();
                    //Spostamento della Card in TOBEREVISED
                    NotifyChatOfMovement(cardTitle,oldPosition,newPosition);
                    return "Operazione Successo: Card spostata in posizione desiderata";

                } else {
                    inprogressLock.unlock();
                    return "Operazione Fallita: Card non è presente in posizione indicata";
                }
            } else {//Caso in cui la posizione di partenza è non valida
                return "Operazione Fallita: Impossibile spostarsi";
            }
        }
        else if (newPosition.equals("DONE")) {
            if (oldPosition.equals("TOBEREVISED")) {
                toberevisedLock.lock();//accesso in mutua esclusione alla lista delle card da revisionare
                if (ToBeRevised.containsKey(cardTitle)) {
                    cardToMove = ToBeRevised.get(cardTitle);
                    ToBeRevised.remove(cardTitle, cardToMove);

                    cardToMove.setNEWCurrentCardStatus("DONE");
                    doneLock.lock();//ottengo lock per inserimento in mutua esclusione
                    Done.put(cardTitle, cardToMove);
                    doneLock.unlock();



                    toberevisedLock.unlock();//rilascio la lock, ho terminato operazioni su ToBeRevised
                    NotifyChatOfMovement(cardTitle,oldPosition,newPosition);
                    return "Operazione Successo: Card spostata in posizione desiderata";


                } else {
                    toberevisedLock.unlock();
                    return "Operazione Fallita: Card non è presente in posizione indicata";
                }
            } else if (oldPosition.equals("INPROGRESS")) {
                inprogressLock.lock();
                if (InProgess.containsKey(cardTitle)) {
                    cardToMove = InProgess.get(cardTitle);
                    InProgess.remove(cardTitle, cardToMove);

                    cardToMove.setNEWCurrentCardStatus("DONE");
                    doneLock.lock();//ottengo lock per inserimento in mutua esclusione
                    Done.put(cardTitle, cardToMove);
                    doneLock.unlock();

                    inprogressLock.unlock();

                    return "Operazione Successo: Card spostata in posizione desiderata";
                } else {
                    inprogressLock.unlock();
                    return "Operazione Fallita: Card non è presente in posizione indicata";
                }
            } else {
                return "Operazione Fallita: Impossibile spostarsi";
            }

        }
        return "Operazione Fallita: Posizione desiderata INESISTENTE";
    }

    public String[] getCards(String username) throws IllegalArgumentException {
        authentication(username);
        ArrayList<String> listOfCards=new ArrayList<>();//utilizzo HashSet per eliminare i duplicati

        todoLock.lock();
        listOfCards.addAll(ToDo.keySet());
        todoLock.unlock();

        inprogressLock.lock();//acquisisco lock di una lista alla volta
        listOfCards.addAll(InProgess.keySet());
        inprogressLock.unlock();

        toberevisedLock.lock();
        listOfCards.addAll(ToBeRevised.keySet());

        toberevisedLock.unlock();

        doneLock.lock();
        listOfCards.addAll(Done.keySet());
        doneLock.unlock();
        return listOfCards.toArray(new String[0]);
    }
    public String getCard(String username,String cardName) throws IllegalArgumentException {//ricerca della card fra tutte le liste di card
        //restituite informazioni nel formato 'cardname descprition listacorrente' con ogni informazione separata da spazio
        authentication(username);
        WORTHCard carDtoReturn;
        String toReturn;//formato risposta "cardname+\u2407+cardDescription+\u2407+cardCurrentStatus"
        authentication(username);
        todoLock.lock();
        if(ToDo.containsKey(cardName)){
            carDtoReturn=ToDo.get(cardName);
            toReturn= carDtoReturn.getCardName()+"\u2407"+carDtoReturn.getCardDescrption()+"\u2407"+carDtoReturn.getCurrentCardStatus();

            todoLock.unlock();
            return toReturn;
        }
        todoLock.unlock();

        inprogressLock.lock();
        toberevisedLock.lock();//necessario ottenerli insieme per evitare di perdere card in movimento toberevised->inprogress
        if(InProgess.containsKey(cardName)){
            carDtoReturn=InProgess.get(cardName);
            toReturn= carDtoReturn.getCardName()+"\u2407"+carDtoReturn.getCardDescrption()+"\u2407"+carDtoReturn.getCurrentCardStatus();
            inprogressLock.unlock();
            toberevisedLock.unlock();
            return toReturn;
        }
        if(ToBeRevised.containsKey(cardName)){
            carDtoReturn=ToBeRevised.get(cardName);
            toReturn= carDtoReturn.getCardName()+"\u2407"+carDtoReturn.getCardDescrption()+"\u2407"+carDtoReturn.getCurrentCardStatus();
            inprogressLock.unlock();
            toberevisedLock.unlock();
            return toReturn;
        }
        inprogressLock.unlock();
        toberevisedLock.unlock();

        doneLock.lock();
        if(Done.containsKey(cardName)){
            carDtoReturn=Done.get(cardName);
            toReturn= carDtoReturn.getCardName()+"\u2407"+carDtoReturn.getCardDescrption()+"\u2407"+carDtoReturn.getCurrentCardStatus();
            doneLock.unlock();
            return toReturn;
        }
        doneLock.unlock();

        return "Card inesistente";
    }
    public boolean canDelete(String username)throws IllegalArgumentException{
        //controllo che ogni lista eccetto la lista Done siano vuote
        boolean ris=false;
        authentication(username);
        usersListLock.lock();//Evito inserimento di altre Cards mentre si tenta l'eliminazione bloccando l'autenticazione utente
        todoLock.lock();//controllo in tutte le liste simultaneamente
        toberevisedLock.lock();
        inprogressLock.lock();
        if(ToDo.isEmpty() && ToBeRevised.isEmpty() &&InProgess.isEmpty()) {
            ris = true;
            usersOfProject.clear();//Per non permettere l'aggiunta di nuove cards dopo il check che siano tutte in DONE e nessuna in altre liste
        }
        usersListLock.unlock();
        todoLock.unlock();
        toberevisedLock.unlock();
        inprogressLock.unlock();
        return ris;
    }

    public void SaveProjectState(String path) throws FileNotFoundException, JsonProcessingException {
        //I campi del progetto projectTitle,UsersofProject vengono salvati in un file JSON
        //Mentre le liste di oggetti in sè non verranno salvate piuttosto ricostruite(prendendo dai file delle card)
        File projectDirectory=new File(path+"/"+this.ProjectTitle);
        FileOutputStream fos;
        //deleteOldBackup(projectDirectory);//necessario eliminare le card del backup precedente per evitare duplicati
        //projectDirectory.delete();
        File subDirectory;
        projectDirectory.mkdir();//creo directory del Project se non esistente
        ObjectMapper objectMapper=new ObjectMapper();
        //salvataggio in Files diversi di tutte le card di tutte le liste in delle sottodirectory che indicano la loro lista
        subDirectory=new File(projectDirectory.getPath()+"/TODO");
        subDirectory.delete();
        subDirectory.mkdir();

        todoLock.lock();//in mutua esclusione scandisco ogni lista salvando le cards
        for (WORTHCard c:
                ToDo.values()) {
            c.SaveCard(subDirectory.getPath(),objectMapper);
        }
        todoLock.unlock();

        subDirectory=new File(projectDirectory.getPath()+"/INPROGRESS");
        subDirectory.mkdir();
        subDirectory=new File(projectDirectory.getPath()+"/TOBEREVISED");
        subDirectory.mkdir();

        inprogressLock.lock();//in mutua esclusione scandisco ogni lista salvando le cards
        for (WORTHCard c:
                InProgess.values()) {
            c.SaveCard(projectDirectory.getPath()+"/INPROGRESS",objectMapper);
        }
        inprogressLock.unlock();

        toberevisedLock.lock();
        for (WORTHCard c:
                ToBeRevised.values()) {
            c.SaveCard(subDirectory.getPath(),objectMapper);
        }

        toberevisedLock.unlock();

        subDirectory=new File(projectDirectory.getPath()+"/DONE");
        subDirectory.mkdir();
        doneLock.lock();//in mutua esclusione scandisco ogni lista salvando le cards
        for (WORTHCard c:
                Done.values()) {
            c.SaveCard(subDirectory.getPath(),objectMapper);
        }
        doneLock.unlock();
        //Infine in un file vengono salvate le informazioni del Progetto projectTitle+lista utenti
        File project=new File(projectDirectory.getPath()+"/"+getProjectTitle());
        byte []value=objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(this);
        fos=new FileOutputStream(project);
        try {
            fos.write(value);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteOldBackup(File projectDirectory) {//per eliminare i vecchi backup delle card
        if(projectDirectory.exists()){//eliminazione di tutte le card di tutte le directory di liste
            for (File listDirectory:
                 projectDirectory.listFiles()) {//per ogn
                if(listDirectory.exists() && listDirectory.isDirectory()){
                    for (File card:
                         listDirectory.listFiles()) {
                        card.delete();
                    }
                    listDirectory.delete();//dopo aver cancellato ogni elemento si cancella anche la lista
                }
                else{
                    listDirectory.delete();//in questo caso si elimina il FIle del progetto
                }

            }
        }
    }

    public void WorthRestoration(String path) throws IOException {
        ByteBuffer byteBuffer=ByteBuffer.allocate(256);//per tutta l'operazione di lettura si usa questo byte buffer
        ObjectMapper objectMapper=new ObjectMapper();
        ArrayList<Byte>growingBuf=new ArrayList<>();
        ToDo=new HashMap<>();
        todoLock=new ReentrantLock();
        InProgess=new HashMap<>();
        inprogressLock=new ReentrantLock();
        toberevisedLock=new ReentrantLock();
        ToBeRevised=new HashMap<>();
        doneLock=new ReentrantLock();
        Done=new HashMap<>();
        usersListLock=new ReentrantLock();
        JsonNode jnode=null;
        File directoryCard;
        FileChannel fc;
        directoryCard=new File(path+"/TODO");
        byte []result;
        int i;
        WORTHCard worthCard;
        //ottengo card dall singole directory delle liste
        RecreateList(directoryCard,byteBuffer,objectMapper,ToDo);
        directoryCard=new File(path+"/INPROGRESS");

        RecreateList(directoryCard,byteBuffer,objectMapper,InProgess);
        directoryCard=new File(path+"/TOBEREVISED");

        RecreateList(directoryCard,byteBuffer,objectMapper,ToBeRevised);
        directoryCard=new File(path+"/DONE");

        RecreateList(directoryCard,byteBuffer,objectMapper,Done);

    }
    private void RecreateList(File directoryCard,ByteBuffer byteBuffer,ObjectMapper objectMapper,HashMap list) throws IOException {
        //viene passata la directory della lista in cui la card andrà messa e la lista in cui re-inserire la card
        FileChannel fc;
        ArrayList<Byte> growingBuf = new ArrayList();
        byte[] result;
        int i;
        WORTHCard worthCard;
        JsonNode jnode;
        for (File cardFile :
                    directoryCard.listFiles()) {//ripristino tutti i progetti nella directory Project
            jnode = WORTHService.getJsonNode(byteBuffer, objectMapper, growingBuf, cardFile);//chiamo un metodo da WorthService
            worthCard = objectMapper.convertValue(jnode, WORTHCard.class);//ripristinati i campi ProjectTitle+Lista utenti
            //card viene aggiunta nella lista in cui era presente
            if (worthCard != null) {
                System.out.println("ripristinata la Card "+worthCard.cardName);
                list.put(worthCard.getCardName(), worthCard);
            }
        }
    }
}
