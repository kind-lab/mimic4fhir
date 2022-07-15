/*******************************************************************************
 * Copyright (C) 2021 S. Ververs, P. Behrend, J. Wiedekopf, H.Ulrich - University of Lübeck
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.uzl.itcr.mimic4fhir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonObject;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.util.StopWatch;

import ca.uhn.fhir.model.primitive.IdDt;
import de.uzl.itcr.mimic4fhir.concur.ConversionThread;
import de.uzl.itcr.mimic4fhir.concur.ValidationThread;
import de.uzl.itcr.mimic4fhir.concur.TimeMeasurements;
import de.uzl.itcr.mimic4fhir.model.*;
import de.uzl.itcr.mimic4fhir.model.manager.*;
import de.uzl.itcr.mimic4fhir.queue.Receiver;
import de.uzl.itcr.mimic4fhir.queue.Sender;
import de.uzl.itcr.mimic4fhir.tools.FhirInstanceValidatorMimic;
import de.uzl.itcr.mimic4fhir.tools.FHIRInstanceValidator;
import de.uzl.itcr.mimic4fhir.work.BundleControl;
import de.uzl.itcr.mimic4fhir.work.Config;
import de.uzl.itcr.mimic4fhir.work.ConnectDB;
import de.uzl.itcr.mimic4fhir.work.FHIRComm;
import de.uzl.itcr.mimic4fhir.table.MimicFhirTable;

/**
 * Application for transforming data from MIMIC IV to FHIR R4
 * 
 */
public class Mimic4Fhir {
	// Config-Object
	private Config config;

	private OutputMode outputMode;
	private InputMode inputMode;
	private int topPatients;
	private boolean random;

	private ConnectDB dbAccess;
	private FHIRComm fhir;
	private HashMap<Integer, MCaregiver> caregivers;
	private HashMap<String, String> locationsInBundle;
	private HashMap<String, String> caregiversInBundle;
	private HashMap<String, String> medicationInBundle;

	private Organization hospital;
	private BundleControl bundleC;

	private Sender sendr;
	private Receiver receiver;

	private static FhirInstanceValidatorMimic instanceValidator = FhirInstanceValidatorMimic.getInstance();
	private static FHIRInstanceValidator instanceValidator2 = FHIRInstanceValidator.getInstance();

	public Config getConfig() {
		return config;
	}

	/**
	 * Set Configuration Object for App
	 * @param config Config-Object
	 */
	public void setConfig(Config config) {
		this.config = config;
	}

	/**
	 * Set Application output mode
	 * 
	 * @param modus (Write to file, load to server..)
	 */
	public void setOutputMode(OutputMode modus) {
		this.outputMode = modus;
	}
	
	/**
	 * Set Application input mode
	 * 
	 * @param modus (Read from MIMIC-IV base tables or MIMIC-IV fhir tables)
	 */
	public void setInputMode(InputMode modus) {
		this.inputMode = modus;
	}

	/**
	 * Set Number of Patients to load (first x patients in db); 0 if all Works only
	 * with "all patients" import mode
	 * 
	 * @param topPatients number of patients to load
	 */
	public void setTopPatients(int topPatients, boolean random) {
		this.topPatients = topPatients;
		this.random = random;
	}

