package com.movesense.samples.dataloggersample;

import com.google.gson.annotations.SerializedName;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by lipponep on 23.11.2017.
 */

public class MdsLogbookEntriesResponse {

    public MdsLogbookEntriesResponse(LogEntry[] logEntries) {
        this.logEntries = logEntries;
    }

    @SerializedName("elements")
    public final LogEntry[] logEntries;

    public static class LogEntry {
        @SerializedName("Id")
        public final int id;

        @SerializedName("ModificationTimestamp")
        public final long modificationTimestamp;

        @SerializedName("Size")
        public final Long size;

        LogEntry(int id, int modificationTimestamp, Long size) {
            this.id = id;
            this.modificationTimestamp = modificationTimestamp;
            this.size = size;
        }

        static SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss");

        public String getDateStr() {
            return dateFormat.format(new Date(modificationTimestamp*1000));
        }
        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append(id);
            sb.append(" : ");
            // pretty print date time
            sb.append(getDateStr());
            sb.append(" : ");
            sb.append(size);

            return sb.toString();
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (LogEntry le : logEntries) {
            if (sb.length() > 0)
                sb.append("\n");

            sb.append(le.id);
            sb.append(" : ");
            sb.append(le.modificationTimestamp);
            sb.append(" : ");
            sb.append(le.size);
        }
        return sb.toString();
    }

}
