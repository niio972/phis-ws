//******************************************************************************
//                          ExperimentResourceService.java 
// SILEX-PHIS
// Copyright © INRA 2018
// Creation date: January 2017
// Contact: morgane.vidal@inra.fr, anne.tireau@inra.fr, pascal.neveu@inra.fr
//******************************************************************************
package opensilex.service.resource;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import opensilex.service.configuration.DateFormat;
import opensilex.service.configuration.DateFormats;
import opensilex.service.configuration.DefaultBrapiPaginationValues;
import opensilex.service.configuration.GlobalWebserviceValues;
import opensilex.service.dao.DataDAO;
import opensilex.service.dao.ExperimentRdf4jDAO;
import opensilex.service.dao.ExperimentSQLDAO;
import opensilex.service.dao.ProvenanceDAO;
import opensilex.service.dao.ScientificObjectRdf4jDAO;
import opensilex.service.dao.SensorDAO;
import opensilex.service.dao.VariableDAO;
import opensilex.service.documentation.DocumentationAnnotation;
import opensilex.service.documentation.StatusCodeMsg;
import opensilex.service.model.Data;
import opensilex.service.resource.dto.experiment.ExperimentDTO;
import opensilex.service.resource.dto.experiment.ExperimentPostDTO;
import opensilex.service.resource.validation.interfaces.Date;
import opensilex.service.resource.validation.interfaces.Required;
import opensilex.service.resource.validation.interfaces.URL;
import opensilex.service.utils.POSTResultsReturn;
import opensilex.service.view.brapi.Status;
import opensilex.service.view.brapi.form.AbstractResultForm;
import opensilex.service.view.brapi.form.ResponseFormGET;
import opensilex.service.view.brapi.form.ResponseFormPOST;
import opensilex.service.result.ResultForm;
import opensilex.service.model.Experiment;
import opensilex.service.model.ScientificObject;
import opensilex.service.model.Variable;
import opensilex.service.ontology.Oeso;
import opensilex.service.resource.dto.data.DataDTO;
import opensilex.service.resource.dto.data.DataSearchDTO;
import opensilex.service.view.model.provenance.Provenance;

/**
 * Experiment resource service.
 *
 * @update [Morgane Vidal] 31 Oct. 2017: refactor trial to experiment
 * @update [Morgane Vidal] 20 Dec. 2018: add PUT services: -
 * experiment/{uri}/variables - experiment/{uri}/sensors
 * @author Morgane Vidal <morgane.vidal@inra.fr>
 */
@Api("/experiments")
@Path("experiments")
public class ExperimentResourceService extends ResourceService {

    final static Logger LOGGER = LoggerFactory.getLogger(ExperimentResourceService.class);

