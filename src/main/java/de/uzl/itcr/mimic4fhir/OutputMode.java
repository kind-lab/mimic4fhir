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

/**
 * Output-Mode: Where shall the data go?
 * -CONSOLE: Print to console
 * -FILE: Print to xml-Files
 * -BOTH: Console and file
 * -SERVER: Push to a Fhir server
 *
 */
public enum OutputMode {
	PRINT_CONSOLE,
	PRINT_FILE_XML,
	PRINT_FILE_JSON,
	PRINT_BOTH,
	PUSH_SERVER
}
