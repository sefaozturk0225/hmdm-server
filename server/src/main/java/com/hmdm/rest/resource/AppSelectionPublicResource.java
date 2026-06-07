package com.hmdm.rest.resource;

import com.hmdm.persistence.DeviceAppProposalDAO;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.rest.json.AppProposalRequest;
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
                log.warn("App-selection proposal rejected — unknown device number: {}", deviceNumber);
                return Response.DEVICE_NOT_FOUND_ERROR();
            }

            if (request == null || request.getApps() == null) {
                return Response.ERROR("apps field must not be null");
            }

            proposalDAO.upsertProposal(device.getId(), device.getCustomerId(), request);
            return Response.OK();

        } catch (Exception e) {
            log.error("Unexpected error processing app-selection proposal for device {}", deviceNumber, e);
            return Response.INTERNAL_ERROR();
        }
    }
}
