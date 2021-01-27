public class WORTH {
    private String djd;
    private  WORTHService worthService;
    private ServerUserData serverUserData;
    public WORTH(WORTHService worthService, ServerUserData serverUserData){
        this.worthService=worthService;
        this.serverUserData=serverUserData;
    };
    public String OPTODO(String operation,String userRequester) {
        /*Stringhe di esempio comandi che arriveranno da CLient
        String test0 = "0 login-\rnomeutente-\rpassword";
        String test1 = "1 createproject-\rProjectName";
        String test2 = "2 addmember\r-ProjectName-\rnickUtente";
        String test3 = "3 showmembers-\rProjectName";
        String test4 = "4 showcards-\rProjectName";
        String test5 = "5 showcard-\rProjectName-\rcardName";
        String test6 = "6 getcardhistory-\rProjectName-\rcardName";
        String test7 = "7 cancelproject-\rProjectName";
        String test8= "8 addcard-\rProjectName-\rCardName-\rcardDescription";
        String test9= "9 movecard-\rProjectName-\rcardName-\rsrcList-\rdstList";
        String test10="10 listProjects";
        String test11="11 readchat-\rProjectName";
        String test11="LOGINSUCCESS";
        String test12="WRONGPASSWORD";
        String test14="NOUSER";
        String test15="DOLOGIN";*/
        String[] opFields = operation.split("-\r");
        if (opFields != null) {
            if (opFields[0].equals("0 login")) {
                if (opFields.length != 3) {
                    return "Error Wrong Format request login " + operation;
                }
                return Login(userRequester, opFields[1], opFields[0]);//userRequest fin qui è un dummy value
            }
            else if (opFields[0].equals("1 createproject")) {
                if (opFields.length != 2) {
                    return "Error Wrong Format request createproject " + operation;
                }
                return OPCreateProject(userRequester, opFields[1]);
            } else if (opFields[0].equals("2 addmember")) {
                if (opFields.length != 3) {
                    return "Error Wrong Format request addmember " + operation;
                }
                return OPAddMember(userRequester,opFields[1],opFields[2]);
            }
            else if (opFields[0] .equals( "3 showmembers")) {
                if (opFields.length != 2) {
                    return "Error Wrong Format request showmembers " + operation;
                }
                return OPShowMembers(userRequester,opFields[1]);
            }else if (opFields[0] .equals( "4 showcards")) {
                if (opFields.length != 2) {
                    return "Error Wrong Format request showcards " + operation;
                }
                return OPShowCards(userRequester,opFields[1]);
            }else if (opFields[0].equals( "5 showcard")) {
                if (opFields.length != 3) {
                    return "Error Wrong Format request showcard " + operation;
                }
                return OPShowCard(userRequester,opFields[1],opFields[2]);
            }else if (opFields[0] .equals("6 getcardhistory")) {
                if (opFields.length != 3) {
                    return "Error Wrong Format request getcardhistory " + operation;
                }
                return OPGetCardHistory(userRequester,opFields[1],opFields[2]);
            }
            else if (opFields[0] .equals("7 cancelproject")) {
                if (opFields.length != 2) {
                    return "Error Wrong Format request addmember " + operation;
                }
                return OPCancelProject(userRequester,opFields[1]);
            }
            else if (opFields[0].equals("8 addcard")) {
                if (opFields.length != 4) {
                    return "Error Wrong Format request addmember " + operation;
                }
                return OPAddCard(userRequester,opFields[1],opFields[2],opFields[3]);
            }
            else if (opFields[0] .equals("9 movecard")) {
                if (opFields.length != 5) {
                    return "Error Wrong Format request addmember " + operation;
                }
                return OPMoveCard(userRequester,opFields[1],opFields[2],opFields[3],opFields[4]);
            }
            else if(opFields[0].equals("10 listprojects")) {
                if (opFields.length != 1) {
                    return "Error Wrong Format request listProjects " + operation;
                }
                //Non esiste una struttura che contiene le associazioni utenti->progetti
                //meno costo in spazio di memoria, costo computazionale nel cercare fra tutti i progetti se l'utente
                //ne è membro o meno
                return OPListProjects(userRequester);
            }
            else if (opFields[0] .equals( "11 readchat")) {
                if (opFields.length != 2) {
                        return "Error Wrong Format request readchat " + operation;
                    }
                    return OPReadChat(userRequester,opFields[1]);
            }
            //operazioni di risposta al login
            else if (opFields[0] .equals("NOUSER")) {
                return opFields[0];
            }
            else if (opFields[0] .equals("WrongPassword")) {
                return opFields[0];
            }
            else if (opFields[0] .equals("LOGINSUCCESS")) {
                return opFields[0];
            }
            else if (opFields[0] .equals("NoUser")) {
                return opFields[0];
            }



            else return "Operation NOT Supported: "+opFields[0];
        }
        return "NULL string response";
    }

    private String OPReadChat(String userRequester, String ProjectName) {
        String result;
        try{
            result=worthService.readChat(ProjectName,userRequester);
        }catch (IllegalArgumentException e){
            return e.getMessage();
        }
        if(result!=null){
            return result;
        }
        else{
            return "Project non esiste";
        }

    }

    private String OPListProjects(String userRequester) {
        String[] result;
        try{
            result=worthService.listProjects(userRequester);
        }catch (IllegalArgumentException e){
            return e.getMessage();
        }
        if(result!=null){
            return String.join("\u2407",result);//restituisco in un'unica stringa con delimitatore \u2407\r la lista dei progetti
        }
        else{
            return "NO PROJECTS";
        }

    }

    protected String OPCreateProject(String userRequester,String ProjectName)  {
        int result=-1;
        try{
            result=worthService.createProject(ProjectName,userRequester);
            System.out.println("risultato creazione progetto: "+result);
        }catch (IllegalArgumentException e){
            return e.getMessage();
        } catch (Exception e) {
            e.printStackTrace();
            return "No more Chat Adressed";//non è stato possibile creare il Progetto perchè non ci sono più MulticastSocket
        }
        if(result==1){
            return "OK";
        }
        else return "ProjectName già in uso";
    }
    protected String OPAddMember(String userRequester,String ProjectName,String newmember){
        int result;
        if(!serverUserData.isRegistered(newmember)){
            return "Utente non registrato";
        }
        try{
             result=worthService.addMemberProject(ProjectName,userRequester,newmember);
        }catch (IllegalArgumentException e){
            return e.getMessage();
        }
        if(result==1){
            return "OK";
        }
        else if(result==2){
            return "Already Member";
        }
        else if(result==-1) return "Project cancellato recentemente";
        else return "Project non esiste";
    }

    protected String OPShowMembers(String userRequester,String ProjectName){
        //Piuttosto che restituire un Array Di Stringhe restituisco una stringa unica con i nomi utente separati da '\u2407'
        String[] result;
        try{
            result=worthService.showMembers(ProjectName,userRequester);
        }catch (IllegalArgumentException e){
            return e.getMessage();
        }
        if(result!=null){
            return String.join("\u2407",result);//restituisco in un'unica stringa con delimitatore "-\u2407" la lista degli utenti
        }
        else{
            return "Project non esiste";
        }
    }
    protected String OPShowCards(String userRequester,String ProjectName){
        //Piuttosto che restituire un Array Di Stringhe restituisco una stringa unica con i titoli card separati da '\u2407'
        String[] result;
        try{
            result=worthService.showCards(ProjectName,userRequester);

        }catch (IllegalArgumentException e){
            return e.getMessage();
        }
        if(result!=null){
            return String.join("\u2407",result);//restituisco in un'unica stringa con delimitatore
        }
        else{
            return "Project non esiste";
        }

    }
    protected String OPShowCard(String userRequester,String ProjectName,String card){
        String result;
        try{
            result= worthService.showCard(ProjectName,userRequester,card);
            //restituite informazioni nel formato 'cardname descprition listacorrente' con ogni informazione separata da spazio
            //Se una card non esiste viene restituita stringa che lo indica
            //worthService.SaveState();
        }catch (IllegalArgumentException e){
            return e.getMessage();
        }
        if(result!=null){
            return result;
        }
        else{
            return "Project non esiste";//Caso in cui Project specificato non è presente
        }
    }
    protected String OPGetCardHistory(String userRequester,String ProjectName,String card){
        String result;
        try{
            result=worthService.getCardHistory(ProjectName,userRequester,card);
        }catch (IllegalArgumentException e){
            return e.getMessage();
        }

        return result;

    }
    protected String OPCancelProject(String userRequester,String ProjectName){
            int result;
            try{
                result=worthService.deleteProject(ProjectName,userRequester);
            }catch (IllegalArgumentException e){
                return e.getMessage();
            }
            if(result==1){
                return "OK";
            }
            else if(result==-1){
                return "Card in liste non DONE";
            }
            else return "ProjectName non esiste";
    }
    protected String OPAddCard(String userRequester,String ProjectName,String cardName,String cardDescription){
        int result;
        try{
            result=worthService.addCard(ProjectName,userRequester,cardName,cardDescription);
        }catch (IllegalArgumentException e){
            return e.getMessage();
        }
        if(result==1){
            return "OK";
        }
        else if(result==0){
            return "Project contiene cardName";
        }
        else return "Project non esiste";
    }
    protected String OPMoveCard(String userRequester,String ProjectName,String cardName,String srcList,String dstList){
        String result;
        try{
            result=worthService.moveCard(userRequester,ProjectName,cardName,srcList,dstList);
        }catch (IllegalArgumentException e){
            return e.getMessage();
        }
        if(result!=null){
            return result;
        }
        else return "Project non esiste";
    }

    private String Login(String userRequester, String username, String password) {
        return "Utente già loggato";
    }
}