	public void startWithThread() {

		dbAccess = new ConnectDB(config, inputMode);
		fhir = new FHIRComm(config);
		
		int numberOfAllPatients = 0;
		if (topPatients == 0) { // all Patients
			numberOfAllPatients = dbAccess.getNumberOfPatients();
		} else {
			numberOfAllPatients = topPatients;
		}
		
		int numReceivers = 5;		
		if (numberOfAllPatients > numReceivers) {
			numReceivers = numberOfAllPatients;
		}		
		for (int i=0; i<numReceivers; i++) {
			receiver = new Receiver();
			receiver.setFhirConnector(fhir);
			receiver.setOutputMode(outputMode);
			receiver.receive();
			
		}
	
		String[] patientIDs = dbAccess.getAmountOfPatientIds(topPatients, random);

		StopWatch watch = new StopWatch();
		watch.start();
		
		ExecutorService executor = Executors.newFixedThreadPool(10);

		if (inputMode == InputMode.MIMIC_BASE) {
			StationManager stations = dbAccess.getStations();
			for (int i = 0; i < topPatients; i++) {
				executor.submit(
						new ConversionThread(fhir, patientIDs[i], i, stations, config, config.getValidateResources(), dbAccess));
			}
		} else if (inputMode == InputMode.MIMIC_FHIR) {
			dbAccess.setFhirConnector(fhir);
			for (int i = 0; i < topPatients; i++) {
				executor.submit(
						new ValidationThread(fhir, patientIDs[i], config, dbAccess));
			}
		}
		
		

		executor.shutdown();
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			sendr = new Sender();
			JsonObject message = Json.createObjectBuilder().add("number", "0").add("bundle", "END").build();
			for (int i=0; i<numReceivers; i++) {
				sendr.send(message.toString()); 
			}

			// close connection to queue
			sendr.close();
			
			
			watch.stop();
			System.out.print(
					"Conversion of " + topPatients + " Patients complete in " + watch.getTotalTimeMillis() + " ms)");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		TimeMeasurements.getInstance().writeToFile();
	}


	
	/*
	 * Start transformation for mimic-fhir SQL resources
	 */
	public void start() {
		// Connection to mimic postgres DB
		dbAccess = new ConnectDB(config, inputMode);	
    	
		// Fhir-Communication and Resource-Bundle-Stuff
		fhir = new FHIRComm(config);
		bundleC = new BundleControl();
		dbAccess.setFhirConnector(fhir);

		int numberOfAllPatients = 0;
		if (topPatients == 0) { // all Patients
			numberOfAllPatients = dbAccess.getNumberOfPatients();
		} else {
			numberOfAllPatients = topPatients;
		}

		// Sender for sending bundle messages to queue
		sendr = new Sender();

		// Start Message-Receiver (handles bundle operations)
		Receiver r = new Receiver();
		r.setFhirConnector(fhir);
		r.setOutputMode(outputMode);
		r.receive();
		
		// Preload data (if requested...)
		// medication, organization, location
		// data elements
//		preLoadDataResources();
		
		

		System.out.println("Getting all Patients");
		String[] patientIds = dbAccess.getAmountOfPatientIds(numberOfAllPatients, random);	
		
		StopWatch watch = new StopWatch();
		watch.start();		


		// loop all patients..
		for (int i = 0; i < numberOfAllPatients; i++) {
			System.out.println("Processing Patient:" + (i + 1));
			processPatient(patientIds[i]);
		}

		// Push end-Message to queue
		JsonObject message = Json.createObjectBuilder().add("number", "0").add("bundle", "END").build();
		sendr.send(message.toString());

		// close connection to queue
		sendr.close();

		watch.stop();
		System.out.print(
				"Conversion of " + topPatients + " Patients complete in " + watch.getTotalTimeMillis() + " ms)");
	}
	
	public void preLoadDataResources() {
		List<Resource> locations = dbAccess.getResources(MimicFhirTable.LOCATION);
		List<Resource> medications = dbAccess.getResources(MimicFhirTable.MEDICATION);
		List<Resource> organizations = dbAccess.getResources(MimicFhirTable.ORGANIZATION);
		
		addResourcesToBundle(locations);
		addResourcesToBundle(organizations);
		addResourcesToBundle(medications);
			
		//Push bundle to queue
		JsonObject message = Json.createObjectBuilder()
				.add("number", "data_bundle" + bundleC.getInternalBundleNumber()) 
				.add("bundle", fhir.getBundleAsString(bundleC.getTransactionBundle()))
				.build();
	
		sendr.send(message.toString()); 		
	}
	
	
	
