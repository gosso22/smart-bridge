package com.smartbridge.core.model.cht;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Wrapper for the CouchDB {@code _changes} feed response.
 * Used by CHTSyncService for incremental synchronization.
 *
 * <p>The {@code last_seq} field is an opaque string token (not a numeric version)
 * that must be persisted and passed back as the {@code since} parameter on
 * the next sync cycle.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CHTChangesResponse {

    @JsonProperty("last_seq")
    private String lastSeq;

    @JsonProperty("pending")
    private Integer pending;

    @JsonProperty("results")
    private List<CHTChangeResult> results;

    public CHTChangesResponse() {}

    // Getters and Setters

    public String getLastSeq() {
        return lastSeq;
    }

    public void setLastSeq(String lastSeq) {
        this.lastSeq = lastSeq;
    }

    public Integer getPending() {
        return pending;
    }

    public void setPending(Integer pending) {
        this.pending = pending;
    }

    public List<CHTChangeResult> getResults() {
        return results;
    }

    public void setResults(List<CHTChangeResult> results) {
        this.results = results;
    }

    /**
     * A single change entry from the CouchDB {@code _changes} feed.
     * When {@code include_docs=true}, the full document is available in {@code doc}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CHTChangeResult {

        @JsonProperty("seq")
        private String seq;

        @JsonProperty("id")
        private String id;

        @JsonProperty("changes")
        private List<ChangeRev> changes;

        @JsonProperty("doc")
        private Map<String, Object> doc;

        @JsonProperty("deleted")
        private Boolean deleted;

        public CHTChangeResult() {}

        public String getSeq() {
            return seq;
        }

        public void setSeq(String seq) {
            this.seq = seq;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<ChangeRev> getChanges() {
            return changes;
        }

        public void setChanges(List<ChangeRev> changes) {
            this.changes = changes;
        }

        public Map<String, Object> getDoc() {
            return doc;
        }

        public void setDoc(Map<String, Object> doc) {
            this.doc = doc;
        }

        public Boolean getDeleted() {
            return deleted;
        }

        public void setDeleted(Boolean deleted) {
            this.deleted = deleted;
        }

        public boolean isDeleted() {
            return Boolean.TRUE.equals(deleted);
        }
    }

    /**
     * Revision entry within a change result.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChangeRev {

        @JsonProperty("rev")
        private String rev;

        public ChangeRev() {}

        public String getRev() {
            return rev;
        }

        public void setRev(String rev) {
            this.rev = rev;
        }
    }
}
