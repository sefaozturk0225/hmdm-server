package com.hmdm.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceAppProposal implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer deviceId;
    private Integer customerId;
    private String  proposalJson;
    private String  status;
    private Long    createdAt;
    private Integer appliedConfigurationId;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getDeviceId() { return deviceId; }
    public void setDeviceId(Integer deviceId) { this.deviceId = deviceId; }

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public String getProposalJson() { return proposalJson; }
    public void setProposalJson(String proposalJson) { this.proposalJson = proposalJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public Integer getAppliedConfigurationId() { return appliedConfigurationId; }
    public void setAppliedConfigurationId(Integer appliedConfigurationId) {
        this.appliedConfigurationId = appliedConfigurationId;
    }
}
