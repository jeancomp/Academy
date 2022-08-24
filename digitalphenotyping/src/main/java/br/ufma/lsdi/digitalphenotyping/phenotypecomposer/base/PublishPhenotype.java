package br.ufma.lsdi.digitalphenotyping.phenotypecomposer.base;

import android.content.Context;

import com.google.gson.Gson;

import br.ufma.lsdi.cddl.message.Message;
import br.ufma.lsdi.cddl.network.ConnectionImpl;
import br.ufma.lsdi.cddl.pubsub.Publisher;
import br.ufma.lsdi.cddl.pubsub.PublisherFactory;
import br.ufma.lsdi.digitalphenotyping.Topics;
import br.ufma.lsdi.digitalphenotyping.dataprocessor.digitalphenotypeevent.DigitalPhenotypeEvent;
import br.ufma.lsdi.digitalphenotyping.dataprocessor.digitalphenotypeevent.Situation;

public class PublishPhenotype {
    private static final String TAG = PublishPhenotype.class.getName();
    private Publisher publisher = PublisherFactory.createPublisher();
    private static PublishPhenotype instance = null;
    private static ConnectionImpl connection;
    private static Context context;
    private SaveConnection saveConnection = SaveConnection.getInstance();

    //public PublishPhenotype(){ }

    public PublishPhenotype(Context context, ConnectionImpl con){
        this.connection = con;
        this.context = context;
        publisher.addConnection(connection);
    }

    public static PublishPhenotype getInstance() {
        if (instance == null) {
            instance = new PublishPhenotype(context, connection);
        }
        return instance;
    }

    public void publishPhenotypeComposer(Situation dpe){
        DigitalPhenotype dp = new DigitalPhenotype();
        dp.setSituationList(dpe);
        String valor = stringFromObject(dp);

        Message message = new Message();
        message.setServiceName(Topics.OPENDPMH_TOPIC.toString());
        //message.setTopic(Topics.OPENDPMH_TOPIC.toString());
        message.setServiceValue(valor);
        publisher.publish(message);
    }

    //Lista de DigitalPhenotypeEvent dentro do DigitalPhenotype
    public void publishPhenotypeComposer(DigitalPhenotype digitalPhenotypeList) {
        try{
            //Log.i(TAG,"#### TOTAL: " + digitalPhenotypeList.getDigitalPhenotypeEventList().size());

            Situation dpe = new Situation();
            DigitalPhenotype dp = new DigitalPhenotype();
            Message msg = new Message();
            Publisher pub = PublisherFactory.createPublisher();
            pub.addConnection(saveConnection.getInstance().getConnection());

            for (int i = 0; i < digitalPhenotypeList.getSituationList().size(); i++) {
                //Log.i(TAG, "#### Data Publish to Server");

                //Log.i(TAG,"#### >>>>>>> " + digitalPhenotypeList.getDigitalPhenotypeEventList().get(i).toString());

                dpe = digitalPhenotypeList.getSituationList().get(i);

                dp = new DigitalPhenotype();
                dp.setSituationList(dpe);
                String valor = stringFromObject(dp);

                msg.setServiceName("opendpmh");
                msg.setTopic(Topics.OPENDPMH_TOPIC.toString());
                msg.setServiceValue(valor);

                pub.publish(msg);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public String stringFromObject(DigitalPhenotype dp){
        Gson gson = new Gson();
        String jsonString = gson.toJson(dp);
        return jsonString;
    }
}
