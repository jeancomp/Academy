package com.jp.myapplication;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import br.ufma.lsdi.cddl.CDDL;
import br.ufma.lsdi.cddl.listeners.ISubscriberListener;
import br.ufma.lsdi.cddl.pubsub.Subscriber;
import br.ufma.lsdi.cddl.pubsub.SubscriberFactory;
import br.ufma.lsdi.digitalphenotyping.CompositionMode;
import br.ufma.lsdi.digitalphenotyping.SaveActivity;
import br.ufma.lsdi.digitalphenotyping.Topics;
import br.ufma.lsdi.digitalphenotyping.dpmanager.DPManager;
import br.ufma.lsdi.digitalphenotyping.dpmanager.database.FrameworkOnOff;
import br.ufma.lsdi.digitalphenotyping.dpmanager.database.FrameworkOnOffManager;
import br.ufma.lsdi.digitalphenotyping.dpmanager.handlingexceptions.InvalidActivityException;
import br.ufma.lsdi.digitalphenotyping.dpmanager.handlingexceptions.InvalidCompositionModeException;
import br.ufma.lsdi.digitalphenotyping.dpmanager.handlingexceptions.InvalidDataProcessorNameException;
import br.ufma.lsdi.digitalphenotyping.dpmanager.handlingexceptions.InvalidFrequencyException;
import br.ufma.lsdi.digitalphenotyping.dpmanager.handlingexceptions.InvalidHostServerException;
import br.ufma.lsdi.digitalphenotyping.dpmanager.handlingexceptions.InvalidMainServiceException;
import br.ufma.lsdi.digitalphenotyping.dpmanager.handlingexceptions.InvalidPasswordException;
import br.ufma.lsdi.digitalphenotyping.dpmanager.handlingexceptions.InvalidPortException;
import br.ufma.lsdi.digitalphenotyping.dpmanager.handlingexceptions.InvalidUsernameException;
import br.ufma.lsdi.digitalphenotyping.mainservice.MainService;
import br.ufma.lsdi.digitalphenotyping.processormanager.services.database.active.ActiveDataProcessor;
import br.ufma.lsdi.digitalphenotyping.processormanager.services.database.active.ActiveDataProcessorManager;

public class MainActivity2 extends AppCompatActivity {
    private static final String TAG = MainActivity2.class.getName();
    private ActiveDataProcessorManager activeDataProcessorManager;
    private FrameworkOnOffManager frameworkStatus;
    private List<String> listProcessors = new ArrayList();
    private RecyclerViewAdapter adapter;
    private MainService myService;
    private DPManager dpManager;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView textLoad;
    private TextView txtStatus;
    private View button_init;
    private View fab;
    private Notification notification;
    private boolean flag_on_off = false;
    private Vibrator vibe;
    private Activity activity;
    private SaveActivity saveActivity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        Toolbar toolbar = findViewById(R.id.toolbar2);
        setSupportActionBar(toolbar);
        activity = (Activity) this;
        frameworkStatus = new FrameworkOnOffManager(this);
        activeDataProcessorManager = new ActiveDataProcessorManager(this);
        saveActivity = new SaveActivity(this);
        recyclerView = findViewById(R.id.recyclerview_fragment_main_list);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm);
        textLoad = findViewById(R.id.error_msg);
        txtStatus = findViewById(R.id.txtStatus);
        progressBar = findViewById(R.id.progress_bar);
        button_init = findViewById(R.id.button_init);
        button_init.setOnClickListener(clickListener);
        fab = findViewById(R.id.fab);
        fab.setOnClickListener(clickListener);
        vibe = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
        new AddItemTaskGet().execute(); // verifica status do framework on/off