    /**
     * @param uri
     * @param limit
     * @param page
     * @param projectUri
     * @param startDate
     * @param endDate
     * @param field
     * @param campaign
     * @param place
     * @param alias
     * @param keywords
     * @return found experiments
     */
    @GET
    @ApiOperation(value = "Get all experiments corresponding to the searched params given",
            notes = "Retrieve all experiments authorized for the user corresponding to the searched params given")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Retrieve all experiments", response = Experiment.class, responseContainer = "List"),
        @ApiResponse(code = 400, message = DocumentationAnnotation.BAD_USER_INFORMATION),
        @ApiResponse(code = 401, message = DocumentationAnnotation.USER_NOT_AUTHORIZED),
        @ApiResponse(code = 500, message = DocumentationAnnotation.ERROR_FETCH_DATA)})
    @ApiImplicitParams({
        @ApiImplicitParam(name = "Authorization", required = true,
                dataType = "string", paramType = "header",
                value = DocumentationAnnotation.ACCES_TOKEN,
                example = GlobalWebserviceValues.AUTHENTICATION_SCHEME + " ")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getExperimentsBySearch(
            @ApiParam(value = DocumentationAnnotation.PAGE_SIZE) @QueryParam("pageSize") @DefaultValue(DefaultBrapiPaginationValues.PAGE_SIZE) @Min(0) int limit,
            @ApiParam(value = DocumentationAnnotation.PAGE) @QueryParam("page") @DefaultValue(DefaultBrapiPaginationValues.PAGE) @Min(0) int page,
            @ApiParam(value = "Search by uri", example = DocumentationAnnotation.EXAMPLE_EXPERIMENT_URI) @QueryParam("uri") @URL String uri,
            @ApiParam(value = "Search by project uri", example = DocumentationAnnotation.EXAMPLE_PROJECT_URI) @QueryParam("projectUri") @URL String projectUri,
            @ApiParam(value = "Search by start date", example = DocumentationAnnotation.EXAMPLE_EXPERIMENT_START_DATE) @QueryParam("startDate") @Date(DateFormat.YMD) String startDate,
            @ApiParam(value = "Search by end date", example = DocumentationAnnotation.EXAMPLE_EXPERIMENT_END_DATE) @QueryParam("endDate") @Date(DateFormat.YMD) String endDate,
            @ApiParam(value = "Search by field", example = DocumentationAnnotation.EXAMPLE_EXPERIMENT_FIELD) @QueryParam("field") String field,
            @ApiParam(value = "Search by campaign", example = DocumentationAnnotation.EXAMPLE_EXPERIMENT_CAMPAIGN) @QueryParam("campaign") @Pattern(regexp = DateFormats.YEAR_REGEX, message = "This is not a valid year. Excepted format : YYYY (e.g. 2017)") String campaign,
            @ApiParam(value = "Search by place", example = DocumentationAnnotation.EXAMPLE_EXPERIMENT_PLACE) @QueryParam("place") String place,
            @ApiParam(value = "Search by alias", example = DocumentationAnnotation.EXAMPLE_EXPERIMENT_ALIAS) @QueryParam("alias") String alias,
            @ApiParam(value = "Search by keywords", example = DocumentationAnnotation.EXAMPLE_EXPERIMENT_KEYWORDS) @QueryParam("keywords") String keywords) {

        ExperimentSQLDAO experimentDao = new ExperimentSQLDAO();

        if (uri != null) {
            experimentDao.uri = uri;
        }
        if (projectUri != null) {
            experimentDao.projectUri = projectUri;
        }
        if (startDate != null) {
            experimentDao.startDate = startDate;
        }
        if (endDate != null) {
            experimentDao.endDate = endDate;
        }
        if (field != null) {
            experimentDao.field = field;
        }
        if (campaign != null) {
            experimentDao.campaign = campaign;
        }
        if (place != null) {
            experimentDao.place = place;
        }
        if (alias != null) {
            experimentDao.alias = alias;
        }
        if (keywords != null) {
            experimentDao.keyword = keywords;
        }

        experimentDao.user = userSession.getUser();
        experimentDao.setPage(page);
        experimentDao.setPageSize(limit);

        return getExperimentsData(experimentDao);
    }

    /**
     * Get an experiment.
     *
     * @param experimentURI
     * @param limit
     * @param page
     * @return the experiment corresponding to the URI given
     */
    @GET
    @Path("{experiment}")
    @ApiOperation(value = "Get an experiment",
            notes = "Retrieve an experiment. Need URL encoded experiment URI (Unique resource identifier).")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Retrieve an experiment.", response = Experiment.class, responseContainer = "List"),
        @ApiResponse(code = 400, message = DocumentationAnnotation.BAD_USER_INFORMATION),
        @ApiResponse(code = 401, message = DocumentationAnnotation.USER_NOT_AUTHORIZED),
        @ApiResponse(code = 500, message = DocumentationAnnotation.ERROR_FETCH_DATA)
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "Authorization", required = true,
                dataType = "string", paramType = "header",
                value = DocumentationAnnotation.ACCES_TOKEN,
                example = GlobalWebserviceValues.AUTHENTICATION_SCHEME + " ")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getExperimentDetail(
            @ApiParam(
                    value = DocumentationAnnotation.EXPERIMENT_URI_DEFINITION,
                    example = DocumentationAnnotation.EXAMPLE_EXPERIMENT_URI,
                    required = true)
            @PathParam("experiment")
            @URL @Required String experimentURI,
            @ApiParam(value = DocumentationAnnotation.PAGE_SIZE)
            @QueryParam("pageSize")
            @DefaultValue(DefaultBrapiPaginationValues.PAGE_SIZE)
            @Min(0) int limit,
            @ApiParam(value = DocumentationAnnotation.PAGE)
            @QueryParam("page")
            @DefaultValue(DefaultBrapiPaginationValues.PAGE)
            @Min(0) int page) {
        if (experimentURI == null) {
            final Status status = new Status("Access error", StatusCodeMsg.ERR, "Empty Experiment URI");
            return Response.status(Response.Status.BAD_REQUEST).entity(new ResponseFormGET(status)).build();
        }

        ExperimentSQLDAO experimentDao = new ExperimentSQLDAO(experimentURI);
        experimentDao.setPageSize(limit);
        experimentDao.setPage(page);
        experimentDao.user = userSession.getUser();

        return getExperimentsData(experimentDao);
    }

    /**
     * @param experiments
     * @param context
     * @return result of the experiment creation request
     */
    @POST
    @ApiOperation(value = "Post a experiment",
            notes = "Register a new experiment in the database")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Experiment saved", response = ResponseFormPOST.class),
        @ApiResponse(code = 400, message = DocumentationAnnotation.BAD_USER_INFORMATION),
        @ApiResponse(code = 401, message = DocumentationAnnotation.USER_NOT_AUTHORIZED),
        @ApiResponse(code = 500, message = DocumentationAnnotation.ERROR_SEND_DATA)
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "Authorization", required = true,
                dataType = "string", paramType = "header",
                value = DocumentationAnnotation.ACCES_TOKEN,
                example = GlobalWebserviceValues.AUTHENTICATION_SCHEME + " ")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postExperiment(
            @ApiParam(value = DocumentationAnnotation.EXPERIMENT_POST_DATA_DEFINITION) @Valid ArrayList<ExperimentPostDTO> experiments,
            @Context HttpServletRequest context) {
        AbstractResultForm postResponse = null;

        // If there is at least an experiment in the data sent      
        if (experiments != null
                && !experiments.isEmpty()) {
            try {
                ExperimentSQLDAO experimentDao = new ExperimentSQLDAO();
                if (experimentDao.remoteUserAdress != null) {
                    experimentDao.remoteUserAdress = context.getRemoteAddr();
                }

                experimentDao.user = userSession.getUser();

                // Check and insert the experiments
                POSTResultsReturn result = experimentDao.checkAndInsertExperimentsList(experiments);

                if (result.getHttpStatus().equals(Response.Status.CREATED)) {
                    //Code 201, experiments created
                    postResponse = new ResponseFormPOST(result.statusList);
                    postResponse.getMetadata().setDatafiles(result.getCreatedResources());
                } else if (result.getHttpStatus().equals(Response.Status.BAD_REQUEST)
                        || result.getHttpStatus().equals(Response.Status.OK)
                        || result.getHttpStatus().equals(Response.Status.INTERNAL_SERVER_ERROR)) {
                    postResponse = new ResponseFormPOST(result.statusList);
                }
                return Response.status(result.getHttpStatus()).entity(postResponse).build();
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                return Response.status(Response.Status.BAD_REQUEST).entity(postResponse).build();
            }
        } else {
            postResponse = new ResponseFormPOST(new Status("Request error", StatusCodeMsg.ERR, "Empty experiment(s) to add"));
            return Response.status(Response.Status.BAD_REQUEST).entity(postResponse).build();
        }
    }

    /**
     * Experiment update service.
     *
     * @param experiments
     * @param context
     * @return the update result
     */
    @PUT
    @ApiOperation(value = "Update experiment")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Experiment updated", response = ResponseFormPOST.class),
        @ApiResponse(code = 400, message = DocumentationAnnotation.BAD_USER_INFORMATION),
        @ApiResponse(code = 404, message = "Experiment not found"),
        @ApiResponse(code = 500, message = DocumentationAnnotation.ERROR_SEND_DATA)
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "Authorization", required = true,
                dataType = "string", paramType = "header",
                value = DocumentationAnnotation.ACCES_TOKEN,
                example = GlobalWebserviceValues.AUTHENTICATION_SCHEME + " ")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response putExperiment(
            @ApiParam(value = DocumentationAnnotation.EXPERIMENT_POST_DATA_DEFINITION) @Valid ArrayList<ExperimentDTO> experiments,
            @Context HttpServletRequest context) {
        AbstractResultForm postResponse = null;

        if (experiments != null && !experiments.isEmpty()) {
            ExperimentSQLDAO experimentDao = new ExperimentSQLDAO();
            if (experimentDao.remoteUserAdress != null) {
                experimentDao.remoteUserAdress = context.getRemoteAddr();
            }
            experimentDao.user = userSession.getUser();

            // Check and update the experiments
            POSTResultsReturn result = experimentDao.checkAndUpdateList(experiments);

            if (result.getHttpStatus().equals(Response.Status.OK)) { //200: experiments updated
                postResponse = new ResponseFormPOST(result.statusList);
                return Response.status(result.getHttpStatus()).entity(postResponse).build();
            } else if (result.getHttpStatus().equals(Response.Status.BAD_REQUEST)
                    || result.getHttpStatus().equals(Response.Status.OK)
                    || result.getHttpStatus().equals(Response.Status.INTERNAL_SERVER_ERROR)) {
                postResponse = new ResponseFormPOST(result.statusList);
            }
            return Response.status(result.getHttpStatus()).entity(postResponse).build();
        } else {
            postResponse = new ResponseFormPOST(new Status("Request error", StatusCodeMsg.ERR, "Empty experiment(s) to update"));
            return Response.status(Response.Status.BAD_REQUEST).entity(postResponse).build();
        }
    }

    /**
     * Updates the variables linked to an experiment.
     * @example
     * [
     *   "http://www.opensilex.fr/platform/id/variables/v001",
     *   "http://www.opensilex.fr/platform/id/variables/v003"
     * ]
     * @param variables
     * @param uri
     * @param context
     * @return the result
     * @example 
     * {
     *      "metadata": {
     *          "pagination": null,
     *          "status": [
     *              {
     *                  "message": "Resources updated",
     *                  "exception": {
     *                      "type": "Info",
     *                      "href": null,
     *                      "details": "The experiment http://www.opensilex.fr/platform/OSL2015-1 has now 2 linked variables"
     *                  }
     *              }
     *          ],
     *          "datafiles": [
     *              "http://www.opensilex.fr/platform/OSL2015-1"
     *          ]
     *      }
     * }
     */
    @PUT
    @Path("{uri}/variables")
    @ApiOperation(value = "Update the observed variables of an experiment")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Measured observed variables of the experiment updated", response = ResponseFormPOST.class),
        @ApiResponse(code = 400, message = DocumentationAnnotation.BAD_USER_INFORMATION),
        @ApiResponse(code = 401, message = DocumentationAnnotation.USER_NOT_AUTHORIZED),
        @ApiResponse(code = 500, message = DocumentationAnnotation.ERROR_SEND_DATA)
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = GlobalWebserviceValues.AUTHORIZATION, required = true,
                dataType = GlobalWebserviceValues.DATA_TYPE_STRING, paramType = GlobalWebserviceValues.HEADER,
                value = DocumentationAnnotation.ACCES_TOKEN,
                example = GlobalWebserviceValues.AUTHENTICATION_SCHEME + " ")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response putVariables(
            @ApiParam(value = DocumentationAnnotation.LINK_VARIABLES_DEFINITION) @URL ArrayList<String> variables,
            @ApiParam(
                    value = DocumentationAnnotation.EXPERIMENT_URI_DEFINITION,
                    example = DocumentationAnnotation.EXAMPLE_EXPERIMENT_URI,
                    required = true)
            @PathParam("uri") @Required @URL String uri,
            @Context HttpServletRequest context) {
        AbstractResultForm postResponse = null;

        ExperimentSQLDAO experimentDAO = new ExperimentSQLDAO();
        if (context.getRemoteAddr() != null) {
            experimentDAO.remoteUserAdress = context.getRemoteAddr();
        }

        experimentDAO.user = userSession.getUser();

        POSTResultsReturn result = experimentDAO.checkAndUpdateLinkedVariables(uri, variables);

        if (result.getHttpStatus().equals(Response.Status.CREATED)) {
            postResponse = new ResponseFormPOST(result.statusList);
            postResponse.getMetadata().setDatafiles(result.getCreatedResources());
        } else if (result.getHttpStatus().equals(Response.Status.BAD_REQUEST)
                || result.getHttpStatus().equals(Response.Status.OK)
                || result.getHttpStatus().equals(Response.Status.INTERNAL_SERVER_ERROR)) {
            postResponse = new ResponseFormPOST(result.statusList);
        }

        return Response.status(result.getHttpStatus()).entity(postResponse).build();
    }

    /**
     * Updates the sensors linked to an experiment.
     * @example
     * [
     *      "http://www.phenome-fppn.fr/opensilex/2018/s18001"
     * ]
     * @param sensors
     * @param uri
     * @param context
     * @return the query result
     * @example
     * {
     *      "metadata": {
     *          "pagination": null,
     *          "status": [
     *              {
     *                  "message": "Resources updated",
     *                  "exception": {
     *                      "type": "Info",
     *                      "href": null,
     *                      "details": "The experiment http://www.opensilex.fr/platform/OSL2018-1 has now 1 linked sensors"
     *                  }
     *              }
     *          ],
     *          "datafiles": [
     *              "http://www.opensilex.fr/platform/OSL2015-1"
     *          ]
     *      }
     * }
     */
    @PUT
    @Path("{uri}/sensors")
    @ApiOperation(value = "Update the sensors which participates in an experiment")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "The list of sensors which participates in the experiment updated", response = ResponseFormPOST.class),
        @ApiResponse(code = 400, message = DocumentationAnnotation.BAD_USER_INFORMATION),
        @ApiResponse(code = 401, message = DocumentationAnnotation.USER_NOT_AUTHORIZED),
        @ApiResponse(code = 500, message = DocumentationAnnotation.ERROR_SEND_DATA)
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = GlobalWebserviceValues.AUTHORIZATION, required = true,
                dataType = GlobalWebserviceValues.DATA_TYPE_STRING, paramType = GlobalWebserviceValues.HEADER,
                value = DocumentationAnnotation.ACCES_TOKEN,
                example = GlobalWebserviceValues.AUTHENTICATION_SCHEME + " ")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response putSensors(
            @ApiParam(value = DocumentationAnnotation.LINK_SENSORS_DEFINITION) @URL ArrayList<String> sensors,
            @ApiParam(value = DocumentationAnnotation.EXPERIMENT_URI_DEFINITION, example = DocumentationAnnotation.EXAMPLE_EXPERIMENT_URI, required = true)
            @PathParam("uri")
            @Required
            @URL String uri,
            @Context HttpServletRequest context) {
        AbstractResultForm postResponse = null;

        ExperimentSQLDAO experimentDAO = new ExperimentSQLDAO();
        if (context.getRemoteAddr() != null) {
            experimentDAO.remoteUserAdress = context.getRemoteAddr();
        }

        experimentDAO.user = userSession.getUser();

        POSTResultsReturn result = experimentDAO.checkAndUpdateLinkedSensors(uri, sensors);

        if (result.getHttpStatus().equals(Response.Status.CREATED)) {
            postResponse = new ResponseFormPOST(result.statusList);
            postResponse.getMetadata().setDatafiles(result.getCreatedResources());
        } else if (result.getHttpStatus().equals(Response.Status.BAD_REQUEST)
                || result.getHttpStatus().equals(Response.Status.OK)
                || result.getHttpStatus().equals(Response.Status.INTERNAL_SERVER_ERROR)) {
            postResponse = new ResponseFormPOST(result.statusList);
        }

        return Response.status(result.getHttpStatus()).entity(postResponse).build();
    }

    /**
     * Gets experiment data.
     *
     * @param experimentSQLDao
     * @return experiments found
     */
    private Response getExperimentsData(ExperimentSQLDAO experimentSQLDao) {
        ArrayList<Experiment> experiments = new ArrayList<>();
        ArrayList<Status> statusList = new ArrayList<>();
        ResultForm<Experiment> getResponse;
        Integer experimentsCount = experimentSQLDao.count();

        if (experimentsCount != null && experimentsCount == 0) {
            getResponse = new ResultForm<>(experimentSQLDao.getPageSize(), experimentSQLDao.getPage(), experiments, true, experimentsCount);
            return noResultFound(getResponse, statusList);
        } else {
            experiments = experimentSQLDao.allPaginate();

            if (experiments == null || experimentsCount == null) { //sql error
                getResponse = new ResultForm<>(0, 0, experiments, true, experimentsCount);
                return sqlError(getResponse, statusList);
            } else if (experiments.isEmpty()) { // no result found
                getResponse = new ResultForm<>(experimentSQLDao.getPageSize(), experimentSQLDao.getPage(), experiments, false, experimentsCount);
                return noResultFound(getResponse, statusList);
            } else { //results founded
                getResponse = new ResultForm<>(experimentSQLDao.getPageSize(), experimentSQLDao.getPage(), experiments, true, experimentsCount);
                getResponse.setStatus(statusList);
                return Response.status(Response.Status.OK).entity(getResponse).build();
            }
        }
    }

    /**
     * Experiment Data GET service.
     *
     * @param pageSize
     * @param page
     * @param provenanceUri
     * @param variablesUri
     * @param startDate
     * @param endDate
     * @param object
     * @param uri
     * @return list of the data corresponding to the search params given
     * @example { "metadata": { "pagination": { "pageSize": 20, "currentPage":
     * 0, "totalCount": 3, "totalPages": 1 }, "status": [], "datafiles": [] },
     * "result": { "data": [ { "uri":
     * "http://www.phenome-fppn.fr/diaphen/id/data/d2plf65my4rc2odiv2lbjgukc2zswkqyoddh25jtoy4b5pf3le3q4ec5c332f5cd44ce82977e404cebf83c",
     * "provenanceUri": "http://www.phenome-fppn.fr/mtp/2018/pv181515071552",
     * "objectUri": "http://www.phenome-fppn.fr/diaphen/2018/o18001199",
     * "variableUri": "http://www.phenome-fppn.fr/diaphen/id/variables/v009",
     * "date": "2017-06-15T00:00:00+0200", "value": 2.4 }, { "uri":
     * "http://www.phenome-fppn.fr/diaphen/id/data/pttdrrqybxoyku4img323dyrhmpp267mhnpiw3vld2wm6tap3vwq93b344c429ec45bb9b185edfe5bc2b64",
     * "provenanceUri": "http://www.phenome-fppn.fr/mtp/2018/pv181515071552",
     * "objectUri": "http://www.phenome-fppn.fr/diaphen/2018/o18001199",
     * "variableUri": "http://www.phenome-fppn.fr/diaphen/id/variables/v009",
     * "date": "2017-06-16T00:00:00+0200", "value": "2017-06-15T00:00:00+0200" }
     * ] } }
     */
    @GET
    @Path("{uri}/data")
    @ApiOperation(value = "Get data corresponding to the search parameters given.",
            notes = "Retrieve all data corresponding to the search parameters given,"
            + "<br/>Date parameters could be either a datetime like: " + DocumentationAnnotation.EXAMPLE_XSDDATETIME
            + "<br/>or simply a date like: " + DocumentationAnnotation.EXAMPLE_DATE)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Retrieve all data", response = Data.class, responseContainer = "List"),
        @ApiResponse(code = 400, message = DocumentationAnnotation.BAD_USER_INFORMATION),
        @ApiResponse(code = 401, message = DocumentationAnnotation.USER_NOT_AUTHORIZED),
        @ApiResponse(code = 500, message = DocumentationAnnotation.ERROR_FETCH_DATA)
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = GlobalWebserviceValues.AUTHORIZATION, required = true,
                dataType = GlobalWebserviceValues.DATA_TYPE_STRING, paramType = GlobalWebserviceValues.HEADER,
                value = DocumentationAnnotation.ACCES_TOKEN,
                example = GlobalWebserviceValues.AUTHENTICATION_SCHEME + " ")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getExperimentDataSearch(
            @PathParam("uri")
            @Required
            @URL String experimentUri,
            @ApiParam(value = DocumentationAnnotation.PAGE_SIZE) @QueryParam(GlobalWebserviceValues.PAGE_SIZE) @DefaultValue(DefaultBrapiPaginationValues.PAGE_SIZE) @Min(0) int pageSize,
            @ApiParam(value = DocumentationAnnotation.PAGE) @QueryParam(GlobalWebserviceValues.PAGE) @DefaultValue(DefaultBrapiPaginationValues.PAGE) @Min(0) int page,
            @ApiParam(value = "Search by variable uri", example = DocumentationAnnotation.EXAMPLE_VARIABLE_URI) @QueryParam("variableUri") @URL @Required String variableUri,
            @ApiParam(value = "Search by minimal date", example = DocumentationAnnotation.EXAMPLE_XSDDATETIME) @QueryParam("startDate") @Date({DateFormat.YMDTHMSZ, DateFormat.YMD}) String startDate,
            @ApiParam(value = "Search by maximal date", example = DocumentationAnnotation.EXAMPLE_XSDDATETIME) @QueryParam("endDate") @Date({DateFormat.YMDTHMSZ, DateFormat.YMD}) String endDate,
            @ApiParam(value = "Search by object uri", example = DocumentationAnnotation.EXAMPLE_SCIENTIFIC_OBJECT_URI) @QueryParam("objectUri") @URL String objectUri,
            @ApiParam(value = "Search by object label", example = DocumentationAnnotation.EXAMPLE_SCIENTIFIC_OBJECT_ALIAS) @QueryParam("objectLabel") String objectLabel,
            @ApiParam(value = "Search by provenance uri", example = DocumentationAnnotation.EXAMPLE_PROVENANCE_URI) @QueryParam("provenanceUri") @URL String provenanceUri,
            @ApiParam(value = "Search by provenance label", example = DocumentationAnnotation.EXAMPLE_PROVENANCE_LABEL) @QueryParam("provenanceLabel") String provenanceLabel,
            @ApiParam(value = "Date search result order ('true' for ascending and 'false' for descending)", example = "true") @QueryParam("dateSortAsc") boolean dateSortAsc
    ) {
        ArrayList<DataSearchDTO> list = new ArrayList<>();
        ArrayList<Status> statusList = new ArrayList<>();
        ResultForm<DataSearchDTO> getResponse;

        DataDAO dataDAO = new DataDAO();

        List<String> objectsUris = new ArrayList<>();
        List<String> provenancesUris = new ArrayList<>();

        Map<String, List<String>> objectsUrisAndLabels = new HashMap<>();
        Map<String, String> provenancesUrisAndLabels = new HashMap<>();

        //1. Get list of objects uris corresponding to the label given if needed.
        ScientificObjectRdf4jDAO scientificObjectDAO = new ScientificObjectRdf4jDAO();
        if (objectUri != null && !objectUri.isEmpty()) {
            objectsUrisAndLabels.put(objectUri, scientificObjectDAO.findLabelsForUri(objectUri));
        } else if (objectLabel != null && !objectLabel.isEmpty()) { //We need to get the list of the uris of the scientific object with this label (like)
            objectsUrisAndLabels = scientificObjectDAO.findUriAndLabelsByLabelAndRdfType(objectLabel, Oeso.CONCEPT_SCIENTIFIC_OBJECT.toString());
        }

        for (String uri : objectsUrisAndLabels.keySet()) {
            objectsUris.add(uri);
        }
        boolean wrongProv = false;
        //2. Get list of provenances uris corresponding to the label given if needed.
        ProvenanceDAO provenanceDAO = new ProvenanceDAO();
        if (provenanceUri != null && !provenanceUri.isEmpty()) {
            try {
                Provenance findById = provenanceDAO.findById(provenanceUri);
                if (findById.getExperiments().contains(experimentUri)) {
                    //If the provenance URI is given, we need the provenance label
                    provenancesUris.add(provenanceUri);
                }else{
                    wrongProv =true;
                }
            } catch (Exception ex) {
                wrongProv =true;
                LOGGER.error(ex.getMessage(),ex);           
            }

        } else if (provenanceLabel != null && !provenanceLabel.isEmpty()) {
            //If the provenance URI is empty and a label is given, we search the provenance(s) with the given label (like)
            provenancesUrisAndLabels = provenanceDAO.findUriAndLabelsByLabel(provenanceLabel);
            for (String uri : provenancesUrisAndLabels.keySet()) {
                Provenance findById;
                try {
                    findById = provenanceDAO.findById(uri);
                    if (findById.getExperiments().contains(experimentUri)) {
                        //If the provenance URI is given, we need the provenance label
                        provenancesUris.add(uri);
                    }
                } catch (Exception ex) {
                    LOGGER.error(ex.getMessage(), ex);
                }
            }
        }else{
            Provenance prov = new Provenance();
            List<String> exps = new ArrayList<>();
            exps.add(experimentUri);
            prov.setExperiments(exps);
            provenanceDAO.setPage(0);
            provenanceDAO.setPageSize(5000);

            ArrayList<Provenance> provenances = provenanceDAO.getProvenances(prov,null);
            for (Provenance provenance : provenances) {
                provenancesUris.add(provenance.getUri());
            }
            if(provenancesUris.isEmpty()){
                  wrongProv =true;
            }
        }

        //3. Get variable label
        VariableDAO variableDAO = new VariableDAO();
        if (!variableDAO.existAndIsVariable(variableUri)) {
            // Request failure
            getResponse = new ResultForm<>(0, 0, list, true, 0);
            statusList.add(new Status(StatusCodeMsg.DATA_ERROR, StatusCodeMsg.ERR, "Unknown variable URI : " + variableUri));
            getResponse.setStatus(statusList);
            return Response.status(Response.Status.NOT_FOUND).entity(getResponse).build();
        }
        String variableLabel = variableDAO.findLabelsForUri(variableUri).get(0);
        
        List<Data> dataList = null;
        Integer totalCount = 0;
        if(!wrongProv){
            //4. Get count
            totalCount = dataDAO.count(variableUri, startDate, endDate, objectsUris, provenancesUris);

            //5. Get data
            dataList = dataDAO.find(page, pageSize, variableUri, startDate, endDate, objectsUris, provenancesUris);
        } 
        //6. Return result
        if (dataList == null) {
            // Request failure
            getResponse = new ResultForm<>(0, 0, list, true, 0);
            return noResultFound(getResponse, statusList);
        } else if (dataList.isEmpty()) {
            // No results
            getResponse = new ResultForm<>(0, 0, list, true, 0);
            return noResultFound(getResponse, statusList);
        } else {
            // Convert all data object to DTO's
            for (Data data : dataList) {
                if (data.getObjectUri() != null && !objectsUrisAndLabels.containsKey(data.getObjectUri())) {
                    //We need to get the labels of the object
                    objectsUrisAndLabels.put(data.getObjectUri(), scientificObjectDAO.findLabelsForUri(data.getObjectUri()));
                }

                if (!provenancesUrisAndLabels.containsKey(data.getProvenanceUri())) {
                    //We need to get the label of the provenance
                    provenancesUrisAndLabels.put(data.getProvenanceUri(), provenanceDAO.findLabelByUri(data.getProvenanceUri()));
                }

                //Get provenance label
                String dataProvenanceLabel = provenancesUrisAndLabels.get(data.getProvenanceUri());
                //Get object labels
                List<String> dataObjectLabels = new ArrayList<>();
                if (objectsUrisAndLabels.get(data.getObjectUri()) != null) {
                    dataObjectLabels = objectsUrisAndLabels.get(data.getObjectUri());
                }

                list.add(new DataSearchDTO(data, dataProvenanceLabel, dataObjectLabels, variableLabel));
            }

            // Return list of DTO
            getResponse = new ResultForm<>(pageSize, page, list, true, totalCount);
            getResponse.setStatus(statusList);
            return Response.status(Response.Status.OK).entity(getResponse).build();
        }
    }
    
    // /**
    //  * Experiment Data GET service.
    //  *
    //  * @param experimentUri
    //  * @param pageSize
    //  * @param page
    //  * @param provenanceUri
    //  * @param startDate
    //  * @param endDate
    //  * @param uri
    //  * @return list of the data corresponding to the search params given
    //  * @example { "metadata": { "pagination": { "pageSize": 20, "currentPage":
    //  * 0, "totalCount": 3, "totalPages": 1 }, "status": [], "datafiles": [] },
    //  * "result": { "data": [ { "uri":
    //  * "http://www.phenome-fppn.fr/diaphen/id/data/d2plf65my4rc2odiv2lbjgukc2zswkqyoddh25jtoy4b5pf3le3q4ec5c332f5cd44ce82977e404cebf83c",
    //  * "provenanceUri": "http://www.phenome-fppn.fr/mtp/2018/pv181515071552",
    //  * "objectUri": "http://www.phenome-fppn.fr/diaphen/2018/o18001199",
    //  * "variableUri": "http://www.phenome-fppn.fr/diaphen/id/variables/v009",
    //  * "date": "2017-06-15T00:00:00+0200", "value": 2.4 }, { "uri":
    //  * "http://www.phenome-fppn.fr/diaphen/id/data/pttdrrqybxoyku4img323dyrhmpp267mhnpiw3vld2wm6tap3vwq93b344c429ec45bb9b185edfe5bc2b64",
    //  * "provenanceUri": "http://www.phenome-fppn.fr/mtp/2018/pv181515071552",
    //  * "objectUri": "http://www.phenome-fppn.fr/diaphen/2018/o18001199",
    //  * "variableUri": "http://www.phenome-fppn.fr/diaphen/id/variables/v009",
    //  * "date": "2017-06-16T00:00:00+0200", "value": "2017-06-15T00:00:00+0200" }
    //  * ] } }
    //  */
    // @GET
    // @Path("{uri}/allData")
    // @ApiOperation(value = "Get data corresponding to the search parameters given.",
    //         notes = "Retrieve all data corresponding to the search parameters given,"
    //         + "<br/>Date parameters could be either a datetime like: " + DocumentationAnnotation.EXAMPLE_XSDDATETIME
    //         + "<br/>or simply a date like: " + DocumentationAnnotation.EXAMPLE_DATE)
    // @ApiResponses(value = {
    //     @ApiResponse(code = 200, message = "Retrieve all data", response = Data.class, responseContainer = "List"),
    //     @ApiResponse(code = 400, message = DocumentationAnnotation.BAD_USER_INFORMATION),
    //     @ApiResponse(code = 401, message = DocumentationAnnotation.USER_NOT_AUTHORIZED),
    //     @ApiResponse(code = 500, message = DocumentationAnnotation.ERROR_FETCH_DATA)
    // })
    // @ApiImplicitParams({
    //     @ApiImplicitParam(name = GlobalWebserviceValues.AUTHORIZATION, required = true,
    //             dataType = GlobalWebserviceValues.DATA_TYPE_STRING, paramType = GlobalWebserviceValues.HEADER,
    //             value = DocumentationAnnotation.ACCES_TOKEN,
    //             example = GlobalWebserviceValues.AUTHENTICATION_SCHEME + " ")
    // })
    // @Produces(MediaType.APPLICATION_JSON)
    // public Response getExperimentData(
    //         @PathParam("uri")
    //         @Required
    //         @URL String experimentUri    ) {
    //     ArrayList<DataSearchDTO> list = new ArrayList<>();
    //     ArrayList<Status> statusList = new ArrayList<>();
    //     ResultForm<DataSearchDTO> getResponse;

    //     DataDAO dataDAO = new DataDAO();

    //     List<String> objectsUris = new ArrayList<>();
    //     List<String> provenancesUris = new ArrayList<>();

    //     Map<String, List<String>> objectsUrisAndLabels = new HashMap<>();
    //     Map<String, String> provenancesUrisAndLabels = new HashMap<>();
 
    //     //2. Get list of provenances uris corresponding to the label given if needed.
    //     ProvenanceDAO provenanceDAO = new ProvenanceDAO();
    //     ArrayList<Provenance> provenances = provenanceDAO.getProvenances(new Provenance(), null);

    //     //3. Get list of sensors

    //     ExperimentRdf4jDAO experimentRdf4jDAO = new ExperimentRdf4jDAO();
    //     HashMap<String, String> sensors = experimentRdf4jDAO.getSensors(experimentUri);
        
    //     for (Provenance prov : provenances ) {
    //         if (prov.getExperiments().contains(experimentUri)) {
    //             //If the provenance URI is given, we need the provenance label
    //             provenancesUris.add(prov.getUri());
    //             provenancesUrisAndLabels.put(prov.getUri(), prov.getLabel());
    //         }
    //         Map<String,Object> metadata = (Map<String,Object>)  prov.getMetadata();
    //         if(metadata.get("prov:Agent") != null){
    //             System.out.println("opensilex.service.resource.ExperimentResourceService.getExperimentData()");
    //             System.out.println(metadata.toString());

    //             List<Map<String,String>> agents = (List<Map<String,String>>) metadata.get("prov:Agent");
    //             for (Map<String, String> agent : agents) {
    //                 if(agent.containsKey("rdf:type") && agent.get("rdf:type").equals("oeso:SensingDevice")){
    //                     if(sensors.containsKey(agent.get("prov:id"))){
    //                         provenancesUris.add(agent.get("prov:id"));
    //                         provenancesUrisAndLabels.put(prov.getUri(), prov.getLabel());
    //                     }
    //                 }
    //             }
    //         } 
    //     }
        
    //     //4. Get variable label
    //     Map<String, String> variablesUrisAndLabels = experimentRdf4jDAO.getVariables(experimentUri);
    //     List<String> keySet =  new ArrayList<>(variablesUrisAndLabels.keySet()); 
    //     List<Data> dataList = new ArrayList<>();
    //     Integer totalCount = 0;
        
    //     for (String variableUri : keySet) {
    //         totalCount = dataDAO.count( variableUri, null, null, objectsUris, provenancesUris);
    //         if(totalCount >0){
    //              dataList.addAll(dataDAO.find(0, 4000000, variableUri, null, null, null, provenancesUris)) ;
    //         }
    //     }
      
    //     System.out.println("opensilex.service.resource.ExperimentResourceService.getExperimentData()");
    //     System.out.println(dataList.size());
      

    //     //6. Return result
    //     if (dataList == null) {
    //         // Request failure
    //         getResponse = new ResultForm<>(0, 0, list, true, 0);
    //         return noResultFound(getResponse, statusList);
    //     } else if (dataList.isEmpty()) {
    //         // No results
    //         getResponse = new ResultForm<>(0, 0, list, true, 0);
    //         return noResultFound(getResponse, statusList);
    //     } else {
    //         ScientificObjectRdf4jDAO scientificObjectDAO = new ScientificObjectRdf4jDAO();

    //         // Convert all data object to DTO's
    //         for (Data data : dataList) {
    //             if (data.getObjectUri() != null && !objectsUrisAndLabels.containsKey(data.getObjectUri())) {
    //                 //We need to get the labels of the object
    //                 objectsUrisAndLabels.put(data.getObjectUri(), scientificObjectDAO.findLabelsForUri(data.getObjectUri()));
    //             }

    //             if (!provenancesUrisAndLabels.containsKey(data.getProvenanceUri())) {
    //                 //We need to get the label of the provenance
    //                 provenancesUrisAndLabels.put(data.getProvenanceUri(), provenanceDAO.findLabelByUri(data.getProvenanceUri()));
    //             }
                 
    //             //Get provenance label
    //             String dataProvenanceLabel = provenancesUrisAndLabels.get(data.getProvenanceUri());
    //             //Get object labels
    //             List<String> dataObjectLabels = new ArrayList<>();
    //             if (objectsUrisAndLabels.get(data.getObjectUri()) != null) {
    //                 dataObjectLabels = objectsUrisAndLabels.get(data.getObjectUri());
    //             }

    //             list.add(new DataSearchDTO(data, dataProvenanceLabel, dataObjectLabels, variablesUrisAndLabels.get(data.getVariableUri())));
    //         }

    //         // Return list of DTO
    //         getResponse = new ResultForm<>(0, 0, list, true, totalCount);
    //         getResponse.setStatus(statusList);
    //         return Response.status(Response.Status.OK).entity(getResponse).build();
    //     }
    // }

