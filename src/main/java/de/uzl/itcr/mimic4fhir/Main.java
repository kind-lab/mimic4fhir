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

import de.uzl.itcr.mimic4fhir.model.manager.ModelVersion;
import de.uzl.itcr.mimic4fhir.work.Config;

public class Main {

	public static void main(String[] args) {
		// Add server and config data..
		Config configObj = new Config();

		// Postgres
		configObj.setPassPostgres("postgres");
		configObj.setPortPostgres("5432");
		configObj.setUserPostgres("postgres");
		configObj.setPostgresServer("localhost");
		configObj.setDbnamePostgres("mimic");
		configObj.setSchemaPostgres("mimic_fhir");

		// Fhir
		configObj.setFhirServer("http://localhost:8080/fhir");
		configObj.setFhirxmlFilePath("/home/alex/MIMIC_Output");

		// Validation
		configObj.setValidateResources(true);

		// ConceptMaps
//		configObj.setICD9toICD10GM("https://server.com/fhir/ConceptMap/d9be1278-282b-4e80-8be5-226cb30a9eb5");
//		configObj.setICD9ToSnomed("https://server.com/fhir/ConceptMap/9f0b2a1f-8253-47fc-a8cf-118226823e22");
//		configObj.setICD9ProcToSnomed("https://server.com/fhir/ConceptMap/01c83771-6524-46ef-aaa8-4f63e1d837ea");
//		configObj.setICD10PCStoSnomed("https://server.com/fhir/ConceptMap/03ea8e3a-7fc3-4fb3-8e30-21af497c2a63");
		
		// Use CXR 
		configObj.setUseCXR(false);
		
		//Specification
//		configObj.setSpecification(ModelVersion.R4);

		Mimic4Fhir app = new Mimic4Fhir();
		app.setConfig(configObj);
		app.setOutputMode(OutputMode.PUSH_SERVER);
		app.setInputMode(InputMode.MIMIC_FHIR);
		app.setTopPatients(10, false);
//		app.start();
		app.startWithThread();
	}
}
