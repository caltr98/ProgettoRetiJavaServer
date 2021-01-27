import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class ServerRegistrationProxy extends UnicastRemoteObject implements RegistrationInterface {
    //oggetto che si occupa di registrare un Utente al servizio,
    private ServerUserData serverUserData;
    public ServerRegistrationProxy(ServerUserData serverUserData) throws RemoteException{
        super();
        this.serverUserData=serverUserData;
    }
    @Override
    public int registration(String usrname, String password) throws RemoteException {
        //chiamata ad un oggetto interno al server per registrare utente in struttura dati del server
        return serverUserData.registration(usrname,password);
    }
}