//    //List provenances
//    // search provenance by experiments
//    Provenance searchProvenance = new Provenance();
//    String queryExp = BasicDBObjectBuilder.start("experiments", new BasicDBObject("$in", Collections.singletonList(uri))).get().toString();
//    ArrayList<Provenance> provenances = provenanceDAO.getProvenances(searchProvenance, queryExp);
//    for (Provenance provenance : provenances) {
//                provenanceUrisAssociatedToSensor.add(provenance.getUri());
//    }
//    // Search provenance by sensors
//    ExperimentRdf4jDAO experimentDao = new ExperimentRdf4jDAO();
//    HashMap<String, String> sensors = experimentDao.getSensors(uri);
//    Set<String> sensorskeySet = sensors.keySet();
//
//    if (!sensors.isEmpty () 
//        ) {
//                String querySensor = BasicDBObjectBuilder.start("metadata.prov:Agent.prov:id", new BasicDBObject("$in", sensorskeySet)).get().toString();
//        ArrayList<Provenance> provenancesSensors = provenanceDAO.getProvenances(searchProvenance, querySensor);
//        for (Provenance provenance : provenancesSensors) {
//            if (!provenanceUrisAssociatedToSensor.contains(provenance.getUri())) {
//                provenanceUrisAssociatedToSensor.add(provenance.getUri());
//            }
//        }
//    }

    /**
     * Service to search data
     *
     * @param uri
     * @param pageSize
     * @param page
     * @param variableUri
     * @param startDate
     * @param endDate
     * @param objectUri
     * @param objectLabel
     * @param provenanceUri
     * @param provenanceLabel
     * @param dateSortAsc
     * @return list of the data corresponding to the search params given
     * @example { "metadata": { "pagination": { "pageSize": 20, "currentPage":
     * 0, "totalCount": 3, "totalPages": 1 }, "status": [], "datafiles": [] },
     * "result": { "data": [ { "uri":
     * "http://www.opensilex.org/opensilex/id/data/k3zilz2rrjhkxo4ppy43372rr5hyrbehjuf2stecbekvkxyqcjdq84b1df953972418a8d5808ba2bca3baedfsf",
     * "provenance": { "uri":
     * "http://www.opensilex.org/opensilex/id/provenance/1552386023784",
     * "label": "provenance-label" }, "object": { "uri":
     * "http://www.opensilex.org/opensilex/2019/o19000060", "labels": [ "2" ] },
     * "variable": { "uri":
     * "http://www.opensilex.org/opensilex/id/variables/v001", "label":
     * "trait_method_unit" }, "date": "2014-01-04T00:55:00+0100", "value": "19"
     * }, ] } }
     */
