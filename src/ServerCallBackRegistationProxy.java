import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class ServerCallBackRegistationProxy extends UnicastRemoteObject implements ServerCallbackRegistration {
    //Classe fa da proxy nell'inserire i Client nella lista di quelli a cui fare callback sulla lista utente
    private ServerUserData serverUserData;
    public ServerCallBackRegistationProxy(ServerUserData serverUserData) throws RemoteException {
        super();
        this.serverUserData=serverUserData;
    }

    @Override
    public void callbackregistration(ClientNotifyInterface clientNotifyInterface,String username) throws RemoteException {
        //si inserirà in una struttura dati del server la stub inviata dal client
        serverUserData.callbackregistration(clientNotifyInterface,username);
    }

    @Override
    public void unregistercallback(String username) throws RemoteException {
        //da effettuare al logout
        serverUserData.unregistercallback(username);
    }
}