//        listProcessors.add("RawDataCollector");
//        listProcessors.add("Mobility");
//        listProcessors.add("Sociability");
//        listProcessors.add("Sleep");

        try {
            startFramework();
        } catch (InvalidCompositionModeException e) {
            e.printStackTrace();
        } catch (InvalidPortException e) {
            e.printStackTrace();
        } catch (InvalidUsernameException e) {
            e.printStackTrace();
        } catch (InvalidHostServerException e) {
            e.printStackTrace();
        } catch (InvalidActivityException e) {
            e.printStackTrace();
        } catch (InvalidFrequencyException e) {
            e.printStackTrace();
        } catch (InvalidPasswordException e) {
            e.printStackTrace();
        }
    }


    public void startFramework() throws InvalidCompositionModeException, InvalidPortException, InvalidUsernameException, InvalidHostServerException, InvalidActivityException, InvalidFrequencyException, InvalidPasswordException {
        dpManager = new DPManager.Builder(this)
                .setExternalServer("192.168.0.7", "1883")
                //.setExternalServer("broker.hivemq.com","1883")
                .setCompositionMode(CompositionMode.SEND_WHEN_IT_ARRIVES)
                //.setFrequency(15)
                .build();
        dpManager.getInstance().start();
    }


    @Override
    public void onStart() {
        super.onStart();

        try {
            Intent intent = new Intent(this, MainService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "#### Error: " + e.getMessage());
        }
    }


    @Override
    public void onStop() {
        super.onStop();
    }


    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.i(TAG, "#### Connection service MainService");
            MainService.LocalBinder binder = (MainService.LocalBinder) iBinder;
            myService = binder.getService();

            try {
                dpManager.getInstance().setMainService(myService);
            } catch (InvalidMainServiceException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "#### Disconnection service MainService");
        }
    };


    public View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.button_init: {
                    try {
                        if(!flag_on_off){
                            processValue(true);
                            new AddItemTaskSet().execute(true);
                            if(notification == null) {
                                notification = new Notification();
                            }
                            dpManager.getInstance().foregroundAPP();
                            vibe.vibrate(50);
                        }
                        else {
                            processValue(false);
                            new AddItemTaskSet().execute(false);
                            new AddItemTaskOn().execute();  // limpa o banco com os nomes dos DataProcessor ativos, ficando vazio.
                            new AddItemTaskClear().execute();
                            vibe.vibrate(50);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case R.id.fab: {
                    try {
                        startAddDataProcessorFragment();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    };


    public void startAddDataProcessorFragment(){
        Intent i = new Intent(this, AddActiveDataProcessorActivity.class);
        startActivity(i);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    public void notificationAdapter(){
        adapter = new RecyclerViewAdapter(this, dpManager.getInstance().getActiveDataProcessorsList());
        recyclerView.setAdapter(adapter);
    }


    public void notificationDataProcessor(){
        //Carrega no RecyclerView os DataProcessor ativos
        //adapter.notifyDataSetChanged();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
            return true;
        }
        else if(id == R.id.action_close){
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("Closing OpenDPMH")
                    .setMessage("Are you sure you want to close this app? It will stop the data collection.")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            processValue(false);
                            new AddItemTaskSet().execute(false);
                            new AddItemTaskOn().execute();
                            new AddItemTaskClear().execute();
                            dpManager.getInstance().stop();
                            finish();
                        }

                    })
                    .setNegativeButton("No", null)
                    .show();
        }
        /*else if(id == R.id.action_audio){
            //Start função recorder
            Intent startRecorder = new Intent(getApplicationContext(), MainActivityRecorder.class);
            startActivity(startRecorder);
        }*/

        return super.onOptionsItemSelected(item);
    }


    public void processValue(Boolean myValue) {
        Log.i(TAG,"#### myValue: " + myValue);
        if(!myValue){
            button_init.setBackgroundResource(R.color.colorWhite);
            txtStatus.setText("off");
        }
        else if(myValue){
            button_init.setBackgroundResource(R.color.colorGreen);
            txtStatus.setText("on");
        }
        flag_on_off = myValue;
    }


    private class AddItemTaskGet extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            FrameworkOnOff frameworkOnOff = new FrameworkOnOff();
            frameworkOnOff = frameworkStatus.getInstance().select();
            int tam = frameworkStatus.getInstance().totalRecords();
            Log.i(TAG,"#### Registro: " + tam);
            Boolean value = true;
            if(tam == 0){
                value = false;
            }
            return value;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            processValue(result);
        }
    }


    private class AddItemTaskClear extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            frameworkStatus.getInstance().delete();
            return null;
        }
    }


    private class AddItemTaskSet extends AsyncTask<Boolean, Void, Void> {
        @Override
        protected Void doInBackground(Boolean... params) {
            Log.i(TAG,"#### valor: " + params[0]);
            frameworkStatus.getInstance().update(params[0]);
            return null;
        }
    }


    public void processValue(List<ActiveDataProcessor> myValue) {
        if(myValue.size() > 0){
            List<String> list = new ArrayList();
            for(int i=0; i < myValue.size(); i++) {
                list.add(myValue.get(i).getDataProcessorName());
            }
            try {
                dpManager.getInstance().stopDataProcessors(list);
            } catch (InvalidDataProcessorNameException e) {
                e.printStackTrace();
            }
        }
    }

    private class AddItemTaskOn extends AsyncTask<Void, Void, List<ActiveDataProcessor>> {
        @Override
        protected List<ActiveDataProcessor> doInBackground(Void... params) {
            List<ActiveDataProcessor> l = new ArrayList();
            l = activeDataProcessorManager.getInstance().select();
            return l;
        }

        @Override
        protected void onPostExecute(List<ActiveDataProcessor> result) {
            processValue(result);
        }
    }


