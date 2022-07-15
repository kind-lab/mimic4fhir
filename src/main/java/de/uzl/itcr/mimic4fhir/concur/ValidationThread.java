package de.uzl.itcr.mimic4fhir.concur;

import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;

import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

import de.uzl.itcr.mimic4fhir.queue.Sender;
import de.uzl.itcr.mimic4fhir.table.MimicFhirTable;
import de.uzl.itcr.mimic4fhir.work.BundleControl;
import de.uzl.itcr.mimic4fhir.work.Config;
import de.uzl.itcr.mimic4fhir.work.ConnectDB;
import de.uzl.itcr.mimic4fhir.work.FHIRComm;

public class ValidationThread implements Runnable{
	
	private static final Logger logger = LoggerFactory.getLogger(ConversionThread.class);
	private Sender sendr = new Sender();
	private BundleControl bundleC = new BundleControl();
	
	Patient patient;
	List<Resource> encounters;
	List<Resource> conditions;
	List<Resource> encounterIcus;		
	List<Resource> medicationAdministrations;
	List<Resource> medicationAdministrationIcus;
	List<Resource> medicationDispenses;
	List<Resource> medicationRequests;
	List<Resource> observationChartevents;
	List<Resource> observationDatetimeevents;
	
	List<Resource> observationMicroOrgs;
	List<Resource> observationMicroSuscs;
	List<Resource> observationMicroTests;
	List<Resource> observationOutputevents;
	List<Resource> procedures;
	List<Resource> procedure_icus;
	List<Resource> specimens;

	// TransformerHelper helper
	ConnectDB dbAccess;
	FHIRComm fhir;
	private Config config;
	String patientId;
	
	public ValidationThread(FHIRComm fhirComm, String patientId, Config config, ConnectDB dbAccess) {
		this.fhir = fhirComm;
		this.dbAccess = dbAccess;
		this.config = config;		
		this.patientId = patientId;
	}
	
	@Override
	public void run() {
		logger.info("Start of ValidationThread run");
		StopWatch watch = new StopWatch();
		watch.start();
		logger.info("Pat. {} - Query", this.patientId);
		logger.info("Pat. {} - Convert", this.patientId);
		processPatient(patientId);
		watch.stop();
		logger.info("Pat. {} - Done in {} ms", this.patientId, watch.getTotalTimeMillis());
		
		// Push end-Message to queue
		sendr.close();
		logger.info("End of ValidationThread run");
	}
	
	
	public void processPatient(String patientId) {
		try {
			System.out.println("before patient read");
			patient = dbAccess.getPatientSynchronized(patientId);
			System.out.println("after patient read");
			encounters = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.ENCOUNTER, patientId);
			System.out.println("after enc read");
			conditions = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.CONDITION, patientId);
			System.out.println("after cond read");
			encounterIcus = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.ENCOUNTER_ICU, patientId);		
			medicationAdministrations = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.MEDICATION_ADMINISTRATION, patientId);
			medicationAdministrationIcus = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.MEDICATION_ADMINISTRATION_ICU, patientId);
			medicationDispenses = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.MEDICATION_DISPENSE, patientId);
			medicationRequests = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.MEDICATION_REQUEST, patientId);
			observationChartevents = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_CHARTEVENTS, patientId);
			observationDatetimeevents = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_DATETIMEEVENTS, patientId);
			
			observationMicroOrgs = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_MICRO_ORG, patientId);
			observationMicroSuscs = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_MICRO_SUSC, patientId);
			observationMicroTests = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_MICRO_TEST, patientId);
			observationOutputevents = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_OUTPUTEVENTS, patientId);
			procedures = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.PROCEDURE, patientId);
			procedure_icus = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.PROCEDURE_ICU, patientId);
			specimens = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.SPECIMEN, patientId);
			
			// patient related
			logger.info("Patient Bundle");
			bundleC.addUUIDResourceToBundlePut(patient);
			addResourcesToBundle(encounters);
			addResourcesToBundle(encounterIcus);
			addResourcesToBundle(procedures);
			addResourcesToBundle(conditions);
			logger.info("Patient Bundle Size: " + bundleC.getNumberOfResources());
			submitBundle(patientId);
			
			
			//medication
			logger.info("Medication Bundle");
			addResourcesToBundle(medicationRequests);
			addResourcesToBundle(medicationAdministrations);
			addResourcesToBundle(medicationDispenses);
			
			addResourcesToBundle(medicationAdministrationIcus);
			logger.info("Medication Bundle Size: " + bundleC.getNumberOfResources());
			submitBundle(patientId);
			
			// micro
			logger.info("Microbiology Bundle");
			addResourcesToBundle(observationMicroOrgs);
			addResourcesToBundle(observationMicroSuscs);
			addResourcesToBundle(observationMicroTests);
			addResourcesToBundle(specimens);
			logger.info("Micro Bundle Size: " + bundleC.getNumberOfResources());
			submitBundle(patientId);
			
			// labs
	//				addResourcesToBundle(observationLabevents);
	//				addResourcesToBundle(specimenLabs);
			
			//icu	
			logger.info("ICU Bundle");
			addResourcesToBundle(procedure_icus);
			addResourcesToBundle(observationDatetimeevents);
			addResourcesToBundle(observationChartevents);
			addResourcesToBundle(observationOutputevents);
			logger.info("ICU Bundle Size: " + bundleC.getNumberOfResources());
			//bundleC.resetBundle();
			submitBundle(patientId);		
		} catch (Exception e) {
			System.out.println("SOME ERROR");
			logger.error("ERROR: %s", e);
		}
	}
	
	public void submitBundle(String patientId) {
		//Push bundle to queue
		System.out.println("Submitting bundle: " + patientId + "_" + bundleC.getInternalBundleNumber());
		JsonObject message = Json.createObjectBuilder()
				.add("number", patientId + "_" + bundleC.getInternalBundleNumber()) 
				.add("bundle", fhir.getBundleAsString(bundleC.getTransactionBundle()))
				.build();
	
		sendr.send(message.toString()); 	
		bundleC.resetBundle();
	}
	
	/**
	 * Add fhir list of fhir resources by UUID to current bundle
	 * @param resources fhir-resources to add
	 */
	private void addResourcesToBundle(List<Resource> resources) {
		System.out.println("Size: " + resources.size());
		resources.forEach((resource) -> {
			checkBundleLimit();
			bundleC.addUUIDResourceToBundlePut(resource);
		});
	}
	
	private void checkBundleLimit() {

		// if bundle exceeds 100 resources -> start new bundle
		if (bundleC.getNumberOfResources() > 100) {
			// Push bundle to queue
			JsonObject message = Json.createObjectBuilder()
					.add("number", "bundle_" + bundleC.getInternalBundleNumber())
					.add("bundle", fhir.getBundleAsString(bundleC.getTransactionBundle())).build();

			sendr.send(message.toString());

			// reset bundle
			bundleC.resetBundle();
		}
	}
	

}
