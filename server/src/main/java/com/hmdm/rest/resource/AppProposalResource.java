package com.hmdm.rest.resource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdm.persistence.ApplicationDAO;
import com.hmdm.persistence.ConfigurationDAO;
import com.hmdm.persistence.DeviceAppProposalDAO;
import com.hmdm.persistence.DeviceDAO;
import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationType;
import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.domain.Device;
import com.hmdm.persistence.domain.DeviceAppProposal;
import com.hmdm.rest.json.FromProposalRequest;
import com.hmdm.rest.json.ProfilInfo;
import com.hmdm.rest.json.ProposalAppItem;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Private endpoint: turns a pending app-selection proposal into a dedicated configuration.
 * Path: POST /rest/private/configurations/from-proposal
 *
 * More-specific path than ConfigurationResource (/private/configurations) — Jersey
 * routes /from-proposal requests here via best-match.
 */
@Singleton
@Path("/private/configurations")
@Api(tags = {"Configuration"}, authorizations = {@Authorization("Bearer Token")})
public class AppProposalResource {

    private static final Logger log = LoggerFactory.getLogger(AppProposalResource.class);

    private ConfigurationDAO     configurationDAO;
    private ApplicationDAO       applicationDAO;
    private DeviceDAO            deviceDAO;
    private DeviceAppProposalDAO proposalDAO;
    private final ObjectMapper   jsonMapper = new ObjectMapper();

    public AppProposalResource() {}

    @Inject
    public AppProposalResource(ConfigurationDAO configurationDAO,
                               ApplicationDAO applicationDAO,
                               DeviceDAO deviceDAO,
                               DeviceAppProposalDAO proposalDAO) {
        this.configurationDAO = configurationDAO;
        this.applicationDAO   = applicationDAO;
        this.deviceDAO        = deviceDAO;
        this.proposalDAO      = proposalDAO;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Parses both legacy format (JSON array) and new format ({profil, apps}). */
    private ParsedProposal parseProposalJson(String json) throws Exception {
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            // Legacy format: bare app array stored by earlier versions.
            List<ProposalAppItem> apps = jsonMapper.readValue(json,
                    new TypeReference<List<ProposalAppItem>>() {});
            return new ParsedProposal(null, apps);
        } else {
            // New format: {"profil":{...}, "apps":[...]}
            Map<String, Object> root = jsonMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            Object appsRaw = root.get("apps");
            List<ProposalAppItem> apps = appsRaw != null
                    ? jsonMapper.convertValue(appsRaw, new TypeReference<List<ProposalAppItem>>() {})
                    : Collections.emptyList();
            Object profilRaw = root.get("profil");
            ProfilInfo profil = (profilRaw != null)
                    ? jsonMapper.convertValue(profilRaw, ProfilInfo.class)
                    : null;
            return new ParsedProposal(profil, apps);
        }
    }

