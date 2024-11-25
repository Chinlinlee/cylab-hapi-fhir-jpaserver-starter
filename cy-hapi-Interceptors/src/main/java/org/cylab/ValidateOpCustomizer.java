package org.cylab;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import ca.uhn.fhir.util.OperationOutcomeUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class ValidateOpCustomizer {

    private static final String JSON_CONTENT_TYPE = "application/fhir+json";
    private static final String XML_CONTENT_TYPE = "application/fhir+xml";

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLER_SELECTED)
    public boolean onIncomingRequest(ServletRequestDetails theRequestDetails, HttpServletRequest theServletRequest, HttpServletResponse theServletResponse) throws IOException {
        if (!isValidateOperation(theRequestDetails)) {
            return  true;
        }

        String contentType = theServletRequest.getContentType();
        boolean isJson = contentType.contains("json");
        boolean isXml = contentType.contains("xml");

        if (!isJson && !isXml) {
            return true;
        }

        return processValidationRequest(theRequestDetails, theServletRequest, theServletResponse, isJson);
    }

    private boolean isValidateOperation(ServletRequestDetails theRequestDetails) {
        return theRequestDetails != null &&
                theRequestDetails.getOperation().equals("$validate") &&
                theRequestDetails.getRequestType() == RequestTypeEnum.POST;
    }

    private boolean processValidationRequest(ServletRequestDetails theRequestDetails,
                                             HttpServletRequest theServletRequest,
                                             HttpServletResponse theServletResponse,
                                             boolean isJson) throws IOException {

        ModifiableHttpServletRequest modifiableRequest = new ModifiableHttpServletRequest(theServletRequest);
        byte[] requestBody = modifiableRequest.getBody();

        if (requestBody == null || requestBody.length == 0) {
            return true;
        }

        // Parse request and convert to JSON if needed
        String requestResourceJson = parseRequestToJson(theRequestDetails, requestBody, isJson);

        // Validate using external service
        ValidationResponseResult response = validateResource(requestResourceJson);

        if (response.getStatus() != 200) {
            handleValidationError(theRequestDetails, theServletResponse, response, isJson);
            return false;
        }

        // Process successful validation
        OperationOutcome outcome = processValidationResponse(theRequestDetails, response);

        // Send response
        sendResponse(theRequestDetails, theServletResponse, outcome, response.getStatus(), isJson);
        return false;
    }


    private OperationOutcome processValidationResponse(ServletRequestDetails theRequestDetails,
                                                       ValidationResponseResult response) {

        IParser parser = theRequestDetails.getFhirContext().newJsonParser();
        OperationOutcome outcome = (OperationOutcome) parser.parseResource(response.getBody());

        // Add custom validation message
        outcome.addIssue(
                new OperationOutcome.OperationOutcomeIssueComponent()
                        .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                        .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                        .setDiagnostics("Validated by inferno validator wrapper")
        );
        Collections.swap(outcome.getIssue(), 0, outcome.getIssue().size() - 1);

        return outcome;
    }



    private void sendResponse(ServletRequestDetails requestDetails,
                              HttpServletResponse theServletResponse,
                              OperationOutcome outcome,
                              int status,
                              boolean isJson) throws IOException {

        if (hasErrors(requestDetails.getFhirContext(), outcome)) {
            theServletResponse.setStatus(422);
        } else {
            theServletResponse.setStatus(status);
        }

        IParser responseParser = getAppropriateParser(requestDetails, isJson);

        theServletResponse.setHeader("Content-Type", isJson ? JSON_CONTENT_TYPE : XML_CONTENT_TYPE);
        theServletResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        theServletResponse.getWriter().write(responseParser.encodeResourceToString(outcome));
        theServletResponse.getWriter().flush();
    }

    private String parseRequestToJson(ServletRequestDetails theRequestDetails, byte[] requestBody, boolean isJson) {
        IParser parser = getAppropriateParser(theRequestDetails, isJson);

        IBaseResource requestResource = parser.parseResource(new ByteArrayInputStream(requestBody));

        return theRequestDetails.getFhirContext().newJsonParser()
                .setPrettyPrint(true)
                .encodeResourceToString(requestResource);
    }

    private ValidationResponseResult validateResource(String requestResourceJson) {
        ValidatorCaller validatorCaller = new ValidatorCaller();
        return validatorCaller.sendJsonRequest(getInfernoUrl(), requestResourceJson);
    }

    private boolean hasErrors(FhirContext ctx, OperationOutcome outcome) {
        return OperationOutcomeUtil.hasIssuesOfSeverity(ctx, outcome,
                OperationOutcome.IssueSeverity.FATAL.toCode()) ||
                OperationOutcomeUtil.hasIssuesOfSeverity(ctx, outcome,
                        OperationOutcome.IssueSeverity.ERROR.toCode());
    }

    private void handleValidationError(ServletRequestDetails theRequestDetails,
                                       HttpServletResponse theServletResponse,
                                       ValidationResponseResult response,
                                       boolean isJson) throws IOException {

        OperationOutcome errorOutcome = createErrorOutcome(response.getBody());
        IParser parser = getAppropriateParser(theRequestDetails, isJson);

        theServletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        theServletResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        theServletResponse.setHeader("Content-Type", isJson ? JSON_CONTENT_TYPE : XML_CONTENT_TYPE);
        theServletResponse.getWriter().write(parser.encodeResourceToString(errorOutcome));
        theServletResponse.getWriter().flush();
    }

    private OperationOutcome createErrorOutcome(String errorMessage) {
        OperationOutcome errorOutcome = new OperationOutcome();
        errorOutcome.addIssue(
                new OperationOutcome.OperationOutcomeIssueComponent()
                        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                        .setCode(OperationOutcome.IssueType.EXCEPTION)
                        .setDiagnostics("Error validating request: " + errorMessage)
        );
        return errorOutcome;
    }

    private IParser getAppropriateParser(ServletRequestDetails theRequestDetails, boolean isJson) {
        return isJson ?
                theRequestDetails.getFhirContext().newJsonParser() :
                theRequestDetails.getFhirContext().newXmlParser();
    }

    String getInfernoUrl() {
        if (System.getenv("INFERNO_URL") != null) {
            return System.getenv("INFERNO_URL");
        } else {
            return "http://127.0.0.1:4567/validate";
        }
    }

}
