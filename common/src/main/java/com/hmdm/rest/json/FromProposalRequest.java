package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FromProposalRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer deviceId;
    private String  configName;
    private Integer templateConfigurationId;

    public Integer getDeviceId() { return deviceId; }
    public void setDeviceId(Integer deviceId) { this.deviceId = deviceId; }

    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }

    public Integer getTemplateConfigurationId() { return templateConfigurationId; }
    public void setTemplateConfigurationId(Integer templateConfigurationId) {
        this.templateConfigurationId = templateConfigurationId;
    }
}
