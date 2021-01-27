import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RegistrationInterface extends Remote {
    //Interfaccia per il metodo di registrazione da parte del client

    //Registration return=1 Registrazione con successo |IllegalArgumentException username già registrato
    //NullPointerException uno dei due campi password o usrname sono null
    public int registration(String usrname,String password) throws RemoteException,IllegalArgumentException,NullPointerException;
}
