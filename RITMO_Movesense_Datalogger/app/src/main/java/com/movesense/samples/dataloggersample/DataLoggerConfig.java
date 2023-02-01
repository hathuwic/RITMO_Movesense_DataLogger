package com.movesense.samples.dataloggersample;

import com.google.gson.annotations.SerializedName;

/**
 * Created by lipponep on 23.11.2017.
 */


public class DataLoggerConfig {
    // PUT
    @SerializedName("config")
    public final Config config;

    // GET
    @SerializedName("Content")
    public final Config content;

    public DataLoggerConfig(Config config) {
        this.config = config;
        this.content = null;
    }

    public static class Config {
        @SerializedName("dataEntries")
        public final DataEntries dataEntries;

        public Config(DataEntries dataEntries) {
            this.dataEntries = dataEntries;
        }
    }

    public static class DataEntries {
        @SerializedName("dataEntry")
        public final DataEntry[] dataEntry;

        public DataEntries(DataEntry[] dataEntry) {
            this.dataEntry = dataEntry;
        }
    }

    public static class DataEntry {
        @SerializedName("path")
        public final String path;

        public DataEntry(String path) {
            this.path = path;
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        Config root = (config!= null) ? config : content;
        for (DataEntry de : root.dataEntries.dataEntry) {
            if (sb.length() > 0)
                sb.append("\n");

            sb.append(de.path);
        }

        return sb.toString();
    }
}