/*    private class AddItemTaskOff extends AsyncTask<List<ActiveDataProcessor>, Void, Void> {
        @Override
        protected Void doInBackground(List<ActiveDataProcessor>... params) {
            activeDataProcessorManager.getInstance().delete(params[0]);
            return null;
        }
    }*/


    public class Notification {
        Subscriber subNotification;

        public Notification(){
            subNotification = SubscriberFactory.createSubscriber();
            subNotification.addConnection(CDDL.getInstance().getConnection());
            subscribeMessageNotification(Topics.NOTIFICATION.toString());
        }

        public void subscribeMessageNotification(String serviceName) {
            subNotification.subscribeServiceByName(serviceName);
            subNotification.setSubscriberListener(subscriberNotificationListener);
            subNotification.subscribeTopic(Topics.NOTIFICATION.toString());
        }

        public final ISubscriberListener subscriberNotificationListener = new ISubscriberListener() {
            @Override
            public void onMessageArrived(br.ufma.lsdi.cddl.message.Message message) {
                Log.i(TAG, "#### Read messages (Notification):  " + message);

                Object[] valor = message.getServiceValue();
                String mensagemRecebida = StringUtils.join(valor, ", ");
                String[] separated = mensagemRecebida.split(",");
                String atividade = String.valueOf(separated[0]);

                if (atividade.equals("aliveMainService")) {
                    //notificationAdapter();
                }
                else if (atividade.equals("aliveNewDataProcessor") || atividade.equals("aliveRemoveDataProcessor")) {
                    notificationDataProcessor();
                }
            }
        };
    }
}

// --Backup-------------------------------------
/*//Start DataProcessor
        try {
            dpManager.getInstance().startDataProcessors(listProcessors);
        } catch (InvalidDataProcessorNameException e) {
            e.printStackTrace();
        }*/

        /*//Stop DataProcessor
        try {
            dpManager.getInstance().stopDataProcessors(listProcessors);
        } catch (InvalidDataProcessorNameException e) { e.printStackTrace(); }*/

        /*//Close framework
        dpManager.getInstance().stop();
        finish();*/

//--Backup-----------------------------------------
            /*final int tempoDeEspera = 1000;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    SystemClock.sleep(tempoDeEspera);
                    Log.i(TAG,"#### Aguardando1");
                    SystemClock.sleep(tempoDeEspera);
                    Log.i(TAG,"#### Aguardando2");
                    SystemClock.sleep(tempoDeEspera);
                    Log.i(TAG,"#### Aguardando3");
                    SystemClock.sleep(tempoDeEspera);
                    Log.i(TAG,"#### Aguardando4");
                    SystemClock.sleep(tempoDeEspera);
                    progressBar.setVisibility(View.INVISIBLE);
                    textLoad.setVisibility(View.INVISIBLE);
                }
            }).start();*/