package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProposalAppItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String  pkg;
    private String  name;
    private boolean allowed;

    public String getPkg() { return pkg; }
    public void setPkg(String pkg) { this.pkg = pkg; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
}
