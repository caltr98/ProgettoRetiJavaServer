import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerCallbackRegistration extends Remote {
    //Interfaccia per la registrazione del Client al servizio di aggiornamento delle liste Utente
    public void callbackregistration(ClientNotifyInterface clientNotifyInterface,String username)throws RemoteException;
    public void unregistercallback(String username)throws RemoteException;
}