    private static class ParsedProposal {
        final ProfilInfo            profil;
        final List<ProposalAppItem> apps;
        ParsedProposal(ProfilInfo profil, List<ProposalAppItem> apps) {
            this.profil = profil;
            this.apps   = apps;
        }
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @ApiOperation(
            value = "Create configuration from device app proposal",
            notes = "Takes the pending app-selection proposal for the specified device and creates " +
                    "a new configuration. Optionally clones a template configuration first. " +
                    "Binds the device to the new configuration and marks the proposal APPLIED. " +
                    "If the proposal contains profil.cihazAdi, the device description is updated accordingly.",
            response = Response.class
    )
    @POST
    @Path("/from-proposal")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response fromProposal(FromProposalRequest request) {

        if (!SecurityContext.get().hasPermission("configurations") ||
                !SecurityContext.get().hasPermission("add_config")) {
            log.error("Unauthorized attempt to create configuration from proposal by {}",
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }

        try {
            // ── 1. Validate request ────────────────────────────────────────────────────
            if (request.getDeviceId() == null) return Response.ERROR("deviceId is required");
            if (request.getConfigName() == null || request.getConfigName().trim().isEmpty())
                return Response.ERROR("configName is required");

            // ── 2. Load device ────────────────────────────────────────────────────────
            Device device = deviceDAO.getDeviceById(request.getDeviceId());
            if (device == null) {
                log.warn("fromProposal: device {} not found", request.getDeviceId());
                return Response.ERROR("Device not found");
            }

            // ── 3. Load and parse proposal (backward-compat) ──────────────────────────
            DeviceAppProposal proposal = proposalDAO.findByDeviceId(device.getId());
            if (proposal == null || proposal.getProposalJson() == null) {
                return Response.ERROR("No pending proposal found for this device");
            }

            ParsedProposal parsed      = parseProposalJson(proposal.getProposalJson());
            List<ProposalAppItem> proposedApps = parsed.apps;
            ProfilInfo            profil        = parsed.profil;

            // ── 4. Resolve allowed packages — auto-create URL-less stubs for unknowns ─
            List<String> autoCreated = new ArrayList<>();
            List<Application> catalogApps = new ArrayList<>();

            for (ProposalAppItem item : proposedApps) {
                if (!item.isAllowed()) continue;

                List<Application> found = applicationDAO.findByPackageId(item.getPkg());
                Application app;

                if (!found.isEmpty()) {
                    app = found.get(0);
                } else {
                    // Auto-register: URL-less stub following insertWebApplication() pattern.
                    // applicationVersions.url is nullable; version="0" satisfies NOT NULL.
                    // insertApplication() calls insertRecord() (sets customerId from SecurityContext),
                    // then insertApplicationVersion() + recalculateLatestVersion() automatically.
                    Application stub = new Application();
                    String displayName = (item.getName() != null && !item.getName().trim().isEmpty())
                            ? item.getName().trim() : item.getPkg();
                    stub.setName(displayName);
                    stub.setPkg(item.getPkg());
                    stub.setType(ApplicationType.app);
                    stub.setShowIcon(true);
                    stub.setVersion("0");
                    // url left null — applicationVersions.url is nullable

                    applicationDAO.insertApplication(stub);
                    // Re-fetch to get latestVersion FK populated (needed by insertConfiguration)
                    app = applicationDAO.findById(stub.getId());
                    autoCreated.add(item.getPkg());
                    log.info("Auto-created URL-less Application stub for package: {}", item.getPkg());
                }

                app.setAction(1);
                app.setShowIcon(true);
                app.setRemove(false);
                catalogApps.add(app);
            }

            // ── 5. Build the new configuration ────────────────────────────────────────
            Configuration newConfig;
            Integer templateId = request.getTemplateConfigurationId();

            if (templateId != null) {
                Configuration template = configurationDAO.getConfigurationById(templateId);
                if (template == null) return Response.ERROR("Template configuration not found");

                List<Application> templateApps = configurationDAO.getPlainConfigurationApplications(templateId);
                newConfig = template.newCopy();
                newConfig.setDescription("Created from device proposal");

                // Merge: keep template apps, add proposal apps not already present
                Set<String> existingPkgs = templateApps.stream()
                        .map(Application::getPkg)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                List<Application> merged = new ArrayList<>(templateApps);
                for (Application a : catalogApps) {
                    if (!existingPkgs.contains(a.getPkg())) merged.add(a);
                }
                newConfig.setApplications(merged);
            } else {
                newConfig = new Configuration();
                newConfig.setDisableLocation(false);
                newConfig.setDescription("Created from device proposal");
                newConfig.setApplications(catalogApps);
            }

            newConfig.setName(request.getConfigName().trim());

            // Duplicate name check
            if (configurationDAO.getConfigurationByName(newConfig.getName()) != null) {
                return Response.DUPLICATE_ENTITY("error.duplicate.configuration");
            }

            // ── 6. Persist configuration ──────────────────────────────────────────────
            configurationDAO.insertConfiguration(newConfig);

            // ── 7. Assign device to new configuration ─────────────────────────────────
            deviceDAO.updateDeviceConfiguration(device.getId(), newConfig.getId());

            // ── 8. Update device description from profil.cihazAdi if provided ─────────
            if (profil != null && profil.getCihazAdi() != null && !profil.getCihazAdi().trim().isEmpty()) {
                try {
                    deviceDAO.updateDeviceDescription(device.getId(), profil.getCihazAdi().trim());
                    log.info("Updated device {} description to '{}'", device.getId(), profil.getCihazAdi().trim());
                } catch (Exception descEx) {
                    // Non-fatal: config was already created; just log and continue.
                    log.warn("Could not update device description for device {}: {}", device.getId(), descEx.getMessage());
                }
            }

            // ── 9. Mark proposal as APPLIED ───────────────────────────────────────────
            proposalDAO.markApplied(proposal.getId(), newConfig.getId());

            // ── 10. Return result ─────────────────────────────────────────────────────
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("configurationId", newConfig.getId());
            result.put("configurationName", newConfig.getName());
            result.put("autoCreatedPackages", autoCreated);
            return Response.OK(result);

        } catch (Exception e) {
            log.error("Unexpected error in fromProposal", e);
            return Response.INTERNAL_ERROR();
        }
    }

    @ApiOperation(
            value = "Get pending app-selection proposal for a device",
            notes = "Returns the PENDING proposal for the given device, or null if none exists. " +
                    "Response includes profil block if present (new format) alongside the apps list.",
            response = Response.class
    )
    @GET
    @Path("/proposal/{deviceId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProposal(@PathParam("deviceId") Integer deviceId) {
        if (!SecurityContext.get().hasPermission("configurations")) {
            return Response.PERMISSION_DENIED();
        }
        try {
            Device device = deviceDAO.getDeviceById(deviceId);
            if (device == null) return Response.ERROR("Device not found");

            DeviceAppProposal proposal = proposalDAO.findByDeviceId(device.getId());
            if (proposal == null || !"PENDING".equals(proposal.getStatus())) {
                return Response.OK(null);
            }

            ParsedProposal parsed     = parseProposalJson(proposal.getProposalJson());
            long allowedCount         = parsed.apps.stream().filter(ProposalAppItem::isAllowed).count();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deviceId", deviceId);
            result.put("profil", parsed.profil);   // null for legacy proposals — panel hides block via ng-if
            result.put("apps", parsed.apps);
            result.put("count", allowedCount);
            result.put("createdAt", proposal.getCreatedAt());
            return Response.OK(result);
        } catch (Exception e) {
            log.error("Error fetching proposal for device {}", deviceId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    @ApiOperation(
            value = "Dismiss the pending app-selection proposal for a device",
            notes = "Sets the proposal status to DISMISSED.",
            response = Response.class
    )
    @POST
    @Path("/proposal/{deviceId}/dismiss")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dismissProposal(@PathParam("deviceId") Integer deviceId) {
        if (!SecurityContext.get().hasPermission("configurations")) {
            return Response.PERMISSION_DENIED();
        }
        try {
            Device device = deviceDAO.getDeviceById(deviceId);
            if (device == null) return Response.ERROR("Device not found");

            DeviceAppProposal proposal = proposalDAO.findByDeviceId(device.getId());
            if (proposal == null) return Response.ERROR("No proposal found for this device");

            proposalDAO.markDismissed(proposal.getId());
            return Response.OK();
        } catch (Exception e) {
            log.error("Error dismissing proposal for device {}", deviceId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    @ApiOperation(
            value = "Get pending app-selection proposal counts for all devices (batch)",
            notes = "Single query: returns a map of deviceId -> allowedAppCount for every " +
                    "PENDING proposal belonging to the current tenant. Used by the device list " +
                    "to render proposal badges without per-device requests.",
            response = Response.class
    )
    @GET
    @Path("/proposals/pending")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPendingProposals() {
        if (!SecurityContext.get().hasPermission("configurations")) {
            return Response.PERMISSION_DENIED();
        }
        try {
            Optional<Integer> customerId = SecurityContext.get().getCurrentCustomerId();
            if (!customerId.isPresent()) {
                return Response.PERMISSION_DENIED();
            }

            List<DeviceAppProposal> pending =
                    proposalDAO.findPendingByCustomerId(customerId.get());

            Map<Integer, Integer> result = new LinkedHashMap<>();
            for (DeviceAppProposal p : pending) {
                if (p.getProposalJson() == null) continue;
                try {
                    ParsedProposal parsed = parseProposalJson(p.getProposalJson());
                    int count = (int) parsed.apps.stream().filter(ProposalAppItem::isAllowed).count();
                    result.put(p.getDeviceId(), count);
                } catch (Exception parseEx) {
                    log.warn("Could not parse proposalJson for device {}, skipping badge",
                            p.getDeviceId());
                }
            }
            return Response.OK(result);
        } catch (Exception e) {
            log.error("Error fetching pending proposals batch", e);
            return Response.INTERNAL_ERROR();
        }
    }
}
