
import java.io.*;
import java.util.*;

public class EF {

    private int actualLVT;
    private ApplicationDCB App;
    public ArrayList InputRegisterList = new ArrayList();    // Lista de objetos contendo as informa��es sobre os atributos locais do federado (nome e tipo)
    public ArrayList InputAttributeQueue = new ArrayList();
    public int contElemIAQ = 0;
    public int lessTimestamp = 0;
    public String input;
    public PriorityQueue<Message> BufferReceivedMessages = new PriorityQueue<Message>(1, new Message());
    
    //vari�veis criadas por Guilherme Bizzani
    public int menorPredic = Integer.MAX_VALUE;
    private Map<String, Vector<Integer>> hashQt = new HashMap<String, Vector<Integer>>();
    
    /////////////////////////////////////////////////////////////////////////////////////
    public EF(ApplicationDCB A) throws IOException // construtor
    {
        App = A;
        actualLVT = 0;
        System.out.println("EF Inicializado...");
        
    }

    ////////////////////////////////////////////////////////////////////////////////////
    //   	SYNCHRONIZATION ( LVT / GVT )
    /////////////////////////////////////////////////////////////////////////////////////
    public String getLVT() // Retorna o LVT (Local Virtual Time)
    {
        return String.valueOf(actualLVT);
    }

    /////////////////////////////////////////////////////////////////////////////////////
    public synchronized String updateLVT(String newLVT) // Recebe o LVT do federado e atualiza LVT local
    {
        if (App.FederateType.compareTo("asynchronous") == 0) {
            actualLVT = Integer.parseInt(newLVT);
        } else {
            if (Integer.parseInt(newLVT) <= Integer.parseInt(App.NewDCB.getGVT())) {
                actualLVT = Integer.parseInt(newLVT);
            } else {
                // esta condição ocorre quando LVT do federado origem é > GVT,
                // então, a mensagem não é enviada, e o LVT é igualado ao GVT
                actualLVT = Integer.parseInt(App.NewDCB.getGVT());
            }
        }

        // Msg de atualização do GVT é enviada, desde que o federado não seja notime.
        // Isso reduz overhead mas exige que pelo menos um federado da federação seja sincrono
        if ((App.FederateType.compareTo("notime") != 0 ) && (App.FederateType.compareTo("asynchronous") !=0)){
            App.NewEDCB.Update1("444.2", String.valueOf(actualLVT), "0");
        }

//System.out.println("LVT do " + App.UniqueFederateID + ": " + actualLVT);

        if (lessTimestamp <= actualLVT &&
            //	actualLVT > oldLVT &&
            contElemIAQ > 0) {
            seekInputAttributeQueue();
        }
        // se um federado avan�a no tempo sem enviar msg,
        // um seek precisa ser executado na lista de entrada
        
        
        //meu codigo:
        //"chama" o redirect 3, que "for�a" o CHAT a criar um checkpoint.
        if(actualLVT >= menorPredic-100){//LVT chegou a predicao que foram feitas ao receber mensagens dos outros federados.
        	
        	System.out.println("Criando checkpoint com a menor predicao no lvt:" + actualLVT);
        	App.NewGateway.Redirect(3);
        	
        	menorPredic = Integer.MAX_VALUE;
        	
        }
        
        //codigo para "finalizar" a execu��o dos federados que sofreram rollbacks.
        
        if(App.FederateType.compareTo("synchronous") != 0){
        	if(actualLVT == 40000){
	        	System.out.println("Simula��o chegou no LVT 40000, finalizando processo....");
	        	
	        	System.out.println("GVT atual: "+App.NewDCB.getGVT());
	        	
	        	System.out.println("Total de anti-mensagens: "+App.NewEDCB.totalAntiMsg);
	        	
	        	return "-1";
        	
        	}
        }
        //fim
        
        
        
        return String.valueOf(actualLVT);
    }

