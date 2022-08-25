package br.ufma.lsdi.digitalphenotyping.phenotypecomposer;

import static br.ufma.lsdi.digitalphenotyping.CompositionMode.FREQUENCY;
import static br.ufma.lsdi.digitalphenotyping.CompositionMode.GROUP_ALL;
import static br.ufma.lsdi.digitalphenotyping.CompositionMode.SEND_WHEN_IT_ARRIVES;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import br.ufma.lsdi.cddl.CDDL;
import br.ufma.lsdi.cddl.ConnectionFactory;
import br.ufma.lsdi.cddl.listeners.IConnectionListener;
import br.ufma.lsdi.cddl.listeners.ISubscriberListener;
import br.ufma.lsdi.cddl.message.Message;
import br.ufma.lsdi.cddl.network.ConnectionImpl;
import br.ufma.lsdi.cddl.pubsub.Publisher;
import br.ufma.lsdi.cddl.pubsub.PublisherFactory;
import br.ufma.lsdi.cddl.pubsub.Subscriber;
import br.ufma.lsdi.cddl.pubsub.SubscriberFactory;
import br.ufma.lsdi.digitalphenotyping.CompositionMode;
import br.ufma.lsdi.digitalphenotyping.Topics;
import br.ufma.lsdi.digitalphenotyping.dataprocessor.digitalphenotypeevent.Situation;
import br.ufma.lsdi.digitalphenotyping.dpmanager.database.DatabaseManager;
import br.ufma.lsdi.digitalphenotyping.phenotypecomposer.base.DigitalPhenotype;
import br.ufma.lsdi.digitalphenotyping.phenotypecomposer.base.DistributePhenotypeWork;
import br.ufma.lsdi.digitalphenotyping.phenotypecomposer.base.PublishPhenotype;
import br.ufma.lsdi.digitalphenotyping.phenotypecomposer.base.SaveConnection;
import br.ufma.lsdi.digitalphenotyping.phenotypecomposer.database.Phenotypes;
import br.ufma.lsdi.digitalphenotyping.phenotypecomposer.handlingexceptions.InvalidConnectionBroker;

public class PhenotypeComposer extends Service {
    private static final String TAG = PhenotypeComposer.class.getName();
    private Publisher publisher = PublisherFactory.createPublisher();
    private Subscriber subRawDataInferenceResult;
    private Subscriber subConfigurationInformation;
    private Subscriber subCompositionMode;
    private Subscriber subActiveDataProcessor;
    private Subscriber subDeactivateDataProcessor;
    private Context context;
    private PublishPhenotype publishPhenotype;
    private TextView messageTextView;
    private ConnectionImpl connectionBroker;
    private String statusConnection = "";
    private CompositionMode lastCompositionMode = SEND_WHEN_IT_ARRIVES;
    private int lastFrequency = 15;
    private List<String> nameActiveDataProcessors = new ArrayList();
    private List<Boolean> activeDataProcessors = new ArrayList();
    DatabaseManager databaseManager;
    private WorkManager workManager;
    private SaveConnection saveConnection = new SaveConnection();

