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
 * Public endpoint called by the launcher to submit an app-selection proposal.
 * Path: POST /rest/public/sync/app-selection/{deviceNumber}
 *
 * Auth model: same as SyncResource — device identified by its numeric device-number,
 * no Bearer token required (public/* path, no JWTFilter).
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
            notes = "Called by the launcher to propose which apps the user wants. " +
                    "Creates or replaces a PENDING proposal for this device.",
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
                log.warn("App-selection proposal for unknown device: {}", deviceNumber);
                return Response.DEVICE_NOT_FOUND_ERROR();
            }

            if (request == null || request.getApps() == null || request.getApps().isEmpty()) {
                return Response.ERROR("App list must not be empty");
            }

            proposalDAO.upsertProposal(device.getId(), device.getCustomerId(), request);
            return Response.OK();

        } catch (Exception e) {
            log.error("Unexpected error processing app-selection proposal for device {}", deviceNumber, e);
            return Response.INTERNAL_ERROR();
        }
    }
}