    /////////////////////////////////////////////////////////////////////////////////////
    //     END OF SYNCHRONIZATION
    /////////////////////////////////////////////////////////////////////////////////////
    public void Decode(Message Msg) throws IOException // Decodifica a mensagem
    {

    	
    	
    	
        this.StoreReceivedMessages(Msg);

        InputAttribute AttributeTemp = null;
        String Source = Msg.FederationSource + Msg.FederateSource;
        //System.out.print(Msg.LVT + " ");
        AttributeTemp = new InputAttribute(Msg.AttributeID,
            Msg.Value,
            Source,
            Msg.LVT, // Fiz altera��o aqui (o LVT � o timestamp da msg)
            getAttributeType(Msg.AttributeID));
        
        if (Msg.AttributeID.compareTo("444.3") == 0) { //aqui onde recebe uma mensagem que causa rollback
            InputAttributeQueue.add(0, AttributeTemp);
            //System.out.println("adicionou na primeira posicao1");
            contElemIAQ++;
            seekInputAttributeQueue();
            int i = 0;
            
            //this.StoreReceivedMessages(Msg);

        } else if (Msg.AttributeID.compareTo("444.1") != 0) { //aqui � onde recebe a mensagem de outro chat
        	
        	
        	//modifica��es de Guilherme Bizzani
        	
        	
        	//c�digo para modificar o UID da mensagem, pois seu timestamp � menor que o LVT atual.
        	int lvtMsg = Integer.parseInt(Msg.LVT);
        	//int lvtChat = Integer.parseInt(actualLVT);
        	if(lvtMsg < actualLVT && Msg.Value.compareTo("") != 0){
        		
        		//System.out.println("Alterando o UID da mensagem para que se torne uma mensagem que cause rollback");
        		
        		AttributeTemp.uid = "444.3";
        		
                InputAttributeQueue.add(0, AttributeTemp);
                //System.out.println("adicionou na primeira posicao2");
                contElemIAQ++;
                seekInputAttributeQueue();
                
                //System.out.println("LVT atual, depois "+actualLVT);
                //updateLVT(Msg.LVT);
                return;
        	}
        	
        	
        	//c�digo para cria��o e manuten��o do HashMap, que mant�m o timestamp das ultimas 5 mensagens recebidas de cada outro chat
            //System.out.println("SOURCE:"+Source+".");
        	
            if(App.FederateType.compareTo("synchronous") != 0){
            	
            	if(Msg.Value.compareTo("") != 0){ //verifica se a mensagem n�o est� vazia
            		//atualiza ou cria o hashMap do Source atual
	            	if(hashQt.containsKey(Source)){ //caso j� exista o source no hashQt
		            	Vector<Integer> qt = new Vector<Integer>();
		            	qt = hashQt.get(Source);
		            	int tam = qt.size();
		            	//System.out.println("Tamanho do Vector do hash:"+tam);
		            	
		            	//c�digo para verificar caso j� tenha ocorrido rollback
		            	if(qt.get(tam-1) > Integer.parseInt(Msg.LVT)){//se tiver mensagens salvas no vetor com LVT maior que a recebida agora, quer dizer que realizou rollback
		            		//System.out.println("Entrou na verificacao de rollback das mensagens de predicao");
		            		int i = tam-1;
		            		while(qt.size() > 0 && qt.get(i) > Integer.parseInt(Msg.LVT)){
		            			//System.out.println("Removendo mensagem do vetor:"+qt.get(i));
		            			qt.removeElementAt(i);
		            			i--;
		            		}
		            	}
		            	

		            	if(tam < 5){ //caso n�o tenha 5 mensagens recebidas ainda, simplesmente adiciona a nova.
		            		//System.out.println("Tam eh menor que 5, simplesmente adicionou no Vector!");
		            		qt.add(Integer.parseInt(Msg.LVT));
		            		//System.out.println("Vector:"+qt);
		            	}else{//j� possui 5 mensagens, ent�o tem que excluir a mais antiga.
		            		//System.out.println("Vector antes do remove:"+qt);
							qt.removeElementAt(0);
							//System.out.println("Vector depois do remove:"+qt);
							qt.add(Integer.parseInt(Msg.LVT));
							//System.out.println("Vector depois do add:"+qt);
		            	}
		            	hashQt.put(Source, qt);
		            	
		            	//System.out.println("HashQT do source:"+Source+" atualizado para:"+);
		            }else{//criando um source novo.
		            	Vector<Integer> aux = new Vector<Integer>();
		            	aux.add(Integer.parseInt(Msg.LVT));
		            	hashQt.put(Source, aux);
		            	//System.out.println("Criou uma key do hash! key:"+Source+"VALOR COM VECTOR:"+hashQt.get(Source));
		            }
	            	int dif, aux;
	            	if(hashQt.get(Source).size() > 1){
	            		dif = hashQt.get(Source).get(hashQt.get(Source).size()-1) - hashQt.get(Source).get(0);
	            		aux = dif / (hashQt.get(Source).size());
	            	}else{
	            		aux = Integer.parseInt(Msg.LVT);
	            	}
	                aux += Integer.parseInt(Msg.LVT);
	                //System.out.println("Mensagem recebida do federado"+ Source +" previs�o:"+aux+" e tamanho do vector = "+hashQt.get(Source).size());
	                //System.out.println("Value:"+Msg.Value+".");
	                if(aux < menorPredic){
	                	menorPredic = aux;
	                	//System.out.println("Menor predicao atualizada:"+menorPredic+".");
	                }
	            }
            }
        	
        	//codigo original
        	
            InputAttributeQueue.add(AttributeTemp);
            //System.out.println("Stored Msg from:" + AttributeTemp.Source + " Attrib.:" + Msg.AttributeID + " Tstp.:" + Msg.LVT);
            contElemIAQ++; // controla o numero de elementos na lista de entrada
            seekInputAttributeQueue();

        } else {
            // Se AttributeID == "444.1", atualiza GVT do federado local - proveniente de broadcast de atualizacao
            // e o valor de Msg.Value carrega o novo GVT

            App.NewDCB.updateGVT(Msg.Value);
            if (App.FederateType.compareTo("notime") == 0) {
                if (contElemIAQ > 0) {
                    seekInputAttributeQueue(); // Verifica se h� msg's a serem enviadas antes de atualizar o GVT
                }
                updateLVT(Msg.Value);
            }
        // Avan�a o LVT de federados 'notime'. Como antes desta opera��o �
        // disparado um seek na fila de entrada, n�o existe o risco do LVT
        // ultrapassar o timestamp de mensagens ainda n�o retiradas da lista
        // de entrada e enviadas ao federado.
        //System.out.println("Novo GVT:" + App.NewDCB.getGVT());

        }
    }