    @Override
    public void onCreate() {
        try {
            Log.i(TAG, "#### Started PhenotypeComposer Service");
            context = this;

            //Receives data inferred by DataProcessors
            subRawDataInferenceResult = SubscriberFactory.createSubscriber();
            subRawDataInferenceResult.addConnection(CDDL.getInstance().getConnection());

            // Monitor the Configuration Information
            subConfigurationInformation = SubscriberFactory.createSubscriber();
            subConfigurationInformation.addConnection(CDDL.getInstance().getConnection());

            // Monitor the CompositionMode
            subCompositionMode = SubscriberFactory.createSubscriber();
            subCompositionMode.addConnection(CDDL.getInstance().getConnection());

            // Monitor the Active DataProcessor
            subActiveDataProcessor = SubscriberFactory.createSubscriber();
            subActiveDataProcessor.addConnection(CDDL.getInstance().getConnection());

            // Monitor the Deactivate Processors
            subDeactivateDataProcessor = SubscriberFactory.createSubscriber();
            subDeactivateDataProcessor.addConnection(CDDL.getInstance().getConnection());

            messageTextView = new TextView(context);

            databaseManager = DatabaseManager.getInstance(getApplicationContext());
        }catch (Exception e){
            Log.e(TAG,"Error: " + e.toString());
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "#### CONFIGURATION PHENOTYPECOMPOSER SERVICE");

        subscribeMessageRawDataInferenceResult(Topics.INFERENCE_TOPIC.toString());
        subscribeMessageConfigurationInformation(Topics.CONFIGURATION_INFORMATION_TOPIC.toString());
        subscribeMessageCompositionMode(Topics.COMPOSITION_MODE_TOPIC.toString());

        subscribeMessageAtiveDataProcessor(Topics.ACTIVE_DATAPROCESSOR_TOPIC.toString());
        subscribeMessageDeactivateDataProcessor(Topics.DEACTIVATE_DATAPROCESSOR_TOPIC.toString());

        //manager(lastCompositionMode, lastFrequency);

        publishMessage(Topics.MAINSERVICE_CONFIGURATION_INFORMATION_TOPIC.toString(), "alive");

        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        if(databaseManager.getInstance().getDB().isOpen()) {
            databaseManager.getInstance().getDB().close();
        }
        super.onDestroy();
    }


    public final IBinder mBinder = new LocalBinder();


    public class LocalBinder extends Binder {
        public PhenotypeComposer getService() {
            return PhenotypeComposer.this;
        }
    }


    @Override
    public IBinder onBind(Intent intent) { return mBinder; }


    public boolean connectionBroker(@NonNull String hostServer,@NonNull String port,@NonNull String username,@NonNull String password,@NonNull String clientID){
        try {
            Log.i(TAG,"#### Configuration Broker Server");
            Log.i(TAG,"#### HostServer:" + hostServer);
            Log.i(TAG,"#### Port:" + port);
            Log.i(TAG,"#### Username: " + username);
            Log.i(TAG,"#### Password: " + password);
            Log.i(TAG,"#### ClientID: " + clientID);

            //String host = "broker.hivemq.com";
            //String host = "192.168.0.7";
            connectionBroker = ConnectionFactory.createConnection();
            connectionBroker.setClientId(clientID);
            connectionBroker.setHost(hostServer);
            connectionBroker.setPort(port);
            connectionBroker.addConnectionListener(connectionListener);

            long automaticReconnectionTime = 1000L;
            int connectionTimeout = 30;
            int keepAliveInterval = 60;
            boolean automaticReconnection = true;
            boolean publishConnectionChangedStatus = false;
            int maxInflightMessages = 10;
            int mqttVersion =3;

            if(username.equals("username")) {
                connectionBroker.connect("tcp", hostServer, port, automaticReconnection, automaticReconnectionTime, false, connectionTimeout,
                        keepAliveInterval, publishConnectionChangedStatus, maxInflightMessages, "", "", mqttVersion);
            }
            else{
                connectionBroker.connect("tcp", hostServer, port, automaticReconnection, automaticReconnectionTime, false, connectionTimeout,
                        keepAliveInterval, publishConnectionChangedStatus, maxInflightMessages, username, password, mqttVersion);
            }

            Log.i(TAG,"#### CONECTADO: " + connectionBroker.isConnected());
            if(!connectionBroker.isConnected()){
                Log.d(TAG,"#### Failed to connect to broker.");
                return false;
                //connectionBroker.reconnect();
            }
            else if(connectionBroker.isConnected()) {
                // Conectado com o broker, configura o publicador (PublishPhenotype).
                publishPhenotype = new PublishPhenotype(context, connectionBroker);
                saveConnection.getInstance().setActivity(connectionBroker);
            }
        }catch (Exception e){
            Log.e(TAG,"#### Error: " + e.getMessage());
            return false;
        }
        return true;
    }


