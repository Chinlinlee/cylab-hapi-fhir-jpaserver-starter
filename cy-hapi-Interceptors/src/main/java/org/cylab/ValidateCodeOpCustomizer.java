package org.cylab;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.ResponseDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidateCodeOpCustomizer {
    private static final Logger logger = LoggerFactory.getLogger(ValidateCodeOpCustomizer.class);

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLER_SELECTED)
    public void onIncomingRequest(ServletRequestDetails theRequestDetails, HttpServletRequest theServletRequest) throws IOException {

        if (theRequestDetails == null) return;


        String incomingResourceName = theRequestDetails.getResourceName();
        String incomingOperation = theRequestDetails.getOperation();

        if (incomingResourceName == null || incomingOperation == null) return;

        if (incomingResourceName.equals("ValueSet") && incomingOperation.equals("$validate-code") && theRequestDetails.getRequestType() == RequestTypeEnum.POST) {
            valueSetValidateCodePreProcess(theRequestDetails, theServletRequest);
        } else if (incomingResourceName.equals("CodeSystem") && incomingOperation.equals("$validate-code") && theRequestDetails.getRequestType() == RequestTypeEnum.POST) {
            codeSystemValidateCodePreProcess(theRequestDetails, theServletRequest);
        }
    }

    private static void valueSetValidateCodePreProcess(ServletRequestDetails theRequestDetails, HttpServletRequest theServletRequest) throws IOException {
        String contentType = theServletRequest.getContentType();
        if (contentType.contains("application/json") || contentType.contains("application/fhir+json")) {
            logger.info("Doing custom value set $validate-code pre-process");

            // 創建可修改的請求包裝器
            ModifiableHttpServletRequest modifiableRequest = new ModifiableHttpServletRequest(theServletRequest);

            byte[] requestBody = modifiableRequest.getBody();
            if (requestBody == null || requestBody.length == 0) {
                logger.info("Request body is empty, do nothing");
                return;
            }

            // 解析原始請求
            IParser parser = theRequestDetails.getFhirContext().newJsonParser();
            Parameters requestParams = (Parameters) parser.parseResource(new ByteArrayInputStream(requestBody));

            IFhirPath fhirPath = theRequestDetails.getFhirContext().newFhirPath();
            Optional<ValueSet> userValueSet = fhirPath.evaluateFirst(requestParams, "Parameters.parameter.where(name='valueSet').resource", ValueSet.class);
            Optional<UriType> userUrl = fhirPath.evaluateFirst(requestParams, "Parameters.parameter.where(name='url').value", UriType.class);
            Optional<Coding> userCoding = fhirPath.evaluateFirst(requestParams, "Parameters.parameter.where(name='coding').value", Coding.class);
            Optional<CodeType> userCode = fhirPath.evaluateFirst(requestParams, "Parameters.parameter.where(name='code').value", CodeType.class);


            if (userValueSet.isPresent() && userUrl.isEmpty() && (userCoding.isPresent() || userCode.isPresent())) {
                logger.info("Missing url in request, try to append it automatically");
                Parameters newParams = new Parameters();
                userCoding.ifPresent(coding -> newParams.addParameter(new Parameters.ParametersParameterComponent().setName("coding").setValue(coding)));
                userCode.ifPresent(code -> newParams.addParameter(new Parameters.ParametersParameterComponent().setName("code").setValue(code)));

                String url = userValueSet.get().getUrl();
                if (url == null) {
                    url = "nope";
                }
                url = url.replaceAll("--\\d+$", "");
                newParams.addParameter("url", new UriType(url));

                if (!url.startsWith("urn:uuid") &&
                    !url.startsWith("urn:oid") &&
                    !url.equals(CommonCodeSystemsTerminologyService.LANGUAGES_VALUESET_URL) &&
                    !url.equals(CommonCodeSystemsTerminologyService.MIMETYPES_VALUESET_URL) &&
                    !url.equals(CommonCodeSystemsTerminologyService.CURRENCIES_VALUESET_URL) &&
                    !url.equals(CommonCodeSystemsTerminologyService.UCUM_VALUESET_URL) &&
                    !url.equals(CommonCodeSystemsTerminologyService.ALL_LANGUAGES_VALUESET_URL) &&
                    !url.equals(CommonCodeSystemsTerminologyService.USPS_VALUESET_URL) &&
                    !isCommonCodeSystemInCompose(theRequestDetails.getFhirContext(), requestParams)
                ) {
                    // do nothing
                } else {
                    newParams.addParameter(new Parameters.ParametersParameterComponent().setName("valueSet").setResource(userValueSet.get()));
                }

                // 將修改後的參數轉換回 JSON
                String modifiedJson = parser.encodeResourceToString(newParams);
                modifiableRequest.setBody(modifiedJson.getBytes());

                theRequestDetails.setServletRequest(modifiableRequest);
            } else {
                theRequestDetails.setServletRequest(modifiableRequest);
            }
        }
    }

    private static boolean isCommonCodeSystemInCompose(FhirContext ctx, Parameters theRequestParams) {
        String whereCommonCode = String.format("Parameters.parameter.where(name='valueSet').resource.compose.include.where(system='%s' or system='%s' or system='%s' or system='%s' or system='%s')",
            CommonCodeSystemsTerminologyService.LANGUAGES_CODESYSTEM_URL,
            CommonCodeSystemsTerminologyService.MIMETYPES_CODESYSTEM_URL,
            CommonCodeSystemsTerminologyService.CURRENCIES_CODESYSTEM_URL,
            CommonCodeSystemsTerminologyService.UCUM_CODESYSTEM_URL,
            CommonCodeSystemsTerminologyService.USPS_CODESYSTEM_URL
        );
        Optional<ValueSet.ConceptSetComponent> systemUrl = ctx.newFhirPath().evaluateFirst(theRequestParams, whereCommonCode, ValueSet.ConceptSetComponent.class);

        return systemUrl.isPresent();
    }

    private static void codeSystemValidateCodePreProcess(ServletRequestDetails theRequestDetails, HttpServletRequest theServletRequest) throws IOException {
        String contentType = theServletRequest.getContentType();
        if (contentType.contains("application/json") || contentType.contains("application/fhir+json")) {
            logger.info("Doing custom code system $validate-code pre-process");

            // 創建可修改的請求包裝器
            ModifiableHttpServletRequest modifiableRequest = new ModifiableHttpServletRequest(theServletRequest);

            byte[] requestBody = modifiableRequest.getBody();
            if (requestBody == null || requestBody.length == 0) {
                logger.info("Request body is empty, do nothing");
                return;
            }

            // 解析原始請求
            IParser parser = theRequestDetails.getFhirContext().newJsonParser();
            Parameters requestParams = (Parameters) parser.parseResource(new ByteArrayInputStream(requestBody));

            IFhirPath fhirPath = theRequestDetails.getFhirContext().newFhirPath();

            Optional<Coding> userCoding = fhirPath.evaluateFirst(requestParams, "Parameters.parameter.where(name='coding').value", Coding.class);
            Optional<UriType> userUrl = fhirPath.evaluateFirst(requestParams, "Parameters.parameter.where(name='url').value", UriType.class);

            if (userCoding.isPresent() && userUrl.isEmpty()) {
                logger.info("Missing url in request, try to append it automatically");

                String url = userCoding.get().getSystem();

                url = url.replaceAll("--\\d+$", "");
                requestParams.addParameter("url", new UriType(url));

                // 將修改後的參數轉換回 JSON
                String modifiedJson = parser.encodeResourceToString(requestParams);
                modifiableRequest.setBody(modifiedJson.getBytes());

                theRequestDetails.setServletRequest(modifiableRequest);
            } else {
                theRequestDetails.setServletRequest(modifiableRequest);
            }
        }
    }

    @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
    public void onOutgoingResponse(
            RequestDetails theRequestDetails,
            ServletRequestDetails theServletRequestDetails,
            IBaseResource theResource,
            ResponseDetails theResponseDetails
    ) {

        String incomingResourceName = theRequestDetails.getResourceName();
        String incomingOperation = theRequestDetails.getOperation();
        if (incomingResourceName == null || incomingOperation == null) return;

        if (incomingResourceName.equals("ValueSet") && incomingOperation.equals("$validate-code") && theRequestDetails.getRequestType() == RequestTypeEnum.POST) {
            FhirContext ctx = FhirContext.forR4();
            IParser parser = ctx.newJsonParser();
            parser.setPrettyPrint(true);

            IFhirPath fhirPath = ctx.newFhirPath();
            Optional<Parameters.ParametersParameterComponent> result = fhirPath.evaluateFirst(theResource, "Parameters.parameter.where(name='result')", Parameters.ParametersParameterComponent.class);
            if (result.isPresent()) {
                Parameters.ParametersParameterComponent resultParam = result.get();
                boolean resultValue = Boolean.parseBoolean(String.valueOf(resultParam.getValue()));

                if (!resultValue) {
                    Parameters requestParams = (Parameters) theRequestDetails.getResource();
                    Optional<Parameters.ParametersParameterComponent> userValueSetParam = fhirPath.evaluateFirst(requestParams, "Parameters.parameter.where(name='valueSet')", Parameters.ParametersParameterComponent.class);
                    if (userValueSetParam.isPresent()) {
                        Optional<CodeType> theCode = fhirPath.evaluateFirst(requestParams, "Parameters.parameter.where(name='code').value", CodeType.class);
                        boolean validateSingleCodeResult = doValidateSingleCode(theRequestDetails.getFhirContext(), theCode, (ValueSet) userValueSetParam.get().getResource());
                        if (validateSingleCodeResult) {
                            Parameters goodParams = new Parameters();
                            goodParams.addParameter().setName("result").setValue(new BooleanType(true));
                            goodParams.addParameter().setName("message").setValue(new StringType("Code validated by common code system terminology service"));
                            theResponseDetails.setResponseResource(goodParams);
                        }
                    }
                }
            }
        }
    }


    private boolean doValidateSingleCode(FhirContext ctx, Optional<CodeType> code, ValueSet valueSet) {
        IFhirPath fhirPath = ctx.newFhirPath();
        // 正常進到這階段的 code 幾乎都是 common code
        String whereCommonCode = String.format("ValueSet.compose.include.where(system='%s' or system='%s' or system='%s' or system='%s' or system='%s').system",
                CommonCodeSystemsTerminologyService.LANGUAGES_CODESYSTEM_URL,
                CommonCodeSystemsTerminologyService.MIMETYPES_CODESYSTEM_URL,
                CommonCodeSystemsTerminologyService.CURRENCIES_CODESYSTEM_URL,
                CommonCodeSystemsTerminologyService.UCUM_CODESYSTEM_URL,
                CommonCodeSystemsTerminologyService.USPS_CODESYSTEM_URL
        );
        Optional<UriType> theSystem = fhirPath.evaluateFirst(valueSet, whereCommonCode, UriType.class);


        if (!code.isPresent() || !theSystem.isPresent()) {
            return false;
        }

        CommonCodeSystemsTerminologyService service = new CommonCodeSystemsTerminologyService(ctx);
        IValidationSupport.CodeValidationResult r = service.validateCode(
                new ValidationSupportContext(ctx.getValidationSupport()),
                new ConceptValidationOptions().setInferSystem(true),
                theSystem.get().getValue(),
                code.get().getCode(),
                null,
                null
        );
        if (r!=null) {
            return r.isOk();
        }
        return false;
    }
}