	public void testOneResource(Resource resource) {		
		bundleC.addUUIDResourceToBundlePut(resource);

		//Push bundle to queue
		JsonObject message = Json.createObjectBuilder()
				.add("number", resource.getId().split("/")[1] + "_" + bundleC.getInternalBundleNumber()) 
				.add("bundle", fhir.getBundleAsString(bundleC.getTransactionBundle()))
				.build();
	
		sendr.send(message.toString()); 
	}

	
	/**
	 * Start transformation
	 */
	public void startOld() {
		// Connection to mimic postgres DB
		dbAccess = new ConnectDB(config, inputMode);
		// initialize memoryLists of locations and caregivers and medication (->
		// conditional creates, each resource only once in bundle)
		locationsInBundle = new HashMap<String, String>();
		caregiversInBundle = new HashMap<String, String>();
		medicationInBundle = new HashMap<String, String>();

		// Preload Hospital
		hospital = createTopHospital();

		// Fhir-Communication and Resource-Bundle-Stuff
		fhir = new FHIRComm(config);
		bundleC = new BundleControl();

		int numberOfAllPatients = 0;
		if (topPatients == 0) { // all Patients
			numberOfAllPatients = dbAccess.getNumberOfPatients();
		} else {
			numberOfAllPatients = topPatients;
		}

		// Sender for sending bundle messages to queue
		sendr = new Sender();

		// Start Message-Receiver (handles bundle operations)
		Receiver r = new Receiver();
		r.setFhirConnector(fhir);
		r.setOutputMode(outputMode);
		r.receive();

		// Deprecated since the functionality of this block of code can be reproduced
		// using the LIMIT key word
		/*
		 * for(int i = 1; i<= numberOfAllPatients; i++) { MPatient mimicPat2 =
		 * dbAccess.getPatientByRowId(i); processPatient(mimicPat2, i); }
		 */

		System.out.println("Getting all Patients");
		//MPatient[] patients = dbAccess.getAmountOfPatients(numberOfAllPatients, random);
		String[] patientIds = dbAccess.getAmountOfPatientIds(numberOfAllPatients, random);
		System.out.println("Getting all Stations");
		StationManager stations = dbAccess.getStations();

		StopWatch watch = new StopWatch();
		watch.start();

		MPatient mPatient;
		// loop all patients..
		for (int i = 0; i < numberOfAllPatients; i++) {
			System.out.println("Processing Patient:" + (i + 1));
			mPatient = dbAccess.getPatientBySubjectId(patientIds[i]);
			processPatient(mPatient, i, stations);
		}

		// Push end-Message to queue
		JsonObject message = Json.createObjectBuilder().add("number", "0").add("bundle", "END").build();

		sendr.send(message.toString());

		// close connection to queue
		sendr.close();

		watch.stop();
		System.out.print(
				"Conversion of " + topPatients + " Patients complete in " + watch.getTotalTimeMillis() + " ms)");
	}

	private void resetMemoryLists() {
		caregiversInBundle.clear();
		locationsInBundle.clear();
		medicationInBundle.clear();
	}
	
	
	/*
	 *  MIMIC_FHIR process patient
	 */
			