    public boolean isConnectedBroker(){
        if(!connectionBroker.isConnected()){
            return false;
        }
        return true;
    }


    public void manager(CompositionMode compositionMode, int frequency){
        Log.i(TAG,"#### Manager PhenotypeComposer");
        if(lastCompositionModeDifferent()){
            if(compositionMode == SEND_WHEN_IT_ARRIVES){

            }
            else if(compositionMode == GROUP_ALL){

            }
            else if(compositionMode == FREQUENCY){
                startWorkManager();
            }
        }
    }


    public boolean lastCompositionModeDifferent(){
        if(false){
            return false;
        }
        return true;
    }


    public void startWorkManager(){
        Log.i(TAG,"#### Started WorkManager");
        // Adicionamos restrições ao Work: 1 - esteja conectado a internet,
        //                                  2 - o nível de bateria não pode estar baixa.
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build();

        // O Work executa periodicamente, caso uma das exigências não for atendida, tenta executar (exponencial ou linear) novamente o Work.
        PeriodicWorkRequest saveRequest =
                new PeriodicWorkRequest.Builder(DistributePhenotypeWork.class, this.lastFrequency, TimeUnit.MINUTES)
                        .addTag("distributephenotypework")
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                                TimeUnit.SECONDS)
                        .build();

        WorkManager.getInstance(this.context)
                .enqueue(saveRequest);