    /////////////////////////////////////////////////////////////////////////////////////
    public void StoreReceivedMessages(Message ReceivedMessage) {
    	//System.out.println("FederateSource da mensagem atual:"+ReceivedMessage.FederateSource);
        
    	if ((ReceivedMessage.FederateSource.compareTo("444") != 0) && (ReceivedMessage.Value.compareTo("") != 0)) {
            BufferReceivedMessages.add(ReceivedMessage);
        }
        
    /*System.out.println("Inicio do BufferReceivedMessages");
    for (Message e:BufferReceivedMessages){
    System.out.println(e);
    }
    System.out.println("Fim do BufferReceivedMessages\n");*/
    }
    /////////////////////////////////////////////////////////////////////////////////////

    public synchronized void seekInputAttributeQueue() {
        InputAttribute Temp = null;
        InputAttribute TempNext = null;

        // Encontra Msg's com menor LVT na lista e encaminha para execu��o pelo federado.
        // executa a busca na lista mais de uma vez, pois a lista pode conter
        // mais de uma mensagem com timestamp <= GVT
        while (InputAttributeQueue.size() > 0) //for (int y = 0; y < InputAttributeQueue.size();y++)
        {
            //System.out.println("size:" + InputAttributeQueue.size());
            Temp = (InputAttribute) InputAttributeQueue.get(0);
            //System.out.println("Source:" + Temp.Source + " MsgTst:" + Temp.LVT +" LVT:" + actualLVT + " GVT:" + App.NewDCB.getGVT() + " LTt:" + lessTimestamp);
            for (int x = 1; x < InputAttributeQueue.size(); x++) {
                TempNext = (InputAttribute) InputAttributeQueue.get(x);
                if (Integer.parseInt(TempNext.LVT) < Integer.parseInt(Temp.LVT)) {
                    Temp = (InputAttribute) InputAttributeQueue.get(x);
                }
            //System.out.println("Source:" + TempNext.Source + " MsgTst:" + TempNext.LVT + " LVT:" + actualLVT +" GVT:" + App.NewDCB.getGVT() + " LTt:" + lessTimestamp);
            }
            lessTimestamp = Integer.parseInt(Temp.LVT);

            // se Temp.LVT come�ar com 0 ent�o � origin�rio de notime
            // incluir tratamento
            //System.out.print(Temp.LVT +" ");
            if (Temp.LVT.indexOf('0') == 0) {
                //System.out.println("TIMESTAMP DA MSG: "+lessTimestamp+" GVT:"+App.NewDCB.getGVT()+"TempUID:"+Temp.uid);
                App.NewGateway.Redirect(getProtocolConverterID(Temp.uid));
                if (App.FederateType.compareTo("notime") == 0) {
                    updateLVT(App.NewDCB.getGVT());
                }
            //System.out.print(Temp.LVT +" ** notime ** ");
            } // se Temp.LVT n�o come�ar com 0 ent�o � originario de sincrono
            //else if (lessTimestamp <= Integer.parseInt(App.NewDCB.getGVT()) && Temp.LVT.indexOf('0') != 0) {
            else if (lessTimestamp <= actualLVT && Temp.LVT.indexOf('0') != 0) {
            	//System.out.println("entrou no else if");
            	
                //System.out.println("TIMESTAMP DA MSG: "+lessTimestamp+" GVT:"+App.NewDCB.getGVT()+"TempUID:"+Temp.uid);

                // Marcador de tempo de inicio do tempo gasto para atualiza��o do display
                //	Calendar hoje = Calendar.getInstance();
                //	System.out.println("Mandando mensagem com timestamp "+Temp.LVT+" em: "+hoje.getTimeInMillis());
            	
            	if(Temp.uid.compareTo("444.3") == 0){	//esta situa��o ocorre quando o DCB "tenta" reproduzir uma mensagem que gerou rollback e ficou salva com
            											//LVT-1, por exemplo: Mensagem gerou rollback no 600, fica salva como: 599.
            											//O dcb chamava os m�todos de rollbacks, que por algum motivo n�o chegavam a completar o rollback
            											//ent�o est� sendo ignorado este rollback e removendo a mensagem da fila de atributos.
            		if(actualLVT - Integer.parseInt(Temp.LVT) < 10 ){
                		System.out.println("Nao vai chamar o protocol converter.");
                		System.out.println("LVT Atual: "+actualLVT+"LVT temp: "+Temp.LVT);
                		InputAttributeQueue.remove(Temp);
                		
                		break;
                	}
            	}
                App.NewGateway.Redirect(getProtocolConverterID(Temp.uid));
                if (App.FederateType.compareTo("notime") == 0) {
                    updateLVT(String.valueOf(lessTimestamp));
                }
            } else {
            	//System.out.println("entrou no ELSE!");
            	//System.out.println("LVT do Temp: "+Temp.LVT+" Valor do Temp: "+Temp.Value+" e UID: "+Temp.uid);
                //App.NewGateway.Redirect(getProtocolConverterID(Temp.uid));
                break;
            }
        }
    // Ao enviar a mens ao federado, o timestamp (Msg.LVT) da mens pode passar
    // a ser o novo LVT, se assim o federado desejar. O federado tamb�m pode
    // simplesmente armazenar o msg numa lista, e executar o evento mais tarde.
    // Se o federado precisar alterar novamente o LVT
    // de acordo com seu tempo interno (se possuir), ele poder� faze-lo atrav�s do m�todo
    // Gateway.updateLVT(...), respeitando o GVT.
    }

