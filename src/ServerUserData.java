import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class ServerUserData {
    //Oggetto che conterrà le informazioni che ha il server sugli utenti, quindi la lista degli utenti(totale e online)
    //Acesso concorrente tra MainThread-Server e i thread dei metodi RMI
    HashMap<String,byte[]> UserPassword;//HashMap per collezzionare utenti username=key, password=value CRIPTATA
    ArrayList<String> loggedUsers;
    HashMap<String,ClientNotifyInterface>notifyClient;//String->stub HashMap degli stub degli utenti per effettuare callback(aggiornamento)
    Cipher cipher;//per criptare la password da conservare
    SecretKey secretKey;
    byte[] key;
    public ServerUserData() throws NoSuchPaddingException, NoSuchAlgorithmException {
        UserPassword=new HashMap<>();
        loggedUsers=new ArrayList<>();
        notifyClient=new HashMap<> ();
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        cipher=Cipher.getInstance("AES");
        keyGenerator.init(256);
        secretKey=keyGenerator.generateKey();
        key=secretKey.getEncoded();
        //secretKey=new SecretKeySpec(key,0,key.length,"AES");
    }
    public  void reSETKey(){//metodo chiamato per ripristinare la secretKey dopo ripristino stato oggetto
        secretKey=new SecretKeySpec(key,0,key.length,"AES");
    }

    public byte[] getKey() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public int registration(String usrname, String password) throws RemoteException, IllegalArgumentException, NullPointerException {
        if(usrname==null){
            throw new NullPointerException("username null");
        }
        if(password==null){
            throw new NullPointerException("password null");
        }
        try {
            cipher.init(Cipher.ENCRYPT_MODE,secretKey);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        byte []passbyte= new byte[0];
        try {
            passbyte = cipher.doFinal(password.getBytes());
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        synchronized (UserPassword){
            if(!UserPassword.containsKey(usrname)){
                UserPassword.put(usrname,passbyte);
            }
            else{
                throw new IllegalArgumentException("Username già in utilizzo");
            }
        }
        notifyALLRegistration();//Notifico tutti gli utenti di una nuova registrazione,attivata da Thread che esegue metodo
        //RMI
        return 1;
    }
    public boolean isRegistered(String usrname){
        synchronized (UserPassword) {
            return this.UserPassword.containsKey(usrname);//controllo che un dato utente sia registrato(per AddMember al project)
        }
    }


    public String loggingIn(String usrname, String password) throws BadPaddingException, InvalidKeyException, IllegalBlockSizeException {//return username se  usrname è un utente ed ha la password specificata
        //se utente non è registrato o password non corrisponde return NoUser o WrongPassword
        String toReturn=null;
        synchronized (UserPassword) {
            if(UserPassword.containsKey(usrname)){
                if( getPassword(usrname).equals(password) ) {
                    toReturn =usrname;
                    synchronized (loggedUsers) {//necessario aggiungere utente alla lista degli utenti loggati
                        if(loggedUsers.contains(toReturn)){
                            toReturn="AlreadyLoggedSomewhere";//caso in cui l'utente risulta già loggato
                        }
                        loggedUsers.add(toReturn);
                    }
                    //notifyALLNewLoginLogOut();Notifico gli utenti loggati del nuovo login,se eseguita
                    //qui e non nel metodo callbackregistration implica che si occupa della notifica il
                    //MainThread-Server
                }
                else{
                    toReturn="WrongPassword";
                }
            }
            else{
                toReturn="NoUser";
            }
        }
        return toReturn;
    }
    public void logOutUser(String username) {
        synchronized (loggedUsers) {//rimuovo utente dalla lista degli utenti loggati
            loggedUsers.remove(username);
            System.out.println("removing username after logout "+username);
            unregistercallback(username);
        }
        notifyALLNewLoginLogOut();//Il MainThread-Server deve in questo caso eseguire lui stesso i metodi di callback
    }

    private String getPassword(String usrname) throws InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        cipher.init(Cipher.DECRYPT_MODE,secretKey);
        String originalPas=new String(cipher.doFinal(UserPassword.get(usrname)), Charset.defaultCharset());
        return originalPas;
    }

    public void callbackregistration(ClientNotifyInterface clientNotifyInterface,String username) {
        synchronized (notifyClient) {
            if (!notifyClient.containsValue(clientNotifyInterface)) {
                notifyClient.put(username,clientNotifyInterface);
                notifyRegistrationLogin(username);//Notifico al nuovo utente loggato per la prima volta lista utenti del servizio
                // e la lista degli utenti online
                notifyALLNewLoginLogOut();//Attivata da metodo che esegue la registrazione al servizio

            }
        }
    }
    public void unregistercallback(String username) {//eseguita dal MainThread-Server dopo un Logout
        synchronized (notifyClient){
            if(notifyClient.containsKey(username)){
                notifyClient.remove(username);//rimuovo stub metodo di notifica al client
            }
        }
    }
    public void notifyALLRegistration(){
        //Aggiornamento delle liste Utenti dato un evento di registrazione
        String[] usersList;
        System.out.println("Avvio di callback eseguita da:"+Thread.currentThread());

        synchronized (UserPassword) {
            usersList = UserPassword.keySet().toArray(new String[0]);
        }
        ArrayList toShareUserList=new ArrayList(Arrays.asList(usersList));//conservo in una arraylist gli username utente
        synchronized (notifyClient) {
            if(notifyClient.size()!=0) {
                for (ClientNotifyInterface i :
                        notifyClient.values()) {
                    try {
                        i.updateUsers(toShareUserList);//chiamata a stub utente con valore lista degli utenti
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    public void notifyALLNewLoginLogOut(){
        //Aggiornamento delle liste Utenti dato un evento di registrazione
        String[] usersList;
        System.out.println("Avvio di callback invocata da:"+Thread.currentThread());

        synchronized (notifyClient) {
            usersList = notifyClient.keySet().toArray(new String[0]);
            ArrayList toShareUserList=new ArrayList(Arrays.asList(usersList));//conservo in una arraylist gli username utenti
            if(notifyClient.size()!=0) {
                for (ClientNotifyInterface i :
                        notifyClient.values()) {
                    try {
                        i.updateOnlineUsers(toShareUserList);//chiamata a stub utente con valore lista degli utenti
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    public void notifyRegistrationLogin(String username){//notifica al singolo utente della lista Utenti
        String[] usersList;
        String[] usersOnlineList;
        ArrayList toShareUserList;
        ArrayList toShareOnlineUserList;
        System.out.println("Avvio di callback invocata da:"+Thread.currentThread());
        synchronized (UserPassword) {
            usersList = UserPassword.keySet().toArray(new String[0]);
        }
        toShareUserList=new ArrayList(Arrays.asList(usersList));//conservo in una arraylist gli username utente
        synchronized (notifyClient) {
            usersOnlineList=notifyClient.keySet().toArray(new String[0]);;
            toShareOnlineUserList=new ArrayList(Arrays.asList(usersOnlineList));
            try {
                notifyClient.get(username).updateUsers(toShareUserList);//chiamata a stub utente con valore lista degli utenti
                notifyClient.get(username).updateOnlineUsers(toShareOnlineUserList);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public HashMap<String, byte[]> getUserPassword() {
        return UserPassword;
    }

    public void setLoggedUsers(ArrayList<String> loggedUsers) {
        this.loggedUsers = loggedUsers;
    }
    public void setUserPassword(HashMap<String, byte[]> userPassword) {
        UserPassword = userPassword;
    }


    public void SaveRegistredUsers() throws FileNotFoundException, JsonProcessingException {
        File UsersList=new File("./UsersList");
        ObjectMapper objMapper=new ObjectMapper();
        FileOutputStream fos=new FileOutputStream(UsersList);
        byte[] toSave=objMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(this);
        try {
            fos.write(toSave);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ServerUserData usersRestoration() throws IOException {//return true se è stata ripristinata
        //false se non è stata mai salvata la lista degli utenti
        //si ripristina la lista degli utenti registrati(si suppone che non abbia senso far il backup
        //degli utenti loggati,dopotutto se il server fallisce verrebbere disconnessi come conseguenza
        File UsersList=new File("./UsersList");
        if(!UsersList.exists() &&
                !UsersList.isFile()) return null;//necessario creare un nuovo ServerUserData
        ByteBuffer byteBuffer=ByteBuffer.allocate(256);
        ObjectMapper objectMapper=new ObjectMapper();
        ArrayList<Byte> growingBuf=new ArrayList<>();

        JsonNode jnode = WORTHService.getJsonNode(byteBuffer, objectMapper, growingBuf, UsersList);//utilizzo in prestito metodo statico

        //viene restituito un ServerUserData con la lista degli utenti conservata

        ServerUserData serverUserData= objectMapper.convertValue(jnode,ServerUserData.class);//ripristinata la lista degli utenti registrati
        serverUserData.reSETKey();//reimpostiamo la secretkey al valore prima del salvataggio
        return serverUserData;
    }
}