        workManager = WorkManager.getInstance(this.context);
    }


    public void stopWorkManager(){
        workManager.cancelAllWorkByTag("distributephenotypework");
    }


    private IConnectionListener connectionListener = new IConnectionListener() {
        @Override
        public void onConnectionEstablished() {
            statusConnection = "Established connection.";
            messageTextView.setText("Conexão estabelecida.");
            Log.i(TAG,"#### Status CDDL: " + statusConnection);
        }

        @Override
        public void onConnectionEstablishmentFailed() {
            statusConnection = "Failed connection.";
            messageTextView.setText("Falha na conexão.");
            Log.i(TAG,"#### Status MQTT: " + statusConnection);
        }

        @Override
        public void onConnectionLost() {
            statusConnection = "Lost connection.";
            messageTextView.setText("Conexão perdida.");
            Log.i(TAG,"#### Status MQTT: " + statusConnection);
        }

        @Override
        public void onDisconnectedNormally() {
            statusConnection = "A normal disconnect has occurred.";
            messageTextView.setText("Uma disconexão normal ocorreu.");
            Log.i(TAG,"#### Status MQTT: " + statusConnection);
        }
    };


    public void subscribeMessageRawDataInferenceResult(String serviceName) {
        subRawDataInferenceResult.subscribeServiceByName(serviceName);
        subRawDataInferenceResult.setSubscriberListener(subscriberRawDataInferenceResultListener);
        subRawDataInferenceResult.subscribeTopic(Topics.INFERENCE_TOPIC.toString());
    }


    public void subscribeMessageConfigurationInformation(String serviceName) {
        subConfigurationInformation.subscribeServiceByName(serviceName);
        subConfigurationInformation.setSubscriberListener(subscriberConfigurationInformation);
        subConfigurationInformation.subscribeTopic(Topics.CONFIGURATION_INFORMATION_TOPIC.toString());
    }


    public void subscribeMessageCompositionMode(String serviceName) {
        subCompositionMode.subscribeServiceByName(serviceName);
        subCompositionMode.setSubscriberListener(subscriberCompositionModeListener);
        subCompositionMode.subscribeTopic(Topics.COMPOSITION_MODE_TOPIC.toString());
    }


    public void subscribeMessageAtiveDataProcessor(String serviceName) {
        subActiveDataProcessor.subscribeServiceByName(serviceName);
        subActiveDataProcessor.setSubscriberListener(subscriberActiveDataProcessorsListener);
    }


    public void subscribeMessageDeactivateDataProcessor(String serviceName) {
        subDeactivateDataProcessor.subscribeServiceByName(serviceName);
        subDeactivateDataProcessor.setSubscriberListener(subscriberDeactivateDataProcessorsListener);
    }


    Situation digitalPhenotypeEvent = new Situation();
    Phenotypes phenotype = new Phenotypes();
    DigitalPhenotype digitalPhenotype = new DigitalPhenotype();
    String stringPhenotype = "";
    Situation dpe = new Situation();
    Phenotypes phenotypes = new Phenotypes();
    Phenotypes phenotypesFreq = new Phenotypes();
    public ISubscriberListener subscriberRawDataInferenceResultListener = new ISubscriberListener() {
        @Override
        public void onMessageArrived(Message message) {
            try {
                Object[] valor = message.getServiceValue();
                String mensagemRecebida = StringUtils.join(valor, ", ");
                digitalPhenotypeEvent = objectFromString(mensagemRecebida);

                if (lastCompositionMode == SEND_WHEN_IT_ARRIVES) { //Publish Situation
                    if(!isConnectedBroker()){
                        throw new InvalidConnectionBroker("#### Error: Failed to connect to broker.");
                    }
                    publishPhenotype.getInstance().publishPhenotypeComposer(digitalPhenotypeEvent);
                } else if (lastCompositionMode == GROUP_ALL) {

                    int t = databaseManager.getInstance().getDB().phenotypeDAO().total();
                    Log.i(TAG,"#### tt total_antes: " + t);

                    //Save phenotype
                    phenotypes.setPhenotype(mensagemRecebida);
                    databaseManager.getInstance().getDB().phenotypeDAO().insertAll(phenotypes);
                    Log.i(TAG,"#### tt SALVA");
                    Log.i(TAG,"#### tt mensagemRecebida: " + mensagemRecebida);
                    int total = databaseManager.getInstance().getDB().phenotypeDAO().total();
                    Log.i(TAG,"#### tt total_depois: " + total);

                    int position = nameActiveDataProcessors.indexOf(digitalPhenotypeEvent.getDataProcessorName());
                    activeDataProcessors.set(position, true);

                    if (activeDataProcessors.size() != 0) {
                        if (!activeDataProcessors.isEmpty()) {
                            boolean all = true;
                            if (!activeDataProcessors.contains(false)) {
                                all = true;
                                Log.i(TAG,"#### tt TRUE");
                            } else {
                                all = false;
                            }
                            /*for (int i = 0; i <= activeDataProcessors.size(); i++) {
                                if (!activeDataProcessors.get(i).booleanValue()) {
                                    if (!activeDataProcessors.contains(false)) {
                                        all = true;
                                        Log.i(TAG,"#### tt TRUE");
                                    } else {
                                        all = false;
                                    }
                                    break;
                                }
                                break;
                            }*/
                            if (all) {
                                Log.i(TAG,"#### tt VAI IMPRIMIR");
                                digitalPhenotype.setSituationList(digitalPhenotypeEvent);

                                // Retrieve information
                                phenotype = databaseManager.getInstance().getDB().phenotypeDAO().findByPhenotypeAll();
                                while (phenotype != null) {
                                    stringPhenotype = phenotype.getPhenotype();
                                    dpe = phenotype.getObjectFromString(stringPhenotype);

                                    digitalPhenotype.setSituationList(dpe);

                                    // Remove from database
                                    databaseManager.getInstance().getDB().phenotypeDAO().delete(phenotype);

                                    phenotype = databaseManager.getInstance().getDB().phenotypeDAO().findByPhenotypeAll();
                                }
                                if (digitalPhenotype.getSituationList().size() > 0) {
                                    // Publish the information
                                    publishPhenotype.getInstance().publishPhenotypeComposer(digitalPhenotype);
                                }

                                for (int j = 0; j < activeDataProcessors.size(); j++) {
                                    activeDataProcessors.set(j, false);
                                }
                            } /*else {
                                *//*Log.i(TAG,"#### tt SALVA");
                                //Save phenotype
                                phenotypes.setPhenotype(mensagemRecebida);
                                Log.i(TAG,"#### mensagemRecebida: " + mensagemRecebida);
                                databaseManager.getInstance().getDB().phenotypeDAO().insertAll(phenotypes);*//*
                            }*/
                        }
                    } else {
                        if(!isConnectedBroker()){
                            throw new InvalidConnectionBroker("#### Error: Failed to connect to broker.");
                        }

                        Log.i(TAG,"#### tt DIRETO");
                        //Publish Situation
                        publishPhenotype.getInstance().publishPhenotypeComposer(digitalPhenotypeEvent);
                    }
                } else if (lastCompositionMode == FREQUENCY) {
                    //Save phenotype
                    phenotypesFreq.setPhenotype(mensagemRecebida);
                    databaseManager.getInstance().getDB().phenotypeDAO().insertAll(phenotypesFreq);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };


    public final ISubscriberListener subscriberConfigurationInformation = new ISubscriberListener() {
        @Override
        public void onMessageArrived(Message message) {
            Object[] valor = message.getServiceValue();
            String mensagemRecebida = StringUtils.join(valor, ",");
            String[] separated = mensagemRecebida.split(",");

            connectionBroker(separated[0], separated[1], separated[2], separated[3], separated[4]); // Values are already checked for null in DPManager.

        }
    };


    public final ISubscriberListener subscriberCompositionModeListener = new ISubscriberListener() {
        @Override
        public void onMessageArrived(Message message) {
            //Log.i(TAG, "#### Read messages (subscriber CompositionMode):  " + message);
            Object[] valor = message.getServiceValue();
            String mensagemRecebida = StringUtils.join(valor, ", ");
            String[] separated = mensagemRecebida.split(",");
            String atividade1 = String.valueOf(separated[0]);

            lastCompositionMode = CompositionMode.valueOf(atividade1);
            Double n = (Double) valor[1];
            lastFrequency = n.intValue();
            Log.i(TAG, "#### Value lastCompositionMode: " + lastCompositionMode.name().toString() + ", Value frequency: " + lastFrequency);

            manager(lastCompositionMode, lastFrequency);
        }
    };


    public ISubscriberListener subscriberActiveDataProcessorsListener = new ISubscriberListener() {
        @Override
        public void onMessageArrived(Message message) {
            Object[] valor = message.getServiceValue();
            String mensagemRecebida = StringUtils.join(valor, ", ");
            String[] separated = mensagemRecebida.split(",");
            String atividade = String.valueOf(separated[0]);

            if(!atividade.equals("RawDataCollector")) {
                Log.i(TAG, "#### Pheno active Processor:  " + atividade);
                nameActiveDataProcessors.add(atividade);
                activeDataProcessors.add(false);
                Log.i(TAG, "#### Pheno active Processor Total:  " + nameActiveDataProcessors);
            }
        }
    };


    public ISubscriberListener subscriberDeactivateDataProcessorsListener = new ISubscriberListener() {
        @Override
        public void onMessageArrived(Message message) {
            Object[] valor = message.getServiceValue();
            String mensagemRecebida = StringUtils.join(valor, ", ");
            String[] separated = mensagemRecebida.split(",");
            String atividade = String.valueOf(separated[0]);

            if(!atividade.equals("RawDataCollector")) {
                Log.i(TAG, "#### Read messages (deactivate processor):  " + message);
                nameActiveDataProcessors.remove(atividade);
                activeDataProcessors.remove(false);
            }
        }
    };


    public void publishMessage(String service, String text) {
        publisher.addConnection(CDDL.getInstance().getConnection());

        Message message = new Message();
        message.setServiceName(service);
        message.setServiceValue(text);
        publisher.publish(message);
    }

    public String stringFromObject(Message msg){
        Gson gson = new Gson();
        String jsonString = gson.toJson(msg);
        return jsonString;
    }

    public Situation objectFromString(String jsonString){
        Type listType = new TypeToken<Situation>(){}.getType();
        Situation situationEvent = new Gson().fromJson(jsonString, listType);
        return situationEvent;
    }
}
