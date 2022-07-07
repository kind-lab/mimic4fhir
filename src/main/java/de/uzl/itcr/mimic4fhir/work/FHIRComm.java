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
package de.uzl.itcr.mimic4fhir.work;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import ca.uhn.fhir.rest.client.apache.GZipContentInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.parser.IParser;

/**
 * Communication and Functions for and with FHIR
 * 
 * @author Stefanie Ververs
 *
 */
public class FHIRComm {

	private static final Logger logger = LoggerFactory.getLogger(FHIRComm.class);

	private FhirContext ctx;
	private IGenericClient client;

	private Config configuration;

	/**
	 * Create new Fhir-Context with config-Object
	 * 
	 * @param config config-Object
	 */
	public FHIRComm(Config config) {
		this.configuration = config;
		ctx = FhirContext.forR4();

		// Use the narrative generator
		ctx.setNarrativeGenerator(new DefaultThymeleafNarrativeGenerator());
		client = ctx.newRestfulGenericClient(configuration.getFhirServer());

		if (this.configuration.isAuthRequired()) {
			// Authorization
			BearerTokenAuthInterceptor authInterceptor = new BearerTokenAuthInterceptor(this.configuration.getToken());
			client.registerInterceptor(authInterceptor);
		}

		// Set how long to block for individual read/write operations (in ms)
		ctx.getRestfulClientFactory().setSocketTimeout(1500 * 1000);

		// Gzip output content
		client.registerInterceptor(new GZipContentInterceptor());
	}

	/**
	 * logger.error(e.getMessage());
	 * Print bundle as xml to console
	 * 
	 * @param transactionBundle bundle to print
	 */
	public void printBundleAsXml(Bundle transactionBundle) {
		System.out.println(getBundleAsString(transactionBundle));

	}

	/**
	 * Save FHIR-Ressource-Bundle as xml to location specified in Config
	 * 
	 * @param number            Number of bundle. Use 0, if no number in file name
	 *                          wanted ("bundle.xml")
	 * @param transactionBundle bundle to print to file
	 */
	public void printBundleAsXmlToFile(String number, Bundle transactionBundle) {
		System.out.println("IN XML PRINT");
		try {
			String xml = getBundleAsString(transactionBundle);

			String fullFilePath;
			if (!number.equals("0")) {
				fullFilePath = configuration.getFhirxmlFilePath() + "bundle" + number + ".xml";
			} else {
				fullFilePath = configuration.getFhirxmlFilePath() + "bundle.xml";
			}

			Path path = Paths.get(fullFilePath);
			byte[] strToBytes = xml.getBytes();

			/*
			 * File xmlFile = new File(fullFilePath);
			 * if(xmlFile.exists()) xmlFile.createNewFile();
			 */

			// Write xml as file
			Files.write(path, strToBytes);
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Send complete bundle to fhir-server
	 * 
	 * @param transactionBundle bundle to push to server
	 */
	public void bundleToServer(Bundle transactionBundle) {
		Boolean validResources = true;
		System.out.println("Before pushing bundle");
		Bundle resp = client
				.transaction()
				.withBundle(transactionBundle)
				.withAdditionalHeader("Prefer", "return=representation")
				.execute();
		System.out.println("After pushing bundle");
		System.out.println(resp.fhirType());

		if (resp.fhirType() == "OperationOutcome") {
			validResources = false;
		}
		// Log response
		writeToFile(ctx.newJsonParser().encodeResourceToString(resp), "json", validResources);
		System.out.println("After writing bundle");
	}

	private void writeToFile(String text) {
		try {

			String fullFilePath = configuration.getFhirxmlFilePath() + "\\log" + new Date().getTime() + ".xml";

			Path path = Paths.get(fullFilePath);
			byte[] strToBytes = text.getBytes();

			// Write xml as file
			Files.write(path, strToBytes);
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}
	}

	private void writeToFile(String text, String format, Boolean validResources) {
		String fullFilePath;
		try {
			if (validResources) {
				fullFilePath = configuration.getFhirxmlFilePath() + "/valid/" + new Date().getTime() + "." + format;
			} else {
				fullFilePath = configuration.getFhirxmlFilePath() + "/invalid/" + new Date().getTime() + "." + format;
			}

			Path path = Paths.get(fullFilePath);
			byte[] strToBytes = text.getBytes();

			// Write formatted file
			Files.write(path, strToBytes);
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Get a bundle as xml string representation
	 * 
	 * @param bundle bundle to transform into a string
	 * @return bundle xml string
	 */
	public String getBundleAsString(Bundle bundle) {
		return ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle);
	}

	/**
	 * Get a bundle as json string representation
	 * 
	 * @param bundle bundle to transform into a string
	 * @return bundle json string
	 */
	public String getBundleAsJsonString(Bundle bundle) {
		return ctx.newJsonParser().encodeResourceToString(bundle);
	}

	/**
	 * Parse xml string to bundle
	 * 
	 * @param bundle bundle as xml string
	 * @return bundle as Bundle
	 */
	public Bundle getBundleFromString(String bundle) {
		return (Bundle) ctx.newXmlParser().setPrettyPrint(true).parseResource(bundle);
	}

	/**
	 * Save FHIR-Ressource-Bundle as xml to location specified in Config
	 * 
	 * @param number Number of bundle. Use 0, if no number in file name wanted
	 *               ("bundle.xml")
	 * @param bundle bundle to print to file
	 */
	public void printBundleAsJsonToFile(String number, Bundle transactionBundle) {
		String json = getBundleAsJsonString(transactionBundle);

		try {
			String fullFilePath;
			if (!number.equals("0")) {
				fullFilePath = configuration.getFhirxmlFilePath() + "bundle" + number + ".json";
			} else {
				fullFilePath = configuration.getFhirxmlFilePath() + "bundle.json";
			}

			Path path = Paths.get(fullFilePath);
			byte[] strToBytes = json.getBytes();

			// Write json as file
			Files.write(path, strToBytes);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error(e.getMessage());
			e.printStackTrace();
		}

	}

	public void bundleToLocalServer(String text) {
		System.out.println("IN BUNDLE TO SERVER");
		try {
			URL url = new URL("https://localhost:8080/fhir/");
			HttpURLConnection http = (HttpURLConnection) url.openConnection();
			http.setRequestMethod("POST");
			http.setDoOutput(true);
			http.setRequestProperty("Content-Type", "application/fhir+json");

			byte[] strToBytes = text.getBytes();

			System.out.println("STEP 4");
			OutputStream stream = http.getOutputStream();
			System.out.println("STEP 5");
			stream.write(strToBytes);

			System.out.println(http.getResponseCode() + " " + http.getResponseMessage());
			http.disconnect();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Get a jsonparser
	 * 
	 * @return json parser
	 */
	public IParser getJsonParser() {
		return this.ctx.newJsonParser();
	}

}
