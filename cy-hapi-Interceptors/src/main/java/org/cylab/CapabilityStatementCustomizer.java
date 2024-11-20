package org.cylab;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.ResponseDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.TerminologyCapabilities;


@Interceptor
public class CapabilityStatementCustomizer {

    @Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
    public void customizeCapabilityStatement(IBaseConformance theCapabilityStatement, RequestDetails theRequestDetails) {
        CapabilityStatement capabilityStatement = (CapabilityStatement) theCapabilityStatement;

        capabilityStatement.addInstantiates("http://hl7.org/fhir/CapabilityStatement/terminology-server");
        capabilityStatement.getSoftware().setName("CyLab FHIR Server");
    }

    @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
    public void onOutgoingResponse(
            RequestDetails theRequestDetails,
            ServletRequestDetails theServletRequestDetails,
            IBaseResource theResource, ResponseDetails theResponseDetails
    ) {
        if (theResource instanceof CapabilityStatement) {
            var params = theRequestDetails.getParameters();
            var mode = params.get("mode");
            if (mode != null) {
                var modeValue = mode[0];
                if (modeValue.equals("terminology")) {
                    TerminologyCapabilities tc = new TerminologyCapabilities();
                    CapabilityStatement cs = ((CapabilityStatement) theResource).copy();
                    tc.setId(cs.getId());
                    tc.setMeta(cs.getMeta());
                    tc.setUrl(cs.getUrl());
                    tc.setVersion(cs.getVersion());
                    tc.setName(cs.getName());
                    tc.setStatus(cs.getStatus());
                    tc.setDate(cs.getDate());

                    theResponseDetails.setResponseResource(tc);
                }
            }
        }
    }


}
