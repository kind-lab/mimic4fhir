package de.uzl.itcr.mimic4fhir;

/**
 * Input-Mode: Where to get the data from
 * -MIMIC_BASE: From the MIMIC-IV base tables
 * -MIMIC_FHIR: From the MIMIC-IV FHIR tables
 */
public enum InputMode {
	MIMIC_BASE,
	MIMIC_FHIR
}
