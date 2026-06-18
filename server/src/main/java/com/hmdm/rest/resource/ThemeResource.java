package com.hmdm.rest.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;

/**
 * Public REST API for mobile app theme management.
 * Themes are stored as JSON files under {files.directory}/themes/.
 *
 * File layout expected on server:
 *   {files.directory}/themes/index.json       → list of available themes
 *   {files.directory}/themes/{id}.json        → full theme definition
 *   {files.directory}/themes/{id}-preview.jpg → preview image (optional)
 *
 * See install/themes/ for example files to copy to the server.
 */
@Api(tags = {"Themes"})
@Singleton
@Path("/public/themes")
public class ThemeResource {

    private static final Logger log = LoggerFactory.getLogger(ThemeResource.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String filesDirectory;

    public ThemeResource() {
        this.filesDirectory = "";
    }

    @Inject
    public ThemeResource(@Named("files.directory") String filesDirectory) {
        this.filesDirectory = filesDirectory;
    }

    @ApiOperation(
            value = "List themes",
            notes = "Returns the list of available themes from {files.directory}/themes/index.json"
    )
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listThemes() {
        File indexFile = new File(filesDirectory, "themes/index.json");
        if (!indexFile.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"No themes configured. Place index.json in " + filesDirectory + "/themes/\"}")
                    .build();
        }
        try {
            Object themes = objectMapper.readValue(indexFile, Object.class);
            return Response.ok(themes).build();
        } catch (IOException e) {
            log.error("Failed to read themes index.json", e);
            return Response.serverError().build();
        }
    }

    @ApiOperation(
            value = "Get theme by ID",
            notes = "Returns full theme definition from {files.directory}/themes/{id}.json"
    )
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTheme(@PathParam("id") String id) {
        if (!id.matches("[a-zA-Z0-9_-]+")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Invalid theme id\"}")
                    .build();
        }
        File themeFile = new File(filesDirectory, "themes/" + id + ".json");
        if (!themeFile.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Theme not found: " + id + "\"}")
                    .build();
        }
        try {
            Object theme = objectMapper.readValue(themeFile, Object.class);
            return Response.ok(theme).build();
        } catch (IOException e) {
            log.error("Failed to read theme: " + id, e);
            return Response.serverError().build();
        }
    }
}
