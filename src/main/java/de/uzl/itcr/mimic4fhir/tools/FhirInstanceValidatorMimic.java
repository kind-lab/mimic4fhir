package de.uzl.itcr.mimic4fhir.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;

public class FhirInstanceValidatorMimic {

	private static final Logger logger = LoggerFactory.getLogger(FhirInstanceValidatorMimic.class);

	private static FhirInstanceValidatorMimic instance;
	private static final FhirContext ctx = FhirContext.forR4();
	private static FhirValidator validator = ctx.newValidator();

	private FhirInstanceValidatorMimic() {
		ValidationSupportChain supportChain = new ValidationSupportChain();

		DefaultProfileValidationSupport defaultSupport = new DefaultProfileValidationSupport(ctx);
		CommonCodeSystemsTerminologyService codeService = new CommonCodeSystemsTerminologyService(ctx);
		InMemoryTerminologyServerValidationSupport validationSupport = new InMemoryTerminologyServerValidationSupport(
				ctx);

		// Add all support and service elements to the support chain
		supportChain.addValidationSupport(defaultSupport);
		supportChain.addValidationSupport(codeService);
		supportChain.addValidationSupport(validationSupport);

		// Get KDS profiles from project resources folder
		StructureDefinition mimicPatient = this.getStructureDefinition("mimic/snapshots/StructureDefinition-mimic-patient.json");
		// Get code systems
		CodeSystem organizationType = new CodeSystem().setUrl("http://hl7.org/fhir/organization-type");
		// Get value sets
		ValueSet admissionClass = this.getValueSet("mimic/valuesets/ValueSet-admission-class.json");
		// Get Extensions
		StructureDefinition dilutionDetails = this
				.getStructureDefinition("mimic/extensions/StructureDefinition-dilution-details.json");

		PrePopulatedValidationSupport prePopulatedSupport = new PrePopulatedValidationSupport(ctx);
		// Custom structure definitions
		prePopulatedSupport.addStructureDefinition(mimicPatient);

		// Custom code systems
		prePopulatedSupport.addCodeSystem(organizationType);

		// Custom value sets
		prePopulatedSupport.addValueSet(admissionClass);

		// Custom extensions
		prePopulatedSupport.addStructureDefinition(dilutionDetails);

		// Add PrePropulatedValidationSupport
		supportChain.addValidationSupport(prePopulatedSupport);

		CachingValidationSupport cachingChain = new CachingValidationSupport(supportChain);
		FhirInstanceValidator validatorModule = new FhirInstanceValidator(cachingChain);
		validator.registerValidatorModule(validatorModule);
	}

	public static FhirInstanceValidatorMimic getInstance() {
		if (instance == null)
			instance = new FhirInstanceValidatorMimic();
		return instance;
	}

	public void validateAndPrint(IBaseResource resource) {
		ValidationResult result = validator.validateWithResult(resource);
		List<SingleValidationMessage> messages = result.getMessages();
		if (result.isSuccessful()) {
			System.out.println("Validation was successful!");
		} else {
			System.out.println("Validation failed!");
		}
		for (SingleValidationMessage message : messages) {
			if (message.getSeverity() != ResultSeverityEnum.ERROR) {
				System.out.println("== Validation Message:");
				System.out.println("---- Location: " + message.getLocationString());
				System.out.println("---- Severity: " + message.getSeverity());
				System.out.println("---- Message:  " + message.getMessage());
			}
		}
	}

	// Idea: https://github.com/hapifhir/hapi-fhir/issues/552
	private String getProfileText(String pathToProfile) {
		String profileText = null;
		ClassLoader classLoader = getClass().getClassLoader();
		try (InputStream inputStream = classLoader.getResourceAsStream(pathToProfile)) {
			profileText = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return profileText;
	}

	private StructureDefinition getStructureDefinition(String pathToProfile) {
		String profileText = this.getProfileText(pathToProfile);
		return FhirInstanceValidatorMimic.ctx.newJsonParser().parseResource(StructureDefinition.class, profileText);
	}

	private org.hl7.fhir.r4.model.ValueSet getValueSet(String pathToProfile) {
		String profileText = this.getProfileText(pathToProfile);
		return FhirInstanceValidatorMimic.ctx.newJsonParser().parseResource(org.hl7.fhir.r4.model.ValueSet.class, profileText);
	}

	private org.hl7.fhir.r4.model.CodeSystem getCodeSystem(String pathToProfile) {
		String profileText = this.getProfileText(pathToProfile);
		return FhirInstanceValidatorMimic.ctx.newJsonParser().parseResource(org.hl7.fhir.r4.model.CodeSystem.class, profileText);
	}

}
