package com.hmdm.rest.resource;

import com.hmdm.persistence.DeviceAppProposalDAO;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.rest.json.AppProposalRequest;
import com.hmdm.rest.json.ProfilInfo;
import com.hmdm.rest.json.Response;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.*;

import com.hmdm.persistence.domain.DeviceAppProposal;
import com.hmdm.rest.json.ProposalAppItem;

/**
 * POST /rest/public/sync/app-selection/{deviceNumber}
 * Status polling is in AppSelectionStatusPublicResource (separate class — Jersey 2.25.1
 * drops GET /{x}/literal when POST /{x} exists in the same resource class).
 */
@Singleton
@Path("/public/sync/app-selection")
@Api(tags = {"App selection proposal"})
public class AppSelectionPublicResource {

    private static final Logger log = LoggerFactory.getLogger(AppSelectionPublicResource.class);

    private UnsecureDAO           unsecureDAO;
    private DeviceAppProposalDAO  proposalDAO;

    public AppSelectionPublicResource() {}

    @Inject
    public AppSelectionPublicResource(UnsecureDAO unsecureDAO, DeviceAppProposalDAO proposalDAO) {
        this.unsecureDAO = unsecureDAO;
        this.proposalDAO = proposalDAO;
    }

    @ApiOperation(
            value = "Submit app selection proposal",
            notes = "Creates or replaces a PENDING proposal for this device. " +
                    "Returns error.notfound.device if the device number is not registered.",
            response = Response.class
    )
    @POST
    @Path("/{deviceNumber}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response submitAppSelection(
            @PathParam("deviceNumber") @ApiParam("Device number registered in MDM") String deviceNumber,
            AppProposalRequest request) {

        log.debug("POST /public/sync/app-selection/{}", deviceNumber);

        try {
            Device device = unsecureDAO.getDeviceByNumber(deviceNumber);
            if (device == null) {
                // Cihaz henüz yok — form gönderimi sırasında oluştur.
                // sync()'in isSingleCustomer/createNewDevices koşullarına bağlı kalmıyoruz.
                try {
                    final int customerId = 1; // tek müşteri (DEFAULT_CUSTOMER_ID)
                    Integer configId = unsecureDAO.getDefaultConfigurationIdForCustomer(customerId);
                    if (configId == null) {
                        log.error("No configuration found for customer {}, cannot auto-create device {}", customerId, deviceNumber);
                        return Response.ERROR("Server configuration error: no configuration found");
                    }
                    Device newDevice = new Device();
                    newDevice.setNumber(deviceNumber);
                    newDevice.setCustomerId(customerId);
                    newDevice.setConfigurationId(configId);
                    newDevice.setLastUpdate(0L);
                    unsecureDAO.insertDevice(newDevice);
                    device = unsecureDAO.getDeviceByNumber(deviceNumber);
                    log.info("Device {} auto-created on submit (configId={})", deviceNumber, configId);
                } catch (Exception createEx) {
                    log.error("Failed to auto-create device {} on submit", deviceNumber, createEx);
                }
                if (device == null) {
                    log.warn("App-selection proposal rejected — could not create device {}", deviceNumber);
                    return Response.DEVICE_NOT_FOUND_ERROR();
                }
            }

            if (request == null || request.getApps() == null) {
                return Response.ERROR("apps field must not be null");
            }

            // Profili cihaz kaydına yaz (PENDING'ken de panelde görünsün).
            ProfilInfo profil = request.getProfil();
            if (profil != null) {
                device.setCustom1(profil.getAdSoyad() != null ? profil.getAdSoyad().trim() : null);
                device.setCustom2(profil.getYas()     != null ? String.valueOf(profil.getYas()) : null);
                device.setCustom3(profil.getMeslek()  != null ? profil.getMeslek().trim()  : null);
                unsecureDAO.updateDeviceCustomProperties(device.getId(), device);

                if (profil.getCihazAdi() != null && !profil.getCihazAdi().trim().isEmpty()) {
                    unsecureDAO.updateDeviceDescription(device.getId(), profil.getCihazAdi().trim());
                }

                if (profil.getTelefon() != null && !profil.getTelefon().trim().isEmpty()) {
                    unsecureDAO.updateDevicePhone(device.getId(), profil.getTelefon().trim());
                }
            }

            proposalDAO.upsertProposal(device.getId(), device.getCustomerId(), request);

            // Auto-apply: create/update per-device config with ALL submitted apps
            try {
                List<Map<String, Object>> appMaps = new ArrayList<>();
                for (ProposalAppItem appItem : request.getApps()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("pkg",     appItem.getPkg());
                    m.put("name",    appItem.getName());
                    m.put("allowed", appItem.isAllowed());
                    appMaps.add(m);
                }
                Integer configId = unsecureDAO.autoApplyProposal(device, appMaps);
                if (configId != null) {
                    DeviceAppProposal proposal = proposalDAO.findByDeviceId(device.getId());
                    if (proposal != null) {
                        proposalDAO.markApplied(proposal.getId(), configId);
                    }
                    log.info("Auto-applied proposal for device {}, configId={}", deviceNumber, configId);
                }
            } catch (Exception autoEx) {
                log.warn("Auto-apply failed for device {}, proposal remains PENDING", deviceNumber, autoEx);
            }

            return Response.OK();

        } catch (Exception e) {
            log.error("Unexpected error processing app-selection proposal for device {}", deviceNumber, e);
            return Response.INTERNAL_ERROR();
        }
    }
}
