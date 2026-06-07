package com.hmdm.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.persistence.domain.DeviceAppProposal;
import com.hmdm.persistence.mapper.DeviceAppProposalMapper;
import com.hmdm.rest.json.AppProposalRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class DeviceAppProposalDAO {

    private static final Logger log = LoggerFactory.getLogger(DeviceAppProposalDAO.class);

    private final DeviceAppProposalMapper mapper;
    private final ObjectMapper            jsonMapper = new ObjectMapper();

    @Inject
    public DeviceAppProposalDAO(DeviceAppProposalMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Upsert: if a proposal already exists for this device, update its JSON and reset status to PENDING.
     * Otherwise insert a new row. Keeps exactly one active proposal per device.
     */
    public void upsertProposal(Integer deviceId, Integer customerId, AppProposalRequest request) {
        try {
            String json = jsonMapper.writeValueAsString(request.getApps());
            DeviceAppProposal existing = mapper.findByDeviceId(deviceId);
            if (existing == null) {
                DeviceAppProposal p = new DeviceAppProposal();
                p.setDeviceId(deviceId);
                p.setCustomerId(customerId);
                p.setProposalJson(json);
                mapper.insert(p);
            } else {
                mapper.updateProposal(deviceId, json);
            }
        } catch (Exception e) {
            log.error("Failed to upsert app proposal for device {}", deviceId, e);
            throw new RuntimeException(e);
        }
    }

    public DeviceAppProposal findByDeviceId(Integer deviceId) {
        return mapper.findByDeviceId(deviceId);
    }

    public void markApplied(Integer proposalId, Integer configurationId) {
        mapper.updateStatus(proposalId, "APPLIED", configurationId);
    }

    public void markDismissed(Integer proposalId) {
        mapper.updateStatus(proposalId, "DISMISSED", null);
    }

    /** Single query: returns all PENDING proposals for the tenant. */
    public List<DeviceAppProposal> findPendingByCustomerId(Integer customerId) {
        return mapper.findPendingByCustomerId(customerId);
    }
}
