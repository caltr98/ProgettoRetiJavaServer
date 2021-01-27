import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface ClientNotifyInterface extends Remote {
    //Interfaccia per permettere l'aggiornamento delle liste utenti del client da parte del server(con callback)
    public void updateUsers (ArrayList<String> Users) throws RemoteException;
    public void updateOnlineUsers (ArrayList<String> onlineUsers) throws RemoteException ;

}
