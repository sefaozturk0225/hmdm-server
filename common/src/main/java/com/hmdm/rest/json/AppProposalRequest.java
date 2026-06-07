package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppProposalRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<ProposalAppItem> apps;

    public List<ProposalAppItem> getApps() { return apps; }
    public void setApps(List<ProposalAppItem> apps) { this.apps = apps; }
}
