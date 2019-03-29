/*******************************************************************************
 * Idra - Open Data Federation Platform
 *  Copyright (C) 2018 Engineering Ingegneria Informatica S.p.A.
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *  
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package it.eng.idra.beans.dcat;

import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.apache.solr.common.SolrDocument;
import org.json.JSONArray;
import org.json.JSONObject;

@Entity
@Table(name = "subject")
@DiscriminatorValue("2")
public class SKOSConceptSubject extends SKOSConcept {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public SKOSConceptSubject() {
		super();
	}

	public SKOSConceptSubject(SKOSConcept concept) {
		super(concept.getPropertyUri(), concept.getResourceUri(), concept.getPrefLabel(), concept.getNodeID());
	}

	public SKOSConceptSubject(String propertyUri, String resourceUri, List<SKOSPrefLabel> prefLabel, String nodeID) {
		super(propertyUri, resourceUri, prefLabel, nodeID);
	}

	public static SKOSConceptSubject jsonToSKOSConcept(JSONObject obj, String propertyUri, String nodeID) {

		return new SKOSConceptSubject(propertyUri, obj.optString("resourceUri"),
				SKOSPrefLabel.jsonArrayToPrefLabelList(obj.getJSONArray("prefLabel"), nodeID), nodeID);
	}

	public static SKOSConceptSubject docToSKOSConcept(SolrDocument doc, String propertyUri, String nodeID) {

		SKOSConceptSubject t = new SKOSConceptSubject(propertyUri, (String) doc.getFieldValue("resourceUri"), SKOSPrefLabel
				.jsonArrayToPrefLabelList(new JSONArray(doc.getFieldValue("prefLabel").toString()), nodeID), nodeID);
		t.setId(doc.getFieldValue("id").toString());
		
		return t;
	}

}
