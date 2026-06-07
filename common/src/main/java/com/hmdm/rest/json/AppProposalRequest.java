package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppProposalRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private ProfilInfo            profil;
    private List<ProposalAppItem> apps;

    public ProfilInfo getProfil() { return profil; }
    public void setProfil(ProfilInfo profil) { this.profil = profil; }

    public List<ProposalAppItem> getApps() { return apps; }
    public void setApps(List<ProposalAppItem> apps) { this.apps = apps; }
}