	private void processPatient(String patientId) {
		Patient patient = dbAccess.getPatient(patientId);
		List<Resource> encounters = dbAccess.getResourcesByPatientId(MimicFhirTable.ENCOUNTER, patientId);
		List<Resource> conditions = dbAccess.getResourcesByPatientId(MimicFhirTable.CONDITION, patientId);
		List<Resource> encounterIcus = dbAccess.getResourcesByPatientId(MimicFhirTable.ENCOUNTER_ICU, patientId);		
		List<Resource> medicationAdministrations = dbAccess.getResourcesByPatientId(MimicFhirTable.MEDICATION_ADMINISTRATION, patientId);
		List<Resource> medicationAdministrationIcus = dbAccess.getResourcesByPatientId(MimicFhirTable.MEDICATION_ADMINISTRATION_ICU, patientId);
		List<Resource> medicationDispenses = dbAccess.getResourcesByPatientId(MimicFhirTable.MEDICATION_DISPENSE, patientId);
		List<Resource> medicationRequests = dbAccess.getResourcesByPatientId(MimicFhirTable.MEDICATION_REQUEST, patientId);
		List<Resource> observationChartevents = dbAccess.getResourcesByPatientId(MimicFhirTable.OBSERVATION_CHARTEVENTS, patientId);
		List<Resource> observationDatetimeevents = dbAccess.getResourcesByPatientId(MimicFhirTable.OBSERVATION_DATETIMEEVENTS, patientId);
		
		List<Resource> observationMicroOrgs = dbAccess.getResourcesByPatientId(MimicFhirTable.OBSERVATION_MICRO_ORG, patientId);
		List<Resource> observationMicroSuscs = dbAccess.getResourcesByPatientId(MimicFhirTable.OBSERVATION_MICRO_SUSC, patientId);
		List<Resource> observationMicroTests = dbAccess.getResourcesByPatientId(MimicFhirTable.OBSERVATION_MICRO_TEST, patientId);
		List<Resource> observationOutputevents = dbAccess.getResourcesByPatientId(MimicFhirTable.OBSERVATION_OUTPUTEVENTS, patientId);
		List<Resource> procedures = dbAccess.getResourcesByPatientId(MimicFhirTable.PROCEDURE, patientId);
		List<Resource> procedure_icus = dbAccess.getResourcesByPatientId(MimicFhirTable.PROCEDURE_ICU, patientId);
		List<Resource> specimens = dbAccess.getResourcesByPatientId(MimicFhirTable.SPECIMEN, patientId);
		
		
		// Labs -- need to regenerate observation_labevents table... postgres struggling
		//List<Resource> observationLabevents = dbAccess.getResourcesByPatientId(MimicFhirTable.OBSERVATION_LABEVENTS, patientId);
		//List<Resource> specimenLabs = dbAccess.getResourcesByPatientId(MimicFhirTable.SPECIMEN_LAB, patientId);
		
		// patient related
		System.out.println("Patient Bundle");
		bundleC.addUUIDResourceToBundlePut(patient);
		addResourcesToBundle(encounters);
		addResourcesToBundle(encounterIcus);
		addResourcesToBundle(procedures);
		addResourcesToBundle(conditions);
		System.out.println("Patient Bundle Size: " + bundleC.getNumberOfResources());
		submitBundle(patientId);
		
		
		//medication
		System.out.println("Medication Bundle");
		addResourcesToBundle(medicationRequests);
		addResourcesToBundle(medicationAdministrations);
		addResourcesToBundle(medicationDispenses);
		
		addResourcesToBundle(medicationAdministrationIcus);
		System.out.println("Medication Bundle Size: " + bundleC.getNumberOfResources());
		submitBundle(patientId);
		
		// micro
		System.out.println("Microbiology Bundle");
		addResourcesToBundle(observationMicroOrgs);
		addResourcesToBundle(observationMicroSuscs);
		addResourcesToBundle(observationMicroTests);
		addResourcesToBundle(specimens);
		System.out.println("Micro Bundle Size: " + bundleC.getNumberOfResources());
		submitBundle(patientId);
		
		// labs
//		addResourcesToBundle(observationLabevents);
//		addResourcesToBundle(specimenLabs);
		
		//icu	
		System.out.println("ICU Bundle");
		addResourcesToBundle(procedure_icus);
		addResourcesToBundle(observationDatetimeevents);
		addResourcesToBundle(observationChartevents);
		addResourcesToBundle(observationOutputevents);
		System.out.println("ICU Bundle Size: " + bundleC.getNumberOfResources());
		//bundleC.resetBundle();
		submitBundle(patientId);		
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

	/*
	 *  MIMIC_BASE process patient
	 */
	private void processPatient(MPatient mimicPat, int numPat, StationManager stations) {
		//Create resource managers
		PatientManager paManager = new PatientManager();
		DiagnoseManager dManager = new DiagnoseManager();
		CharteventManager chManager = new CharteventManager();
		LabeventManager laManager = new LabeventManager();
		ProcedureManager proManager = new ProcedureManager();
		PrescriptionManager preManager = new PrescriptionManager();
		AdmissionManager adManager = new AdmissionManager();
		ImagingManager imManager = new ImagingManager();

		// Fill FHIR-Structure
		Patient fhirPat = paManager.createResource(mimicPat, this.config);
		String patNumber;
		int admissionIndex = 0;

		// Add DiagnosticReports for each Patient if available
		if(config.useCXR()) {
			if (mimicPat.getDiagnosticReports().size() > 0) {
				for (MDiagnosticReport mDiagnosticReport : mimicPat.getDiagnosticReports()) {
					bundleC.addResourceToBundle(imManager.createDiagnosticReport(mDiagnosticReport, this.config));
				}
				JsonObject message = Json.createObjectBuilder()
						.add("number", numPat + "_" + bundleC.getInternalBundleNumber())
						.add("bundle", fhir.getBundleAsString(bundleC.getTransactionBundle())).build();
				
				sendr.send(message.toString());
				bundleC.resetBundle();
			}
		}

		// All admissions of one patient
		for (MAdmission admission : mimicPat.getAdmissions()) {

			// First: Load/create fhir resources
			Encounter enc = adManager.createAdmission(admission, stations, this.config);

			// create Conditions per Admission
			List<Condition> conditions = new ArrayList<>();
			for(MDiagnose mDiagnose : admission.getDiagnoses()){
				conditions.add(dManager.createResource(mDiagnose, this.config));
			}

			// create Procedures per Admission
			List<Procedure> procedures = new ArrayList<>();
			for(MProcedure mProcedure : admission.getProcedures()){
				procedures.add(proManager.createResource(mProcedure, this.config));
			}

			// create List Of Medication & MedicationAdministrations
			List<Medication> medications = new ArrayList<>();
			List<MedicationAdministration> prescriptions = new ArrayList<>();
			int count = 0;
			for(MPrescription mPrescription : admission.getPrescriptions()){
				medications.add(preManager.createResource(mPrescription, this.config));

				prescriptions.add(preManager.createAdministration(mPrescription, count++, this.config));
			}

			// create Observations per Admission
			List<Observation> obs = new ArrayList<>();
			for(MChartevent mChartevent : admission.getEvents()){
				obs.add(chManager.createResource(mChartevent, this.config));
			}

			// create Observation from Labevents
			List<Observation> obsLab = new ArrayList<>();
			for(MLabevent mLabevent : admission.getLabEvents()){
				obsLab.add(laManager.createResource(mLabevent, this.config));
			}

			//No note events in mimiciv currently
			// create Observation from Noteevents
			/*
			List<Observation> obsNotes = admission.createFhirNoteObservationsFromMimic(fhirPat.getId(), enc.getId());
			*/

			// create bundle without observations and medication:
			createBasicBundle(fhirPat, admission, enc, conditions, procedures, stations);

			// Medication only in first bundle of admission
			// Prescriptions
			for (Medication med : medications) {
				String identifier = med.getCode().getCodingFirstRep().getCode();
				if (!medicationInBundle.containsKey(identifier)) {
					bundleC.addUUIDResourceWithConditionToBundle(med,
							"code=" + med.getCode().getCodingFirstRep().getCode());
					medicationInBundle.put(identifier, med.getId());

					if (this.config.getValidateResources()) {
						instanceValidator.validateAndPrint(med);
					}
				}
			}

			// ..and MedicationAdministrations (with correct Medication as Reference)
			for (MedicationAdministration madm : prescriptions) {
				String identifier = medications.get(prescriptions.indexOf(madm)).getCode().getCodingFirstRep()
						.getCode();
				String medId = medicationInBundle.get(identifier);
				madm.setMedication(new Reference(medId));

				bundleC.addUUIDResourceToBundle(madm);

				if (this.config.getValidateResources()) {
					instanceValidator.validateAndPrint(madm);
				}
			}

			// Identification
			admissionIndex++;
			patNumber = numPat + "_" + admissionIndex;

			// add observations to bundle
			for (Observation o : obs) {
				// check if bundle is full
				checkBundleLimit(patNumber, fhirPat, admission, enc, conditions, procedures, stations);

				// Order important - these reference pat & encounter
				bundleC.addResourceToBundle(o);
				if (this.config.getValidateResources()) {
					instanceValidator.validateAndPrint(o);
				}
			}

			for (Observation o : obsLab) {
				// check if bundle is full
				checkBundleLimit(patNumber, fhirPat, admission, enc, conditions, procedures, stations);

				bundleC.addResourceToBundle(o);

				if (this.config.getValidateResources()) {
					instanceValidator.validateAndPrint(o);
				}
			}

			//Currently no notevents in mimiciv
			/*
			for (Observation o : obsNotes) {
				// check if bundle is full
				checkBundleLimit(patNumber, fhirPat, admission, enc, conditions, procedures, stations);

				// get Caregiver for this event
				int caregiverId = admission.getNoteevents().get(obsNotes.indexOf(o)).getCaregiverId();
				if (caregiverId != 0) {
					String pFhirId = processCaregiver(caregiverId);

					// Set caregiver-Reference -> Performer
					o.addPerformer(new Reference(pFhirId));
				}

				bundleC.addResourceToBundle(o);

				if (this.config.getValidateResources()) {
					instanceValidator.validateAndPrint(o);
				}
			}
			 */

			// Push bundle to queue
			JsonObject message = Json.createObjectBuilder()
					.add("number", patNumber + "_" + bundleC.getInternalBundleNumber())
					.add("bundle", fhir.getBundleAsString(bundleC.getTransactionBundle())).build();

			sendr.send(message.toString());

			// reset bundle and memory lists
			bundleC.resetBundle();
			resetMemoryLists();

		}
		bundleC.resetInternalBundleNumber();
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
	

	private void checkBundleLimit(String numPat, Patient fhirPat, MAdmission admission, Encounter enc,
			List<Condition> conditions, List<Procedure> procedures, StationManager stations) {

		// if bundle exceeds 15000 resources -> start new bundle
		if (bundleC.getNumberOfResources() > 15000) {
			// Push bundle to queue
			JsonObject message = Json.createObjectBuilder()
					.add("number", numPat + "_" + bundleC.getInternalBundleNumber())
					.add("bundle", fhir.getBundleAsString(bundleC.getTransactionBundle())).build();

			sendr.send(message.toString());

			// reset bundle and memory lists
			bundleC.resetBundle();
			resetMemoryLists();
			// reload basic bundle stuff
			createBasicBundle(fhirPat, admission, enc, conditions, procedures, stations);
		}
	}

	private void createBasicBundle(Patient fhirPat, MAdmission admission, Encounter enc, List<Condition> conditions,
			List<Procedure> procedures, StationManager stations) {

		// Pat to bundle
		bundleC.addUUIDResourceWithConditionToBundle(fhirPat, "identifier="
				+ fhirPat.getIdentifierFirstRep().getSystem() + "|" + fhirPat.getIdentifierFirstRep().getValue());

		// Top of all: Hospital
		bundleC.addUUIDResourceWithConditionToBundle(hospital, "identifier="
				+ hospital.getIdentifierFirstRep().getSystem() + "|" + hospital.getIdentifierFirstRep().getValue());

		enc.getDiagnosis().clear(); // clear all procedures & diagnoses

		if (this.config.getValidateResources()) {
			instanceValidator.validateAndPrint(fhirPat);
			instanceValidator.validateAndPrint(hospital);
		}

		// Diagnoses
		for (Condition c : conditions) {
			int rank = admission.getDiagnoses().get(conditions.indexOf(c)).getSeqNumber();

			// set Condition in enc.diagnosis
			enc.addDiagnosis().setCondition(new Reference(c.getId())).setRank(rank);

			// add Condition to bundle
			bundleC.addUUIDResourceWithConditionToBundle(c,
					"identifier=" + c.getIdentifierFirstRep().getSystem() + "|" + c.getIdentifierFirstRep().getValue());

			if (this.config.getValidateResources()) {
				instanceValidator.validateAndPrint(c);
			}
		}

		// Procedures
		for (Procedure p : procedures) {
			int rank = admission.getProcedures().get(procedures.indexOf(p)).getSeqNumber();

			// set Procedure in enc.diagnosis
			enc.addDiagnosis().setCondition(new Reference(p.getId())).setRank(rank);

			// add Procedure to bundle
			bundleC.addUUIDResourceWithConditionToBundle(p,
					"identifier=" + p.getIdentifierFirstRep().getSystem() + "|" + p.getIdentifierFirstRep().getValue());

			if (this.config.getValidateResources()) {
				instanceValidator.validateAndPrint(p);
			}
		}

		// create transfer chain

		enc.getLocation().clear(); // clear all locations -> to be newly added

		if(this.config.getSpecification() == ModelVersion.KDS){
			for (MTransfer t : admission.getTransfers()) {
				Location locWard = stations.getLocation(stations.getStation(t.getCareUnit()));

				// Create transfer encounter
				Encounter tEnc = new Encounter();
				tEnc.setId(IdDt.newRandomUuid());
				tEnc.setStatus(Encounter.EncounterStatus.FINISHED);
				tEnc.setClass_(new Coding().setSystem(
						"https://www.medizininformatik-initiative.de/fhir/core/CodeSystem/EncounterClassAdditionsDE")
						.setCode("_ActEncounterCode").setDisplay("ActEncounterCode"));
				tEnc.setSubject(enc.getSubject());
				tEnc.setPeriod(new Period().setStart(t.getIntime()).setEnd(t.getOuttime()));
				tEnc.addLocation().setLocation(new Reference(locWard.getId()))
						.setStatus(Encounter.EncounterLocationStatus.COMPLETED).setPhysicalType(locWard.getPhysicalType())
						.setPeriod(new Period().setStart(t.getIntime()).setEnd(t.getOuttime()));
				tEnc.setServiceProvider(new Reference(stations.getOrganization(stations.getStation(t.getCareUnit())).getId()));
				tEnc.setPartOf(new Reference(enc.getId()));

				String wIdentifier = locWard.getIdentifierFirstRep().getValue();
				String eIdentifier = tEnc.getIdentifierFirstRep().getValue();
				if (!locationsInBundle.containsKey(wIdentifier)) {
					// add to memory list:
					locationsInBundle.put(wIdentifier, locWard.getId());

					bundleC.addUUIDResourceWithConditionToBundle(locWard,
							"identifier=" + locWard.getIdentifierFirstRep().getSystem() + "|" + wIdentifier);
					bundleC.addUUIDResourceWithConditionToBundle(tEnc,
							"identifier=" + tEnc.getIdentifierFirstRep().getSystem() + "|" + eIdentifier);
				}

				if (this.config.getValidateResources()) {
					instanceValidator.validateAndPrint(locWard);
					instanceValidator.validateAndPrint(tEnc);
				}
			}
		}

		// add Encounter to bundle
		bundleC.addUUIDResourceWithConditionToBundle(enc,
				"identifier=" + enc.getIdentifierFirstRep().getSystem() + "|" + enc.getIdentifierFirstRep().getValue());

		if (this.config.getValidateResources()) {
			instanceValidator.validateAndPrint(enc);
		}
	}

	private String processCaregiver(int caregiverId) {
		MCaregiver cgHere = caregivers.get(caregiverId);
		// Create FHIR-Resources for Practitioner und -Role
		Practitioner pFhir = cgHere.getFhirRepresentation();
		String identifier = pFhir.getIdentifierFirstRep().getValue();
		String id;
		if (!caregiversInBundle.containsKey(identifier)) {
			// add to memory list
			caregiversInBundle.put(identifier, pFhir.getId());
			id = pFhir.getId();
			bundleC.addUUIDResourceWithConditionToBundle(pFhir,
					"identifier=" + pFhir.getIdentifierFirstRep().getSystem() + "|" + identifier);

			if (this.config.getValidateResources()) {
				instanceValidator.validateAndPrint(pFhir);
			}

			PractitionerRole roleFhir = cgHere.getFhirRepresentationRole();
			if (roleFhir != null) {
				roleFhir.setPractitioner(new Reference(pFhir.getId()));
				roleFhir.setOrganization(new Reference(hospital.getId()));
				bundleC.addUUIDResourceWithConditionToBundle(roleFhir,
						"identifier=" + roleFhir.getIdentifierFirstRep().getSystem() + "|"
								+ roleFhir.getIdentifierFirstRep().getValue());

				if (this.config.getValidateResources()) {
					instanceValidator.validateAndPrint(roleFhir);
				}
			}
		} else {
			id = caregiversInBundle.get(identifier);
		}
		return id;
	}

	private Organization createTopHospital() {
		// Create a "dummy" Organization that is "top player" of PractitionerRoles and
		// Locations
		Organization hospital = new Organization();

		hospital.addIdentifier().setSystem("http://www.imi-mimic.de").setValue("hospital");

		hospital.addType().addCoding().setCode("prov").setSystem("http://hl7.org/fhir/organization-type")
				.setDisplay("Healthcare Provider");
		hospital.setName("IMI-Mimic Hospital");

		hospital.setId(IdDt.newRandomUuid());
		return hospital;
	}
}
