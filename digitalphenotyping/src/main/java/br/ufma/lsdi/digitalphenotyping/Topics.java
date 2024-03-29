package br.ufma.lsdi.digitalphenotyping;

public enum Topics {
    NOTIFICATION("notification"),
    ADD_PLUGIN_TOPIC("addplugin"),
    REMOVE_PLUGIN_TOPIC("removeplugin"),
    PLUGIN_LIST_TOPIC("pluginlist"),
    SELECT_PLUGIN_TOPIC("selectplugintoactivate"),
    DELETE_PLUGIN_TOPIC("selectplugintodelete"),
    START_DATAPROCESSOR_TOPIC("startdataprocessor"),
    STOP_DATAPROCESSOR_TOPIC("stopdataprocessor"),
    ACTIVE_DATAPROCESSOR_TOPIC("activedataprocessors"),
    DEACTIVATE_DATAPROCESSOR_TOPIC("deactivatedataprocessor"),
    ACTIVE_SENSOR_TOPIC("activesensor"),
    DEACTIVATE_SENSOR_TOPIC("deactivatesensor"),
    LIST_SENSORS_TOPIC("listsensors"),
    COMPOSITION_MODE_TOPIC("compositionmode"),
    MAINSERVICE_CONFIGURATION_INFORMATION_TOPIC("configurationinformation"),
    CONFIGURATION_INFORMATION_TOPIC("configurationinformations"),
    SAVE_PHENOTYPES_EVENT_TOPIC("phenotypesevent"),
    AUDIO_TOPIC("audiodetected"),
    INFERENCE_TOPIC("inference"),
    OPENDPMH_TOPIC("opendpmh");

    private final String text;

    Topics(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}