//    @GET
////    @Path("{uri}/data")
//    @ApiOperation(value = "Get data corresponding to the search parameters given.",
//            notes = "Retrieve all data corresponding to the search parameters given,"
//            + "<br/>Date parameters could be either a datetime like: " + DocumentationAnnotation.EXAMPLE_XSDDATETIME
//            + "<br/>or simply a date like: " + DocumentationAnnotation.EXAMPLE_DATE)
//    @ApiResponses(value = {
//        @ApiResponse(code = 200, message = "Retrieve all data", response = DataSearchDTO.class, responseContainer = "List"),
//        @ApiResponse(code = 400, message = DocumentationAnnotation.BAD_USER_INFORMATION),
//        @ApiResponse(code = 401, message = DocumentationAnnotation.USER_NOT_AUTHORIZED),
//        @ApiResponse(code = 500, message = DocumentationAnnotation.ERROR_FETCH_DATA)
//    })
//    @ApiImplicitParams({
//        @ApiImplicitParam(name = GlobalWebserviceValues.AUTHORIZATION, required = true,
//                dataType = GlobalWebserviceValues.DATA_TYPE_STRING, paramType = GlobalWebserviceValues.HEADER,
//                value = DocumentationAnnotation.ACCES_TOKEN,
//                example = GlobalWebserviceValues.AUTHENTICATION_SCHEME + " ")
//    })
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response getDataSearch(
//            @PathParam("uri") @Required @URL String uri,
//            @ApiParam(value = DocumentationAnnotation.PAGE_SIZE) @QueryParam(GlobalWebserviceValues.PAGE_SIZE) @DefaultValue(DefaultBrapiPaginationValues.PAGE_SIZE) @Min(0) int pageSize,
//            @ApiParam(value = DocumentationAnnotation.PAGE) @QueryParam(GlobalWebserviceValues.PAGE) @DefaultValue(DefaultBrapiPaginationValues.PAGE) @Min(0) int page,
//            @ApiParam(value = "Search by variable uri", example = DocumentationAnnotation.EXAMPLE_VARIABLE_URI) @QueryParam("variableUri") @URL @Required String variableUri,
//            @ApiParam(value = "Search by minimal date", example = DocumentationAnnotation.EXAMPLE_XSDDATETIME) @QueryParam("startDate") @Date({DateFormat.YMDTHMSZ, DateFormat.YMD}) String startDate,
//            @ApiParam(value = "Search by maximal date", example = DocumentationAnnotation.EXAMPLE_XSDDATETIME) @QueryParam("endDate") @Date({DateFormat.YMDTHMSZ, DateFormat.YMD}) String endDate,
//            @ApiParam(value = "Search by object uri", example = DocumentationAnnotation.EXAMPLE_SCIENTIFIC_OBJECT_URI) @QueryParam("objectUri") @URL String objectUri,
//            @ApiParam(value = "Search by object label", example = DocumentationAnnotation.EXAMPLE_SCIENTIFIC_OBJECT_ALIAS) @QueryParam("objectLabel") String objectLabel,
//            @ApiParam(value = "Search by provenance uri", example = DocumentationAnnotation.EXAMPLE_PROVENANCE_URI) @QueryParam("provenanceUri") @URL String provenanceUri,
//            @ApiParam(value = "Search by provenance label", example = DocumentationAnnotation.EXAMPLE_PROVENANCE_LABEL) @QueryParam("provenanceLabel") String provenanceLabel,
//            @ApiParam(value = "Date search result order ('true' for ascending and 'false' for descending)", example = "true") @QueryParam("dateSortAsc") boolean dateSortAsc
//    ) {
//        ArrayList<DataSearchDTO> list = new ArrayList<>();
//        ArrayList<Status> statusList = new ArrayList<>();
//        ResultForm<DataSearchDTO> getResponse;
//
//        DataDAO dataDAO = new DataDAO();
//
//        List<String> objectsUris = new ArrayList<>();
//        List<String> provenancesUris = new ArrayList<>();
//
//        Map<String, List<String>> objectsUrisAndLabels = new HashMap<>();
//        Map<String, String> provenancesUrisAndLabels = new HashMap<>();
//
//        //1. Get list of objects uris corresponding to the label given if needed.
//        ScientificObjectRdf4jDAO scientificObjectDAO = new ScientificObjectRdf4jDAO();
//        if (objectUri != null && !objectUri.isEmpty()) {
//            objectsUrisAndLabels.put(objectUri, scientificObjectDAO.findLabelsForUri(objectUri));
//        } else if (objectLabel != null && !objectLabel.isEmpty()) { //We need to get the list of the uris of the scientific object with this label (like)
//            objectsUrisAndLabels = scientificObjectDAO.findUriAndLabelsByLabelAndRdfType(objectLabel, Oeso.CONCEPT_SCIENTIFIC_OBJECT.toString());
//        }
//
//        for (String objectUriInfo : objectsUrisAndLabels.keySet()) {
//            objectsUris.add(objectUriInfo);
//        }
//
//        //2. Get list of provenances uris corresponding to the label given if needed.
//        ProvenanceDAO provenanceDAO = new ProvenanceDAO();
//        if (provenanceUri != null && !provenanceUri.isEmpty()) {
//            //If the provenance URI is given, we need the provenance label
//            provenancesUris.add(provenanceUri);
//        } else if (provenanceLabel != null && !provenanceLabel.isEmpty()) {
//            //If the provenance URI is empty and a label is given, we search the provenance(s) with the given label (like)
//            provenancesUrisAndLabels = provenanceDAO.findUriAndLabelsByLabel(provenanceLabel);
//        } else {
//            // search provenance by experiments
//            Provenance searchProvenance = new Provenance();
//            String queryExp = BasicDBObjectBuilder.start("experiments", new BasicDBObject("$in", Collections.singletonList(uri))).get().toString();
//            ArrayList<Provenance> provenances = provenanceDAO.getProvenances(searchProvenance, queryExp);
//            for (Provenance provenance : provenances) {
//                provenancesUris.add(provenance.getUri());
//            }
//
//            // Search provenance by sensors
//            ExperimentRdf4jDAO experimentDao = new ExperimentRdf4jDAO();
//            HashMap<String, String> sensors = experimentDao.getSensors(uri);
//            Set<String> sensorskeySet = sensors.keySet();
//            if (!sensors.isEmpty()) {
//                String querySensor = BasicDBObjectBuilder.start("metadata.prov:Agent.prov:id", new BasicDBObject("$in", sensorskeySet)).get().toString();
//                ArrayList<Provenance> provenancesSensors = provenanceDAO.getProvenances(searchProvenance, querySensor);
//                for (Provenance provenance : provenancesSensors) {
//                    if (!provenancesUris.contains(provenance.getUri())) {
//                        provenancesUris.add(provenance.getUri());
//                    }
//                }
//            }
//        }
//
//        for (String provUri : provenancesUrisAndLabels.keySet()) {
//            provenancesUris.add(provUri);
//        }
//
//        //3. Get variable label
//        VariableDAO variableDAO = new VariableDAO();
//        if (!variableDAO.existAndIsVariable(variableUri)) {
//            // Request failure
//            getResponse = new ResultForm<>(0, 0, list, true, 0);
//            statusList.add(new Status(StatusCodeMsg.DATA_ERROR, StatusCodeMsg.ERR, "Unknown variable URI : " + variableUri));
//            getResponse.setStatus(statusList);
//            return Response.status(Response.Status.NOT_FOUND).entity(getResponse).build();
//        }
//        String variableLabel = variableDAO.findLabelsForUri(variableUri).get(0);
//
//        //4. Get count
//        Integer totalCount = dataDAO.count(variableUri, startDate, endDate, objectsUris, provenancesUris);
//
//        //5. Get data
//        List<Data> dataList = dataDAO.find(page, pageSize, variableUri, startDate, endDate, objectsUris, provenancesUris);
//
//        //6. Return result
//        if (dataList == null) {
//            // Request failure
//            getResponse = new ResultForm<>(0, 0, list, true, 0);
//            return noResultFound(getResponse, statusList);
//        } else if (dataList.isEmpty()) {
//            // No results
//            getResponse = new ResultForm<>(0, 0, list, true, 0);
//            return noResultFound(getResponse, statusList);
//        } else {
//            // Convert all data object to DTO's
//            for (Data data : dataList) {
//                if (data.getObjectUri() != null && !objectsUrisAndLabels.containsKey(data.getObjectUri())) {
//                    //We need to get the labels of the object
//                    objectsUrisAndLabels.put(data.getObjectUri(), scientificObjectDAO.findLabelsForUri(data.getObjectUri()));
//                }
//
//                if (!provenancesUrisAndLabels.containsKey(data.getProvenanceUri())) {
//                    //We need to get the label of the provenance
//                    provenancesUrisAndLabels.put(data.getProvenanceUri(), provenanceDAO.findLabelByUri(data.getProvenanceUri()));
//                }
//
//                //Get provenance label
//                String dataProvenanceLabel = provenancesUrisAndLabels.get(data.getProvenanceUri());
//                //Get object labels
//                List<String> dataObjectLabels = new ArrayList<>();
//                if (objectsUrisAndLabels.get(data.getObjectUri()) != null) {
//                    dataObjectLabels = objectsUrisAndLabels.get(data.getObjectUri());
//                }
//
//                list.add(new DataSearchDTO(data, dataProvenanceLabel, dataObjectLabels, variableLabel));
//            }
//
//            // Return list of DTO
//            getResponse = new ResultForm<>(pageSize, page, list, true, totalCount);
//            getResponse.setStatus(statusList);
//            return Response.status(Response.Status.OK).entity(getResponse).build();
//        }
//    }

}
