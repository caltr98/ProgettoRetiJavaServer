import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class WORTHService {
    private ConcurrentHashMap<String, WORTHProject> projects;//Uso di ConcurrentHashMap per avere Thread Safety nelle get
    int ottetto1,ottetto2,ottetto3,ottetto4;
    public WORTHService() {
        projects = new ConcurrentHashMap<>();
        ottetto1=225;//scelgo 225 come primo elemento dell'indirzzo di multicast
        ottetto2=0;
        ottetto3=0;
        ottetto4=0;//appena superato 255 aumenterò il valore di ottetto3
    }

    //Restituisce la lista di stringhe dei titoli dei Progetti
    public String[] ProjectLists() {
        //Ritorna la lista dei progetti, utilizzo i titoli come Keys della HashMap quindi
        //restituisco in realtà il KeySet stesso
        String[] toReturn;
        toReturn = projects.keySet().toArray(new String[0]);
        return toReturn;
    }

    //Creazione di un progetto,viene trovato un Multicast adress da assegnargli, si crea un progetto
    //sse non ne esiste un'altro con lo stesso ProjectName ,return 1 se operazione a successo,0 altrimenti
    public int createProject(String ProjectName, String ProjectMaker) throws Exception {
        WORTHProject newProject;
        String chatMulticastAdress;
        if(!projects.containsKey(ProjectName)) {
            newProject = new WORTHProject(ProjectName, ProjectMaker);
            while (!newProject.createChat((generateMulticastAdress()))){//cerchiamo di create la chat con qualsiasi multicastadress
            }
            if (projects.putIfAbsent(ProjectName, newProject) == null)
                return 1;
            else return 0;
        }
        else {
            return 0;
        }
    }

    //addMemberProject return=1 Se viene aggiunto utente al progetto specificato da projectName
    //return=2 anche se utente già apparteneva a lista utenti
    //-1 se il progetto è stato cancellato durante l'operazione , 0 se il progetto è stato cancellato prima
    public int addMemberProject(String projectName, String nickUtente,String NewNickUtente) throws IllegalArgumentException {
        WORTHProject toModify;
        boolean returned;
        if (projects.containsKey(projectName)) {//Progetto è attivo
            try {
                returned=projects.get(projectName).addUser(NewNickUtente,nickUtente);
                if(returned==false) {
                    return 1;
                }else return 2;
            } catch (NullPointerException e) {//Progetto eliminato poco prima di potervi accedere
                return -1;
            }
        }
        return 0;
    }

    //moveCard ->return:risultato opezione in Stringa se il progetto è attivo
    //null se il progetto è stato eliminato PRIMA o DURANTE l'operazione(o mai esistito)
    public String moveCard(String username, String projectName, String cardName, String listaPartenza, String listaDestinazione)throws IllegalArgumentException{
        WORTHProject toModify;
        if (projects.containsKey(projectName)) {//Progetto è attivo
            try {
                return projects.get(projectName).moveCard(username, cardName, listaPartenza, listaDestinazione);
            } catch (NullPointerException e) {//Progetto eliminato poco prima di potervi accedere
                return null;
            }
        }
        return null;
    }

    //addcard:return -1 se il progetto non è presente/è stato cancellato durante l'operazione
    //return 0 se la card non può essere inserita(esiste una card con lo stesso titolo
    //return 1 se la card è stata inserita con successo
    public int addCard(String projectName, String nickutente, String cardName, String cardDescription) throws IllegalArgumentException {
        WORTHProject toModify;

        if (projects.containsKey(projectName)) {//Progetto è attivo
            try {
                return projects.get(projectName).addCard(nickutente, cardName, cardDescription);
            } catch (NullPointerException e) {//Progetto eliminato poco prima di potervi accedere
                return -1;
            }
        }
        return -1;
    }

    //moveCard ->return:risultato operazione in Stringa se il progetto è attivo
    //null se il progetto è stato eliminato PRIMA o DURANTE l'operazione(o mai esistito)
    public String getCardHistory(String projectName, String nickutente, String cardName) throws IllegalArgumentException {
        if (projects.containsKey(projectName)) {//Progetto è attivo
            try {
                return projects.get(projectName).getHistory(nickutente, cardName);
            } catch (NullPointerException e) {//Progetto eliminato poco prima di potervi accedere
                return "CardInesistente "+cardName;
            }
        }
        return "ProgettoInesistente "+ projectName;
    }

    public int deleteProject (String projectName, String nickutente) throws IllegalArgumentException {
        if (projects.containsKey(projectName)) {//Progetto è attivo
            try {
                if(projects.get(projectName).canDelete(nickutente)){
                    projects.get(projectName).closeSocket();//chiusura della MulticastSocket della chat
                    projects.remove(projectName);
                    return 1;
                }
                else return -1;
            }catch (NullPointerException e){
                return 0;//progetto cancellato già mentre si tentava cancellazione
            }
        }
        return 0;//project non esiste
    }
    public String[] showMembers(String projectName,String nickUtente)throws IllegalArgumentException{
        String[] toReturn = null;
        if (projects.containsKey(projectName)) {//Progetto è attivo
            try {
                toReturn= projects.get(projectName).getMembers(nickUtente);
            } catch (NullPointerException e) {//Progetto eliminato poco prima di potervi accedere
                return null;
            }
        }
        return toReturn;

    }
    public String[] showCards(String projectName,String nickUtente) throws IllegalArgumentException{
        String[] toReturn=null;
        if (projects.containsKey(projectName)) {//Progetto è attivo
            try {
                toReturn= projects.get(projectName).getCards(nickUtente);
            } catch (NullPointerException e) {//Progetto eliminato poco prima di potervi accedere
                return null;
            }
        }
        return toReturn;

    }
    public String showCard(String projectName,String nickUtente,String cardName) throws IllegalArgumentException{
        String toReturn;
        if (projects.containsKey(projectName)) {//Progetto è attivo
            try {
                toReturn= projects.get(projectName).getCard(nickUtente,cardName);
                //restituite informazioni nel formato 'cardname descprition listacorrente' con ogni informazione separata da spazio
                //Se una card non esiste viene restituita stringa che lo indica
                return toReturn;
            } catch (NullPointerException e) {//Progetto eliminato poco prima di potervi accedere
                return null;
            }
        }
        return null;

    }


    public String[] listProjects(String userRequester) {//Lista dei Progetti di un utente
        ArrayList<String>projectsList=new ArrayList<>();
        //necessario controllare presenza utente PROGETTO per PROGETTO
        for (WORTHProject project:projects.values()
             ) {
            if(project.isUser(userRequester)){
                projectsList.add(project.getProjectTitle());
            }
        }
        if(projectsList.size()==0){
            return null;//non ci sono progetti attivi per quest utente
        }
        return projectsList.toArray(new String[0]);
    }
    public void SaveState(){//salvataggio di tutti i project nel filesystem in una directory Projects
        File directoryProjects=new File("./Projects");
        directoryProjects.mkdir();
        for (File project:
             directoryProjects.listFiles()) {//necessario eliminare il vecchio backup + definitivamente
            //i progetti cancellati
            WORTHProject.deleteOldBackup(new File(project.getPath()));//eliminazione di un progetto,liste e cards
            project.delete();//elimazione della directory del progetto stessa
        }
        for (WORTHProject project:projects.values()
        ) {
            try {
                project.SaveProjectState(directoryProjects.getPath());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }

    private String generateMulticastAdress() throws Exception {//genero indirizzi multicast per la chat,uno per ogni progetto
        String adress;
        ottetto4++;
        if(ottetto4==256){
            ottetto4=0;
            ottetto3++;
            if(ottetto3==256){
                ottetto3=0;
                ottetto2++;
                if(ottetto2==256){
                    ottetto2=0;
                    ottetto1++;
                    if(ottetto1==240){
                        throw new Exception("Reboot the Server, No more Multicast adress avaible");
                        //caso in cui tutti gli indirizzi di Multicast possibili sono stati assegnati
                    }
                }
            }
        }
        adress=new String(ottetto1+"."+ottetto2+"."+ottetto3+"."+ottetto4);
        //primo indirizzo assegnato sarà 225.0.0.1
        return adress;
    }


    public void WorthRestoration() throws Exception {//ripristino dello stato persistente dopo un riavvio del Server
        File directoryProjects=new File("Projects");
        int i;
        WORTHProject savedProject;
        if(!directoryProjects.exists() &&
        !directoryProjects.isDirectory())return;
        ArrayList<Byte>growingBuf=new ArrayList<>();//array di supporto al byte array
        ByteBuffer byteBuffer=ByteBuffer.allocate(256);//byteBuffer utilizzato nella lettura del FileChannel
        ObjectMapper objectMapper=new ObjectMapper();
        JsonNode jnode=null;
        File projFile;
        for (File dirprojFile:
             directoryProjects.listFiles()) {//ripristino tutti i progetti nella directory Project
            //viene ottenuta una directory con all'interno il file del project
            projFile=new File(dirprojFile.getPath()+"/"+dirprojFile.getName());//aprendo il file con lo stesso nome della directory
            //ottengo il file del progetto
            jnode=getJsonNode(byteBuffer,objectMapper,growingBuf,projFile);
            savedProject=objectMapper.convertValue(jnode,WORTHProject.class);//ripristinati i campi ProjectTitle+Lista utenti
            //necessario ripristinare le card all'interno del progetto
            if(savedProject!=null) {
                savedProject.WorthRestoration(dirprojFile.getPath());//metodo interno al Project per ripristinarne completamente lo stato
                while (!savedProject.createChat(generateMulticastAdress())){
                    System.out.println("assegnamento Multicast non a buon fine,ciclo di retry");
                }
                projects.put(savedProject.getProjectTitle(),savedProject);
            }jnode=null;
            projFile=null;
        }

        System.out.println("Ripristinati: "+projects.size()+" progetti");
    }

    public static JsonNode getJsonNode(ByteBuffer byteBuffer, ObjectMapper objectMapper, ArrayList<Byte> growingBuf, File cardFile) throws IOException {
        //metodo statico per ottenere un JsonNode da convertire in oggetto, utilizzo di FileInputStream
        //e nio(bloccante)
        FileChannel fc;
        byte[] result;
        int i;
        JsonNode jnode;
        growingBuf.clear();
        fc = new FileInputStream(cardFile).getChannel();
        while (fc.read(byteBuffer) > -1) {
            byteBuffer.flip();
            while (byteBuffer.hasRemaining()) {
                growingBuf.add(byteBuffer.get());
            }
            byteBuffer.clear();
        }
        byteBuffer.clear();
        result = new byte[growingBuf.size()];
        for (i = 0; i < growingBuf.size(); i++) {
            result[i] = growingBuf.get(i);
        }
        jnode = objectMapper.readTree(result);//otteniamo il jnode dai byte letti
        fc.close();
        return jnode;
    }


    public String readChat(String projectName, String nickUtente)throws IllegalArgumentException {
        String toReturn=null;
        if (projects.containsKey(projectName)) {//Progetto è attivo
            try {
                toReturn= projects.get(projectName).readChat(nickUtente);
            } catch (NullPointerException e) {//Progetto eliminato poco prima di potervi accedere
                return null;
            }
        }
        return toReturn;//return dell'indirizzo multicast + porta che il client deve usare per leggere la chat

    }
}