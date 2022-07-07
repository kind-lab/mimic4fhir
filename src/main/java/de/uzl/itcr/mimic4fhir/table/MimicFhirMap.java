package de.uzl.itcr.mimic4fhir.table;

import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationDispense;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.Resource;

import java.util.HashMap;

public class MimicFhirMap {
	HashMap<MimicFhirTable, Class> map;
	
	
	public MimicFhirMap () {
		this.map = new HashMap<MimicFhirTable, Class>();
		this.createMap();
	}
	
	public void createMap() {
		map.put(MimicFhirTable.CONDITION, Condition.class);
		map.put(MimicFhirTable.ENCOUNTER, Encounter.class);
		map.put(MimicFhirTable.ENCOUNTER_ICU, Encounter.class);		
		map.put(MimicFhirTable.LOCATION, Location.class);
		map.put(MimicFhirTable.MEDICATION, Medication.class);
		map.put(MimicFhirTable.MEDICATION_ADMINISTRATION, MedicationAdministration.class);
		map.put(MimicFhirTable.MEDICATION_ADMINISTRATION_ICU, MedicationAdministration.class);
		map.put(MimicFhirTable.MEDICATION_DISPENSE, MedicationDispense.class);
		map.put(MimicFhirTable.MEDICATION_REQUEST, MedicationRequest.class);
		map.put(MimicFhirTable.OBSERVATION_CHARTEVENTS, Observation.class);
		map.put(MimicFhirTable.OBSERVATION_DATETIMEEVENTS, Observation.class);
		map.put(MimicFhirTable.OBSERVATION_LABEVENTS, Observation.class);
		map.put(MimicFhirTable.OBSERVATION_MICRO_ORG, Observation.class);
		map.put(MimicFhirTable.OBSERVATION_MICRO_SUSC, Observation.class);
		map.put(MimicFhirTable.OBSERVATION_MICRO_TEST, Observation.class);
		map.put(MimicFhirTable.OBSERVATION_OUTPUTEVENTS, Observation.class);
		map.put(MimicFhirTable.OBSERVATION_MICRO_TEST, Observation.class);
		map.put(MimicFhirTable.ORGANIZATION, Organization.class);
		map.put(MimicFhirTable.PATIENT, Patient.class);
		map.put(MimicFhirTable.PROCEDURE, Procedure.class);
		map.put(MimicFhirTable.PROCEDURE_ICU, Procedure.class);
		map.put(MimicFhirTable.SPECIMEN, Specimen.class);
		map.put(MimicFhirTable.SPECIMEN_LAB, Specimen.class);
	}
	
	@SuppressWarnings("unchecked")
	public Class<? extends Resource> getResourceClass(MimicFhirTable resource) {
		return this.map.get(resource);
	}
}