    /////////////////////////////////////////////////////////////////////////////////////
    public String getAttributeType(String uid) throws IOException {
        InputRegister InputRegisterTemp = null;

        for (int x = 0; x < InputRegisterList.size(); x++) {
            InputRegisterTemp = (InputRegister) InputRegisterList.get(x);
            if (uid.compareTo(String.valueOf(InputRegisterTemp.uid)) == 0) {
                break;
            } else {
                InputRegisterTemp = null;
            }
        }
        return InputRegisterTemp.type;
    }


    /////////////////////////////////////////////////////////////////////////////////////
    public int getProtocolConverterID(String uid) {
        String pc_id = "";
        char tmp = ' ';

        for (int i = 0; i < uid.length(); i++) {
            tmp = uid.charAt(i);
            if (tmp != '.') {
                pc_id = pc_id + tmp;
            } else {
                break;
            }
        }
        return Integer.parseInt(pc_id);
    }

    /////////////////////////////////////////////////////////////////////////////////////
    public InputAttribute getAttributeReceived(String uid) {
        InputAttribute Temp = null;

        for (int x = 0; x < InputAttributeQueue.size(); x++) {
            Temp = (InputAttribute) InputAttributeQueue.get(x);
            if (uid.compareTo(Temp.uid) == 0 && Integer.parseInt(Temp.LVT) == lessTimestamp) {
                // procura por msg's na lista de espera que tenham timestamp == ao LVT atual
                // se houver, elas s�o enviadas ao federado
                break;
            } else {
                Temp = null;
            }
        }

        return Temp;
    }

    /////////////////////////////////////////////////////////////////////////////////////
    public InputAttribute getAttributeReceived(String uid, String Source, String LVT) {
        InputAttribute Temp = null;

        for (int x = 0; x < InputAttributeQueue.size(); x++) {
            Temp = (InputAttribute) InputAttributeQueue.get(x);
            if (uid.compareTo(Temp.uid) == 0 && Source.compareTo(Temp.Source) == 0 && LVT.compareTo(Temp.LVT) == 0) {
                break;
            } else {
                Temp = null;
            }
        }
        return Temp;
    }

    /////////////////////////////////////////////////////////////////////////////////////
    public void AttributeRemove(InputAttribute AttribRemove) {
        for (int x = 0; x < InputAttributeQueue.size(); x++) {
            if (AttribRemove == (InputAttributeQueue.get(x))) {
                InputAttributeQueue.remove(x);
                contElemIAQ--;  // controla o numero de msg's na lista de entrada
                break;
            }
        }
    }
}