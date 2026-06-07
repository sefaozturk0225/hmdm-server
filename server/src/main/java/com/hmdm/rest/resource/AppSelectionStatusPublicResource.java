package com.hmdm.rest.resource;

import com.hmdm.persistence.DeviceAppProposalDAO;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.persistence.domain.DeviceAppProposal;
import com.hmdm.rest.json.Response;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Collections;

/**
 * GET /rest/public/sync/app-selection-status/{deviceNumber}
 * Sibling path (not sub-path of app-selection/{x}) — avoids Jersey 2.25.1
 * bug where GET /{x}/literal is silently dropped when POST /{x} exists in
 * the same resource class.
 */
@Singleton
@Path("/public/sync/app-selection-status/{deviceNumber}")
@Api(tags = {"App selection proposal"})
public class AppSelectionStatusPublicResource {

    private static final Logger log = LoggerFactory.getLogger(AppSelectionStatusPublicResource.class);

    private UnsecureDAO          unsecureDAO;
    private DeviceAppProposalDAO proposalDAO;

    public AppSelectionStatusPublicResource() {}

    @Inject
    public AppSelectionStatusPublicResource(UnsecureDAO unsecureDAO, DeviceAppProposalDAO proposalDAO) {
        this.unsecureDAO = unsecureDAO;
        this.proposalDAO = proposalDAO;
    }

    @ApiOperation(
            value = "Get app-selection proposal status for a device",
            notes = "Returns {status: NONE|PENDING|APPLIED|DISMISSED}. " +
                    "Used by the launcher's waiting screen to poll for admin approval.",
            response = Response.class
    )
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSelectionStatus(
            @PathParam("deviceNumber") @ApiParam("Device number registered in MDM") String deviceNumber) {

        log.debug("GET /public/sync/app-selection-status/{}", deviceNumber);

        try {
            Device device = unsecureDAO.getDeviceByNumber(deviceNumber);
            if (device == null) {
                log.warn("App-selection status query for unknown device: {}", deviceNumber);
                return Response.DEVICE_NOT_FOUND_ERROR();
            }

            DeviceAppProposal proposal = proposalDAO.findByDeviceId(device.getId());
            String status = (proposal == null) ? "NONE" : proposal.getStatus();
            return Response.OK(Collections.singletonMap("status", status));

        } catch (Exception e) {
            log.error("Unexpected error querying status for device {}", deviceNumber, e);
            return Response.INTERNAL_ERROR();
        }
    }
}
