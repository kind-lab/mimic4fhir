package de.uzl.itcr.mimic4fhir.concur;

import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;

import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

import com.google.common.collect.Lists;

import de.uzl.itcr.mimic4fhir.queue.Sender;
import de.uzl.itcr.mimic4fhir.table.MimicFhirTable;
import de.uzl.itcr.mimic4fhir.work.BundleControl;
import de.uzl.itcr.mimic4fhir.work.Config;
import de.uzl.itcr.mimic4fhir.work.ConnectDB;
import de.uzl.itcr.mimic4fhir.work.FHIRComm;
import de.uzl.itcr.mimic4fhir.work.BundleGroup;

public class ValidationThread implements Runnable{
	
	private static final Logger logger = LoggerFactory.getLogger(ConversionThread.class);
	private Sender sendr = new Sender();
	private BundleControl bundleC = new BundleControl();
	
	Patient patient;
	List<Resource> edConditions;
	List<Resource> edEncounters;
	List<Resource> edMedicationDispenses;
	List<Resource> edMedicationStatements;
	List<Resource> edProcedures;
	List<Resource> edObservations;
	List<Resource> observationVitalSigns;
	
	List<Resource> encounters;
	List<Resource> conditions;
	List<Resource> procedures;	
			
	List<Resource> medicationAdministrations;	
	List<Resource> medicationDispenses;
	List<Resource> medicationRequests;
	
	List<Resource> icuEncounters;
	List<Resource> icuMedicationAdministrations;
	List<Resource> icuProcedures;
	List<Resource> observationChartevents;
	List<Resource> observationDatetimeevents;
	List<Resource> observationOutputevents;
	
	List<Resource> observationMicroOrgs;
	List<Resource> observationMicroSuscs;
	List<Resource> observationMicroTests;	
	List<Resource> observationLabevents;	
	List<Resource> specimenLabs;	
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
			encounters = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.ENCOUNTER, patientId);
			conditions = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.CONDITION, patientId);					
			procedures = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.PROCEDURE, patientId);	
			
			medicationAdministrations = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.MEDICATION_ADMINISTRATION, patientId);			
			medicationDispenses = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.MEDICATION_DISPENSE, patientId);
			medicationRequests = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.MEDICATION_REQUEST, patientId);
						
			icuEncounters = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.ENCOUNTER_ICU, patientId);
			icuMedicationAdministrations = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.MEDICATION_ADMINISTRATION_ICU, patientId);
			icuProcedures = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.PROCEDURE_ICU, patientId);
			observationChartevents = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_CHARTEVENTS, patientId);
			observationDatetimeevents = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_DATETIMEEVENTS, patientId);
			observationOutputevents = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_OUTPUTEVENTS, patientId);
			
			observationMicroOrgs = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_MICRO_ORG, patientId);
			observationMicroSuscs = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_MICRO_SUSC, patientId);
			observationMicroTests = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_MICRO_TEST, patientId);
			specimens = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.SPECIMEN, patientId);
			
			observationLabevents = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_LABEVENTS, patientId);
			specimenLabs = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.SPECIMEN_LAB, patientId);			
			
			// ed resources
			edEncounters = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.ENCOUNTER_ED, patientId);
			edConditions = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.CONDITION_ED, patientId);
			edProcedures = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.PROCEDURE_ED, patientId);
			edObservations = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_ED, patientId);
			edMedicationDispenses = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.MEDICATION_DISPENSE_ED, patientId);
			edMedicationStatements = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.MEDICATION_STATEMENT_ED, patientId);
			observationVitalSigns = dbAccess.getResourcesByPatientIdSynchronized(MimicFhirTable.OBSERVATION_VITAL_SIGNS, patientId);
			
			
			
			// patient related
			logger.info("Patient Bundle");
			BundleGroup bundleGroup = BundleGroup.PATIENT;
			bundleC.addUUIDResourceToBundlePut(patient);
			addResourcesToBundle(encounters, bundleGroup);
			addResourcesToBundle(icuEncounters, bundleGroup);
			addResourcesToBundle(procedures, bundleGroup);
			addResourcesToBundle(conditions, bundleGroup);
			logger.info("Patient Bundle Size: " + bundleC.getNumberOfResources());
			submitBundle(patientId);
			
			
			//medication
			logger.info("Medication Bundle");
			bundleGroup = BundleGroup.MEDICATION;
			addResourcesToBundle(medicationRequests, bundleGroup);
			addResourcesToBundle(medicationAdministrations, bundleGroup);
			addResourcesToBundle(medicationDispenses, bundleGroup);			
			addResourcesToBundle(icuMedicationAdministrations, bundleGroup);
			logger.info("Medication Bundle Size: " + bundleC.getNumberOfResources());
			submitBundle(patientId);
//			
//			// micro
			logger.info("Microbiology Bundle");
			bundleGroup = BundleGroup.MICROBIOLOGY;
			addResourcesToBundle(specimens, bundleGroup);
			addResourcesToBundle(observationMicroOrgs, bundleGroup);
			addResourcesToBundle(observationMicroSuscs, bundleGroup);
			addResourcesToBundle(observationMicroTests, bundleGroup);			
			logger.info("Micro Bundle Size: " + bundleC.getNumberOfResources());
			submitBundle(patientId);
//			
//			// labs
			bundleGroup = BundleGroup.LABS;
			addResourcesToBundle(specimenLabs, bundleGroup);
			addResourcesToBundle(observationLabevents, bundleGroup);
			
//			
//			//icu	
			logger.info("ICU Bundle");
			bundleGroup = BundleGroup.ICU;
			addResourcesToBundle(icuProcedures, bundleGroup);
			addResourcesToBundle(observationDatetimeevents, bundleGroup);
			addResourcesToBundle(observationChartevents, bundleGroup);
			addResourcesToBundle(observationOutputevents, bundleGroup);
			logger.info("ICU Bundle Size: " + bundleC.getNumberOfResources());
			
			// ed
			logger.info("ED Bundle");
			bundleGroup = BundleGroup.ED;
			addResourcesToBundle(edEncounters, bundleGroup);
			addResourcesToBundle(edConditions, bundleGroup);
			addResourcesToBundle(edProcedures, bundleGroup);			
			addResourcesToBundle(edMedicationDispenses, bundleGroup);
			addResourcesToBundle(edMedicationStatements, bundleGroup);
//			addResourcesToBundle(edObservations, bundleGroup);
//			addResourcesToBundle(observationVitalSigns, bundleGroup);
			
			
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
	private void addResourcesToBundle(List<Resource> resources, BundleGroup bundleGroup) {
		int bundleLimit = 10;
		System.out.println("Size: " + resources.size());
		// microbiology bundle must be sent together, no matter the bundle size cause of referential integrity
		if (resources.size() > bundleLimit && !bundleGroup.equals(BundleGroup.MICROBIOLOGY)) {
			List<List<Resource>> resourceLists = Lists.partition(resources, bundleLimit);
			resourceLists.forEach(resourceList -> {
				resourceList.forEach(resource -> {
					bundleC.addUUIDResourceToBundlePut(resource);
				});
				checkBundleLimit();
			});				
		} else {
			resources.forEach(resource -> {
				bundleC.addUUIDResourceToBundlePut(resource);
			});
		}
	
		
	}
	
	private void checkBundleLimit() {

		// if bundle exceeds 50 resources -> start new bundle
		if (bundleC.getNumberOfResources() >= 10) {
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
